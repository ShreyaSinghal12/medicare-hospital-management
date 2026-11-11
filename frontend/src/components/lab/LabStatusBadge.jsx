import StatusBadge from '../ui/StatusBadge.jsx';

export const LAB_STATUS = {
  ORDERED: { label: 'Ordered', tone: 'blue' },
  SAMPLE_COLLECTED: { label: 'Sample collected', tone: 'amber' },
  PROCESSING: { label: 'Processing', tone: 'amber' },
  COMPLETED: { label: 'Report ready', tone: 'green' },
  CANCELLED: { label: 'Cancelled', tone: 'slate' },
};

export default function LabStatusBadge({ status, priority }) {
  const s = LAB_STATUS[status] || { label: status, tone: 'slate' };
  return (
    <span className="inline-flex flex-wrap items-center gap-1">
      <StatusBadge tone={s.tone}>{s.label}</StatusBadge>
      {priority === 'URGENT' && status !== 'COMPLETED' && status !== 'CANCELLED' && <StatusBadge tone="red">Urgent</StatusBadge>}
    </span>
  );
}
