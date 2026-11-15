'use strict';

const { WebSocket } = require('ws');

// A tiny WebSocket client with a message queue, so tests can `await next()` for the reply they expect.
class Client {
  constructor(url) {
    this.ws = new WebSocket(url);
    this.queue = [];
    this.waiters = [];
    this.closed = null;
    this.ws.on('message', (raw) => {
      const msg = JSON.parse(raw.toString());
      const w = this.waiters.shift();
      if (w) w.resolve(msg);
      else this.queue.push(msg);
    });
    this.ws.on('close', (code) => {
      this.closed = code;
      for (const w of this.waiters) w.resolve({ t: '__closed__', code });
      this.waiters = [];
    });
  }

  open() {
    return new Promise((resolve, reject) => {
      this.ws.once('open', resolve);
      this.ws.once('error', reject);
    });
  }

  send(obj) { this.ws.send(JSON.stringify(obj)); }

  // Next message, or reject after a timeout so a hung test fails fast.
  next(timeoutMs = 2000) {
    if (this.queue.length) return Promise.resolve(this.queue.shift());
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const i = this.waiters.findIndex((w) => w.timer === timer);
        if (i >= 0) this.waiters.splice(i, 1);
        reject(new Error('timed out waiting for a message'));
      }, timeoutMs);
      this.waiters.push({ resolve: (m) => { clearTimeout(timer); resolve(m); }, timer });
    });
  }

  // Wait until a message with t===type arrives (skipping others), for assertions that don't care about order.
  async until(type, timeoutMs = 2000) {
    const end = Date.now() + timeoutMs;
    for (;;) {
      const left = end - Date.now();
      if (left <= 0) throw new Error(`timed out waiting for '${type}'`);
      const m = await this.next(left);
      if (m.t === type) return m;
    }
  }

  close() { try { this.ws.close(); } catch (e) {} }
}

module.exports = { Client };
