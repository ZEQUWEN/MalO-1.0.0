import nodemailer from 'nodemailer';
import { createPool } from './database.js';
import { config } from '../config.js';

/** Secrets are runtime-only. Without DB or SMTP the relevant APIs fail closed. */
export function createV2Services(env = process.env) {
  const pool = createPool(env.DATABASE_URL);
  let sendCode = null;
  if (env.SMTP_URL && env.MALO_MAIL_FROM) {
    let parsed;
    try { parsed = new URL(env.SMTP_URL); } catch { throw new Error('Invalid SMTP_URL'); }
    if (!['smtp:', 'smtps:'].includes(parsed.protocol)) throw new Error('SMTP_URL must use smtp or smtps');
    const transport = nodemailer.createTransport({
      host: parsed.hostname,
      port: Number(parsed.port || (parsed.protocol === 'smtps:' ? 465 : 587)),
      secure: parsed.protocol === 'smtps:',
      requireTLS: true,
      auth: parsed.username ? { user: decodeURIComponent(parsed.username), pass: decodeURIComponent(parsed.password) } : undefined,
      connectionTimeout: 10_000,
      greetingTimeout: 10_000,
      socketTimeout: 15_000,
    });
    sendCode = async (email, code) => transport.sendMail({
      from: env.MALO_MAIL_FROM,
      to: email,
      subject: 'MalO — код входа',
      text: `Ваш код входа: ${code}\nОн действует 10 минут. Никому не сообщайте его.`,
    });
  }
  return {
    pool,
    pepper: env.MALO_AUTH_PEPPER || '',
    sendCode,
    kofiVerificationToken: env.KOFI_VERIFICATION_TOKEN || '',
    kofiUrl: env.KOFI_PAGE_URL || '',
    priceUsd: config.subscription.priceUsd,
    periodDays: config.subscription.periodDays,
  };
}
