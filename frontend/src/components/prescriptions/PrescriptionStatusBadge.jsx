import StatusBadge from '../ui/StatusBadge.jsx';

const TONE = { DRAFT: 'amber', FINALIZED: 'green', SUPERSEDED: 'slate' };
const LABEL = { DRAFT: 'Draft', FINALIZED: 'Current', SUPERSEDED: 'Superseded' };

export default function PrescriptionStatusBadge({ status, versionNumber }) {
  return (
    <StatusBadge tone={TONE[status]}>
      {LABEL[status]}{versionNumber > 1 ? ` · v${versionNumber}` : ''}
    </StatusBadge>
  );
}
