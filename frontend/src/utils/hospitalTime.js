/**
 * The hospital's time zone (from GET /api/public/hospital-info), used for "today", for formatting timestamps and
 * for deciding whether an appointment has started, so every user sees hospital time whatever their device's
 * zone. Until it is loaded, the device's zone is used.
 */
let zone = Intl.DateTimeFormat().resolvedOptions().timeZone;

export function setHospitalTimeZone(timeZone) {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone }).format(0);
    zone = timeZone;
    return true;
  } catch {
    return false;
  }
}

export function hospitalTimeZone() {
  return zone;
}

/** Current hospital date and time as { date: 'YYYY-MM-DD', time: 'HH:mm' }. */
export function hospitalNow(now = new Date()) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(now).map((p) => [p.type, p.value]));
  return { date: `${parts.year}-${parts.month}-${parts.day}`, time: `${parts.hour}:${parts.minute}` };
}

/** Whether a hospital-local date ("YYYY-MM-DD") and time ("HH:mm[:ss]") is already in the past. */
export function hasStarted(dateIso, time, now = new Date()) {
  const n = hospitalNow(now);
  return `${dateIso}T${time.slice(0, 5)}` < `${n.date}T${n.time}`;
}
