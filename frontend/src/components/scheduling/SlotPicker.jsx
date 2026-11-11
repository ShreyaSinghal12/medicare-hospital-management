import { useEffect, useState } from 'react';
import { CalendarClock, ChevronLeft, ChevronRight } from 'lucide-react';
import useApi from '../../hooks/useApi.js';
import { getNextAvailable, getSlots } from '../../services/appointmentService.js';
import { addDaysIso, formatDateLong, hhmm, parseIsoDate, todayIsoLocal } from '../../utils/dates.js';
import Button from '../ui/Button.jsx';
import ErrorState from '../ui/ErrorState.jsx';
import Spinner from '../ui/Spinner.jsx';

const VISIBLE_DAYS = 7;

/**
 * Date strip + time-slot grid. Slots come from the server (generated from the doctor's
 * schedule, leave and existing bookings), so what is shown is what can be booked.
 *
 * @param value    { date, startTime } or null
 * @param onChange called with { date, startTime, endTime }
 * @param refreshKey change it to force a reload (e.g. after a 409 conflict)
 */
export default function SlotPicker({ doctorId, value, onChange, initialDate, refreshKey = 0 }) {
  const today = todayIsoLocal();
  const [date, setDate] = useState(initialDate || value?.date || today);
  const [stripStart, setStripStart] = useState(initialDate && initialDate > today ? initialDate : today);
  const [finding, setFinding] = useState(false);
  const [nextMessage, setNextMessage] = useState(null);
  const { data, loading, error, reload } = useApi(() => getSlots(doctorId, date), [doctorId, date, refreshKey]);

  // Bring the selected date into view when it changes (e.g. "Next available"), but let the user
  // page the strip freely with the arrows afterwards.
  useEffect(() => {
    setStripStart((start) => (date < start || date >= addDaysIso(start, VISIBLE_DAYS) ? (date < today ? today : date) : start));
  }, [date, today]);

  async function jumpToNext() {
    setFinding(true);
    setNextMessage(null);
    try {
      const next = await getNextAvailable(doctorId, today);
      if (!next) {
        setNextMessage('No free slots within the booking window.');
      } else {
        setDate(next.date);
        onChange({ date: next.date, startTime: hhmm(next.startTime), endTime: hhmm(next.endTime) });
      }
    } catch (err) {
      setNextMessage(err.message);
    } finally {
      setFinding(false);
    }
  }

  const days = Array.from({ length: VISIBLE_DAYS }, (_, i) => addDaysIso(stripStart, i));
  const slots = data?.slots || [];

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-medium text-slate-700">{formatDateLong(date)}</p>
        <Button variant="secondary" size="sm" onClick={jumpToNext} loading={finding}>
          <CalendarClock className="h-4 w-4" aria-hidden="true" /> Next available
        </Button>
      </div>
      {nextMessage && <p className="mt-2 text-sm text-amber-700">{nextMessage}</p>}

      <div className="mt-3 flex items-center gap-1">
        <button type="button" aria-label="Previous days" disabled={stripStart <= today}
          onClick={() => setStripStart(addDaysIso(stripStart, -VISIBLE_DAYS) < today ? today : addDaysIso(stripStart, -VISIBLE_DAYS))}
          className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 disabled:opacity-30">
          <ChevronLeft className="h-5 w-5" />
        </button>
        <div className="grid flex-1 grid-cols-7 gap-1" role="group" aria-label="Choose a date">
          {days.map((d) => {
            const dt = parseIsoDate(d);
            const selected = d === date;
            return (
              <button key={d} type="button" aria-pressed={selected} aria-label={formatDateLong(d)}
                onClick={() => setDate(d)}
                className={`flex flex-col items-center rounded-lg border px-1 py-2 text-xs transition-colors ${
                  selected ? 'border-brand-600 bg-brand-700 text-white' : 'border-slate-200 bg-white text-slate-700 hover:border-brand-300'
                }`}>
                <span className="font-medium">{dt.toLocaleDateString(undefined, { weekday: 'short' })}</span>
                <span className="text-base font-semibold">{dt.getDate()}</span>
              </button>
            );
          })}
        </div>
        <button type="button" aria-label="Next days" onClick={() => setStripStart(addDaysIso(stripStart, VISIBLE_DAYS))}
          className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100">
          <ChevronRight className="h-5 w-5" />
        </button>
      </div>

      <div className="mt-4 min-h-24" aria-live="polite">
        {loading && <Spinner label="Loading slots…" />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {!loading && !error && slots.length === 0 && (
          <p className="rounded-lg bg-slate-50 p-4 text-sm text-slate-600">{data?.message || 'No slots on this day.'}</p>
        )}
        {!loading && !error && slots.length > 0 && (
          <>
            {data.message && <p className="mb-2 text-sm text-slate-600">{data.message}</p>}
            <div className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6" role="group" aria-label="Choose a time">
              {slots.map((s) => {
                const start = hhmm(s.startTime);
                const selected = value?.date === date && value?.startTime === start;
                const available = s.status === 'AVAILABLE';
                return (
                  <button key={start} type="button" disabled={!available} aria-pressed={selected}
                    aria-label={`${start}${available ? '' : ` (${s.status.toLowerCase()})`}`}
                    onClick={() => onChange({ date, startTime: start, endTime: hhmm(s.endTime) })}
                    className={`rounded-lg border px-2 py-2 text-sm font-medium transition-colors ${
                      selected ? 'border-brand-600 bg-brand-700 text-white'
                        : available ? 'border-brand-200 bg-white text-brand-800 hover:bg-brand-50'
                          : 'cursor-not-allowed border-slate-100 bg-slate-50 text-slate-300 line-through'
                    }`}>
                    {start}
                  </button>
                );
              })}
            </div>
            <p className="mt-2 text-xs text-slate-500">
              {data.availableCount} free · {data.slotDurationMinutes}-minute consultations · greyed-out times are booked, past or unavailable
            </p>
          </>
        )}
      </div>
    </div>
  );
}
