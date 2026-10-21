// Phone OTP through Twilio Verify. Without Twilio settings the endpoints answer 501.
import { config } from '../config.js';
import { HttpError } from '../lib/errors.js';

const configured = () => Boolean(config.TWILIO_ACCOUNT_SID && config.TWILIO_AUTH_TOKEN && config.TWILIO_VERIFY_SERVICE_SID);

async function call(path: string, form: Record<string, string>) {
  if (!configured()) throw new HttpError(501, 'Phone login is not set up on this server', 'otp_unconfigured');
  const res = await fetch(`https://verify.twilio.com/v2/Services/${config.TWILIO_VERIFY_SERVICE_SID}/${path}`, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${Buffer.from(`${config.TWILIO_ACCOUNT_SID}:${config.TWILIO_AUTH_TOKEN}`).toString('base64')}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(form),
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new HttpError(502, 'Could not reach the SMS provider');
  return (await res.json()) as { status?: string };
}

export const otp = {
  configured,
  async start(phone: string) {
    await call('Verifications', { To: phone, Channel: 'sms' });
  },
  async check(phone: string, code: string) {
    const r = await call('VerificationCheck', { To: phone, Code: code });
    return r?.status === 'approved';
  },
};
