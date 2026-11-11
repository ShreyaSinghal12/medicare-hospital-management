import StatusBadge from '../ui/StatusBadge.jsx';

const MAP = {
  UNPAID: ['amber', 'Unpaid'],
  PARTIALLY_PAID: ['blue', 'Partly paid'],
  PAID: ['green', 'Paid'],
  CANCELLED: ['slate', 'Cancelled'],
  REFUNDED: ['slate', 'Refunded'],
};

export default function InvoiceStatusBadge({ status }) {
  const [tone, label] = MAP[status] || ['slate', status];
  return <StatusBadge tone={tone}>{label}</StatusBadge>;
}
