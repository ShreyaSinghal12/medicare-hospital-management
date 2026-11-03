import StatusBadge from '../ui/StatusBadge.jsx';
import { formatDateShort, hhmm } from '../../utils/dates.js';

const TONE = { PENDING: 'amber', APPROVED: 'green', REJECTED: 'red', CANCELLED: 'slate' };

export function leavePeriod(l) {
  const dates = l.startDate === l.endDate ? formatDateShort(l.startDate) : `${formatDateShort(l.startDate)} – ${formatDateShort(l.endDate)}`;
  return l.leaveType === 'UNAVAILABLE' ? `${dates}, ${hhmm(l.startTime)}–${hhmm(l.endTime)}` : dates;
}

/** List of leave/unavailability entries with optional per-row actions. */
export default function LeaveList({ items, showDoctor = false, actions }) {
  return (
    <ul className="divide-y divide-slate-100">
      {items.map((l) => (
        <li key={l.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
          <div>
            {showDoctor && <p className="font-medium text-slate-900">{l.doctorName} <span className="text-sm font-normal text-slate-500">· {l.departmentName}</span></p>}
            <p className={showDoctor ? 'text-sm text-slate-700' : 'font-medium text-slate-900'}>
              {l.leaveType === 'LEAVE' ? 'Leave' : 'Unavailable'} · {leavePeriod(l)}
            </p>
            {l.reason && <p className="text-sm text-slate-500">{l.reason}</p>}
          </div>
          <div className="flex items-center gap-2">
            <StatusBadge tone={TONE[l.status]}>{l.status.toLowerCase()}</StatusBadge>
            {actions?.(l)}
          </div>
        </li>
      ))}
    </ul>
  );
}
