/** Date helpers working with ISO "YYYY-MM-DD" strings (no time-zone shifting). */
import { hospitalNow } from './hospitalTime.js';

export const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

export function toIsoDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function parseIsoDate(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** Today's date in the hospital's time zone. */
export function todayIsoLocal() {
  return hospitalNow().date;
}

export function addDaysIso(iso, days) {
  const d = parseIsoDate(iso);
  d.setDate(d.getDate() + days);
  return toIsoDate(d);
}

/** Monday of the week containing `iso`. */
export function startOfWeekIso(iso) {
  const d = parseIsoDate(iso);
  const dow = (d.getDay() + 6) % 7; // 0 = Monday
  d.setDate(d.getDate() - dow);
  return toIsoDate(d);
}

export function formatDateLong(iso) {
  if (!iso) return '—';
  return parseIsoDate(iso).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

export function formatDateShort(iso) {
  if (!iso) return '—';
  return parseIsoDate(iso).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
}

/** "HH:mm" or "HH:mm:ss" → "HH:mm". */
export function hhmm(time) {
  return time ? time.slice(0, 5) : '';
}
