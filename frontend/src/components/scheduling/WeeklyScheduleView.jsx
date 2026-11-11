import { WEEKDAYS, hhmm } from '../../utils/dates.js';

/** Read-only weekly hours table (public doctor profile). */
export default function WeeklyScheduleView({ schedule }) {
  if (!schedule) return null;
  return (
    <dl className="divide-y divide-slate-100 text-sm">
      {schedule.days.map((d) => (
        <div key={d.dayOfWeek} className="flex justify-between gap-3 py-1.5">
          <dt className="text-slate-600">{WEEKDAYS[d.dayOfWeek - 1]}</dt>
          <dd className={d.working ? 'font-medium text-slate-900' : 'text-slate-500'}>
            {d.working ? `${hhmm(d.startTime)}–${hhmm(d.endTime)}${d.breakStart ? ` (break ${hhmm(d.breakStart)}–${hhmm(d.breakEnd)})` : ''}` : 'Not working'}
          </dd>
        </div>
      ))}
    </dl>
  );
}
