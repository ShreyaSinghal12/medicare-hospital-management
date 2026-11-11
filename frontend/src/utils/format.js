import { hospitalTimeZone } from './hospitalTime.js';

/** Formats an ISO timestamp in the user's locale, in the hospital's time zone unless another zone is given. */
export function formatDateTime(iso, timeZone = hospitalTimeZone()) {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
    ...(timeZone ? { timeZone } : {}),
  }).format(date);
}

/** Formats a decimal amount as currency (defaults to INR). */
export function formatCurrency(amount, currency = 'INR') {
  if (amount === null || amount === undefined || amount === '') return '—';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(Number(amount));
}
