import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { WEEKDAYS, hhmm } from '../../utils/dates.js';
import Button from '../ui/Button.jsx';

function toRows(days) {
  return days.map((d) => ({
    dayOfWeek: d.dayOfWeek, working: d.working,
    startTime: hhmm(d.startTime) || '09:00', endTime: hhmm(d.endTime) || '17:00',
    breakStart: hhmm(d.breakStart), breakEnd: hhmm(d.breakEnd),
  }));
}

function validate(rows) {
  for (const r of rows) {
    if (!r.working) continue;
    const day = WEEKDAYS[r.dayOfWeek - 1];
    if (!r.startTime || !r.endTime || r.endTime <= r.startTime) return `${day}: end time must be after start time`;
    if (!!r.breakStart !== !!r.breakEnd) return `${day}: give both break start and end, or neither`;
    if (r.breakStart && (r.breakEnd <= r.breakStart || r.breakStart < r.startTime || r.breakEnd > r.endTime)) {
      return `${day}: the break must lie within working hours`;
    }
  }
  return null;
}

/**
 * Editable weekly hours. `onSave(days)` must return the server response
 * ({ flaggedAppointments, days }) so the user learns which bookings were affected.
 */
export default function WeeklyScheduleEditor({ schedule, onSave }) {
  const [rows, setRows] = useState(() => toRows(schedule.days));
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => setRows(toRows(schedule.days)), [schedule]);

  function update(i, patch) {
    setRows((rs) => rs.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
    setError(null);
  }

  async function save() {
    const problem = validate(rows);
    if (problem) {
      setError(problem);
      return;
    }
    setSaving(true);
    try {
      const result = await onSave(rows.map((r) => ({
        dayOfWeek: r.dayOfWeek, working: r.working,
        startTime: r.working ? r.startTime : null, endTime: r.working ? r.endTime : null,
        breakStart: r.working && r.breakStart ? r.breakStart : null, breakEnd: r.working && r.breakEnd ? r.breakEnd : null,
      })));
      if (result?.flaggedAppointments > 0) {
        toast.warning(`Schedule saved. ${result.flaggedAppointments} upcoming appointment(s) no longer fit and were flagged for rescheduling; the patients have been notified.`);
      } else {
        toast.success('Schedule saved');
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  const input = 'w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm disabled:bg-slate-50 disabled:text-slate-400';
  return (
    <div>
      <p className="mb-3 text-sm text-slate-600">
        Consultation length: <strong>{schedule.slotDurationMinutes} minutes</strong>. Changing hours never cancels bookings; appointments that no longer fit are flagged for rescheduling.
      </p>
      <div className="-mx-5 overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="border-y border-slate-200 bg-slate-50 text-left text-xs uppercase text-slate-500">
            <tr>
              <th scope="col" className="px-5 py-2">Day</th>
              <th scope="col" className="px-2 py-2">Working</th>
              <th scope="col" className="px-2 py-2">Start</th>
              <th scope="col" className="px-2 py-2">End</th>
              <th scope="col" className="px-2 py-2">Break from</th>
              <th scope="col" className="px-2 py-2 pr-5">Break to</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((r, i) => {
              const day = WEEKDAYS[r.dayOfWeek - 1];
              return (
                <tr key={r.dayOfWeek}>
                  <th scope="row" className="px-5 py-2 text-left font-medium text-slate-800">{day}</th>
                  <td className="px-2 py-2">
                    <input type="checkbox" checked={r.working} aria-label={`${day} working`}
                      onChange={(e) => update(i, { working: e.target.checked })} className="h-4 w-4 rounded border-slate-300 text-brand-700" />
                  </td>
                  {['startTime', 'endTime', 'breakStart', 'breakEnd'].map((f) => (
                    <td key={f} className="px-2 py-2 last:pr-5">
                      <input type="time" step="300" value={r[f]} disabled={!r.working} className={input}
                        aria-label={`${day} ${f.replace(/([A-Z])/g, ' $1').toLowerCase()}`}
                        onChange={(e) => update(i, { [f]: e.target.value })} />
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {error && <p role="alert" className="mt-3 text-sm font-medium text-red-600">{error}</p>}
      <Button className="mt-4" onClick={save} loading={saving}>Save weekly schedule</Button>
    </div>
  );
}
