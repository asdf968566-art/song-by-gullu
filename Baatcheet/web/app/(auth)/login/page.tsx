'use client';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import { api, errorText } from '@/lib/api';
import type { Me } from '@/lib/types';
import { Button, Input } from '@/components/ui';

function LoginForm() {
  const router = useRouter();
  const next = useSearchParams().get('next');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [phoneMode, setPhoneMode] = useState(false);
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);

  const done = (me: Me) => router.replace(!me.onboarded ? '/onboarding' : next && next.startsWith('/') && !next.startsWith('//') ? next : '/chats');

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (!phoneMode) done(await api.post<Me>('/auth/login', { email, password }));
      else if (!codeSent) {
        await api.post('/auth/otp/start', { phone });
        setCodeSent(true);
      } else done(await api.post<Me>('/auth/otp/verify', { phone, code }));
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <h1 className="text-xl font-semibold">Welcome back</h1>
      {!phoneMode ? (
        <>
          <Input label="E-mail" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <Input label="Password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          <div className="text-right text-sm">
            <Link href="/forgot-password" className="text-accent hover:underline">
              Forgot password?
            </Link>
          </div>
        </>
      ) : (
        <>
          <Input label="Phone number" type="tel" autoComplete="tel" placeholder="+919876543210" value={phone} onChange={(e) => setPhone(e.target.value)} disabled={codeSent} hint="Works for numbers confirmed in Settings." />
          {codeSent && <Input label="Code from SMS" inputMode="numeric" autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value)} />}
        </>
      )}
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
      <Button type="submit" className="w-full" loading={busy}>
        {phoneMode && !codeSent ? 'Send code' : 'Log in'}
      </Button>
      <button type="button" className="w-full text-center text-sm text-muted hover:text-text" onClick={() => { setPhoneMode(!phoneMode); setError(null); setCodeSent(false); }}>
        {phoneMode ? 'Use e-mail and password' : 'Log in with a phone code'}
      </button>
      <p className="text-center text-sm text-muted">
        New here?{' '}
        <Link href="/register" className="text-accent hover:underline">
          Create an account
        </Link>
      </p>
    </form>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
