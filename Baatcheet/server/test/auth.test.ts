import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { prisma } from '../src/db.js';
import { api, connect, cookieFrom, lastMail, register, resetDb, startServer, type TestServer } from './helpers.js';

let srv: TestServer;
beforeAll(async () => {
  srv = await startServer();
});
afterAll(async () => {
  await srv.close();
});
beforeEach(resetDb);

describe('registration and login', () => {
  it('registers, stores an argon2 hash and logs in', async () => {
    const u = await register(srv, { verify: false });
    const row = await prisma.user.findUniqueOrThrow({ where: { id: u.id } });
    expect(row.passwordHash).toMatch(/^\$argon2id\$/);
    expect(row.passwordHash).not.toContain(u.password);
    const me = await u.agent.get('/api/auth/me').expect(200);
    expect(me.body).toMatchObject({ username: u.username, emailVerified: false });
    expect(me.body.passwordHash).toBeUndefined();

    const login = await api(srv.http).post('/api/auth/login').send({ email: u.email, password: u.password }).expect(200);
    expect(cookieFrom(login)).toMatch(/^bc_session=/);
    expect(String(login.headers['set-cookie'])).toMatch(/HttpOnly/);
    expect(String(login.headers['set-cookie'])).toMatch(/SameSite=Lax/);
  });

  it('rejects duplicates, weak passwords and bad usernames', async () => {
    const u = await register(srv);
    await api(srv.http).post('/api/auth/register').send({ email: u.email, password: 'Password123', username: 'other_name', displayName: 'X' }).expect(409);
    await api(srv.http).post('/api/auth/register').send({ email: 'new@test.local', password: 'Password123', username: u.username, displayName: 'X' }).expect(409);
    await api(srv.http).post('/api/auth/register').send({ email: 'a@test.local', password: 'short', username: 'abcdef', displayName: 'X' }).expect(400);
    await api(srv.http).post('/api/auth/register').send({ email: 'b@test.local', password: 'Password123', username: 'no spaces!', displayName: 'X' }).expect(400);
  });

  it('locks the account after 5 wrong passwords', async () => {
    const u = await register(srv);
    for (let i = 0; i < 5; i++) await api(srv.http).post('/api/auth/login').send({ email: u.email, password: 'Wrong12345' }).expect(401);
    const locked = await api(srv.http).post('/api/auth/login').send({ email: u.email, password: u.password });
    expect(locked.status).toBe(429);
  });

  it('gives the same answer for unknown e-mails', async () => {
    const r = await api(srv.http).post('/api/auth/login').send({ email: 'nobody@test.local', password: 'Password123' });
    expect(r.status).toBe(401);
    expect(r.body.error).toBe('Wrong e-mail or password');
    await api(srv.http).post('/api/auth/forgot-password').send({ email: 'nobody@test.local' }).expect(200);
  });

  it('verifies the e-mail with the link token, only once', async () => {
    const u = await register(srv, { verify: false });
    const token = decodeURIComponent(/token=(\S+)/.exec(lastMail(u.email).text)![1]);
    await api(srv.http).post('/api/auth/verify-email').send({ token }).expect(200);
    await api(srv.http).post('/api/auth/verify-email').send({ token }).expect(400);
    expect((await u.agent.get('/api/auth/me')).body.emailVerified).toBe(true);
  });

  it('resets the password and logs out every session', async () => {
    const u = await register(srv);
    await api(srv.http).post('/api/auth/forgot-password').send({ email: u.email }).expect(200);
    const token = decodeURIComponent(/token=(\S+)/.exec(lastMail(u.email).text)![1]);
    await api(srv.http).post('/api/auth/reset-password').send({ token, password: 'NewPassword9' }).expect(200);
    await u.agent.get('/api/auth/me').expect(401);
    await api(srv.http).post('/api/auth/login').send({ email: u.email, password: u.password }).expect(401);
    await api(srv.http).post('/api/auth/login').send({ email: u.email, password: 'NewPassword9' }).expect(200);
  });

  it('lists and revokes sessions (devices)', async () => {
    const u = await register(srv);
    const second = cookieFrom(await api(srv.http).post('/api/auth/login').send({ email: u.email, password: u.password }));
    const list = await u.agent.get('/api/auth/sessions').expect(200);
    expect(list.body).toHaveLength(2);
    const other = list.body.find((s: { current: boolean }) => !s.current);
    await u.agent.delete(`/api/auth/sessions/${other.id}`).expect(200);
    await api(srv.http, second).get('/api/auth/me').expect(401);
    await u.agent.get('/api/auth/me').expect(200);
  });

  it('logs out', async () => {
    const u = await register(srv);
    await u.agent.post('/api/auth/logout').expect(200);
    await u.agent.get('/api/auth/me').expect(401);
  });

  it('deletes the account', async () => {
    const u = await register(srv);
    await u.agent.post('/api/auth/delete-account').send({ password: 'nope' }).expect(400);
    await u.agent.post('/api/auth/delete-account').send({ password: u.password }).expect(200);
    expect(await prisma.user.findUnique({ where: { id: u.id } })).toBeNull();
    await api(srv.http).post('/api/auth/login').send({ email: u.email, password: u.password }).expect(401);
  });
});

describe('protection', () => {
  it('rejects requests without a session', async () => {
    for (const path of ['/api/conversations', '/api/contacts', '/api/calls', '/api/notifications', '/api/users/search?q=a']) {
      await api(srv.http).get(path).expect(401);
    }
  });

  it('rejects state changes from foreign origins (CSRF)', async () => {
    const u = await register(srv);
    const r = await u.agent.post('/api/auth/logout').set('Origin', 'https://evil.example');
    expect(r.status).toBe(403);
  });

  it('requires a verified e-mail to start chats', async () => {
    const a = await register(srv, { verify: false });
    const b = await register(srv);
    const r = await a.agent.post('/api/conversations/direct').send({ userId: b.id });
    expect(r.status).toBe(403);
  });

  it('refuses sockets without a valid session', async () => {
    const u = await register(srv);
    await expect(connect(srv, { ...u, cookie: 'bc_session=forged' })).rejects.toThrow(/unauthorized/);
  });

  it('only admins reach the admin API', async () => {
    const u = await register(srv);
    await u.agent.get('/api/admin/stats').expect(403);
    const admin = await register(srv, { email: 'admin@test.local' });
    await admin.agent.get('/api/admin/stats').expect(200);
  });

  it('phone OTP answers 501 when no provider is configured', async () => {
    const u = await register(srv);
    const r = await u.agent.post('/api/auth/otp/start').send({ phone: '+919876543210' });
    expect(r.status).toBe(501);
  });
});

describe('rate limits', () => {
  it('blocks bursts of logins from one IP', async () => {
    const { config } = await import('../src/config.js');
    config.DISABLE_RATE_LIMIT = false;
    try {
      const codes: number[] = [];
      for (let i = 0; i < 12; i++) codes.push((await api(srv.http).post('/api/auth/login').send({ email: `x${i}@test.local`, password: 'Password123' })).status);
      expect(codes).toContain(429);
    } finally {
      config.DISABLE_RATE_LIMIT = true;
    }
  });
});
