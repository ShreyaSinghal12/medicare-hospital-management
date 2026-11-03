/** Formats an ISO timestamp for display in the user's locale. */
export function formatDateTime(iso, timeZone) {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'medium',
    ...(timeZone ? { timeZone } : {}),
  }).format(date);
}

/** Formats a decimal amount as currency (defaults to INR). */
export function formatCurrency(amount, currency = 'INR') {
  if (amount === null || amount === undefined || amount === '') return '—';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(Number(amount));
}
