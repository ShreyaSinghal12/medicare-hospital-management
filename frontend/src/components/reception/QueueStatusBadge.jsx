import StatusBadge from '../ui/StatusBadge.jsx';

const TONE = { WAITING: 'amber', IN_CONSULTATION: 'blue', COMPLETED: 'green', SKIPPED: 'slate' };
const LABEL = { WAITING: 'Waiting', IN_CONSULTATION: 'In consultation', COMPLETED: 'Completed', SKIPPED: 'Skipped' };

export default function QueueStatusBadge({ status }) {
  return <StatusBadge tone={TONE[status]}>{LABEL[status] || status}</StatusBadge>;
}

export function TokenChip({ token, status }) {
  const tone = status === 'IN_CONSULTATION' ? 'bg-sky-700 text-white' : status === 'WAITING' ? 'bg-amber-100 text-amber-900'
    : 'bg-slate-100 text-slate-600';
  return (
    <span className={`inline-flex h-9 min-w-9 items-center justify-center rounded-lg px-2 font-mono text-base font-bold ${tone}`}
      aria-label={`Token ${token}`}>
      {token}
    </span>
  );
}
