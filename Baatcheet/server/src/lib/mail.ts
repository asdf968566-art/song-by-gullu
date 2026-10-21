import nodemailer, { type Transporter } from 'nodemailer';
import { config } from '../config.js';
import { logger } from '../logger.js';

let transport: Transporter | null = null;

/** Every e-mail sent in tests ends up here, so tests can follow the real links. */
export const sentMail: { to: string; subject: string; text: string }[] = [];

export async function sendMail(to: string, subject: string, text: string, html?: string): Promise<void> {
  if (config.NODE_ENV === 'test') {
    sentMail.push({ to, subject, text });
    return;
  }
  if (!config.SMTP_URL) {
    // Development without SMTP: the message goes to the server log instead of a mailbox.
    logger.warn({ to, subject }, `SMTP_URL not set — e-mail not sent. Content:\n${text}`);
    return;
  }
  transport ??= nodemailer.createTransport(config.SMTP_URL);
  await transport.sendMail({ from: config.MAIL_FROM, to, subject, text, html });
}
