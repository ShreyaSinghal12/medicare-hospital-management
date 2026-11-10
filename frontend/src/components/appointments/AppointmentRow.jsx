import AppointmentStatusBadge from './AppointmentStatusBadge.jsx';
import { formatDateShort, hhmm } from '../../utils/dates.js';

/** Compact appointment line for staff lists (time, patient, status, optional actions). */
export default function AppointmentRow({ a, showDate = false, actions }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 py-3">
      <div className="flex min-w-0 items-start gap-3">
        <span className="w-24 shrink-0 font-mono text-sm font-semibold text-slate-800">
          {showDate && <span className="block font-sans text-xs font-normal text-slate-500">{formatDateShort(a.date)}</span>}
          {hhmm(a.startTime)}–{hhmm(a.endTime)}
        </span>
        <div className="min-w-0">
          <p className="font-medium text-slate-900">{a.patientName} <span className="text-sm font-normal text-slate-500">· {a.patientCode}</span></p>
          {a.reason && <p className="truncate text-sm text-slate-500">{a.reason}</p>}
          <p className="text-xs text-slate-400">
            {a.referenceNo}{a.type === 'FOLLOW_UP' ? ' · Follow-up' : ''}{a.type === 'WALK_IN' ? ' · Walk-in' : ''}
            {a.queueToken ? ` · Token ${a.queueToken}` : ''}
          </p>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <AppointmentStatusBadge status={a.status} needsReschedule={a.needsReschedule} />
        {actions}
      </div>
    </li>
  );
}
