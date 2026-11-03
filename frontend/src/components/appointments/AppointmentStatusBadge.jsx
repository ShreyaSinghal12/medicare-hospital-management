import StatusBadge from '../ui/StatusBadge.jsx';

const TONE = {
  SCHEDULED: 'blue', CHECKED_IN: 'amber', IN_PROGRESS: 'amber', COMPLETED: 'green', CANCELLED: 'slate', NO_SHOW: 'red',
};
const LABEL = {
  SCHEDULED: 'Scheduled', CHECKED_IN: 'Checked in', IN_PROGRESS: 'In consultation', COMPLETED: 'Completed',
  CANCELLED: 'Cancelled', NO_SHOW: 'No-show',
};

export default function AppointmentStatusBadge({ status, needsReschedule }) {
  return (
    <span className="inline-flex flex-wrap gap-1">
      <StatusBadge tone={TONE[status] || 'slate'}>{LABEL[status] || status}</StatusBadge>
      {needsReschedule && <StatusBadge tone="red">Needs rescheduling</StatusBadge>}
    </span>
  );
}
