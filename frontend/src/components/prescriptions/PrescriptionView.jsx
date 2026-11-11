import { Download } from 'lucide-react';
import { prescriptionPdfUrl } from '../../services/prescriptionService.js';
import { formatDateLong } from '../../utils/dates.js';
import { formatDateTime } from '../../utils/format.js';
import Card from '../ui/Card.jsx';
import PrescriptionStatusBadge from './PrescriptionStatusBadge.jsx';

/** Read-only prescription (patient detail page and finalized view for doctors). */
export default function PrescriptionView({ rx, actions }) {
  return (
    <Card
      title={`Prescription ${rx.prescriptionNo}`}
      subtitle={`${rx.doctorName} · ${rx.specialization} · consultation on ${formatDateLong(rx.consultationDate)}`}
      actions={<PrescriptionStatusBadge status={rx.status} versionNumber={rx.versionNumber} />}
    >
      {rx.status === 'SUPERSEDED' && (
        <p role="note" className="mb-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
          This version was replaced by a newer one. Follow the current version.
        </p>
      )}
      <div className="-mx-5 overflow-x-auto" tabIndex={0} role="region" aria-label="Scrollable table">
        <table className="min-w-full text-sm">
          <thead className="border-y border-slate-200 bg-slate-50 text-left text-xs uppercase text-slate-500">
            <tr>
              <th scope="col" className="px-5 py-2">Medicine</th>
              <th scope="col" className="px-3 py-2">Dosage</th>
              <th scope="col" className="px-3 py-2">Frequency</th>
              <th scope="col" className="px-3 py-2">Duration</th>
              <th scope="col" className="px-3 py-2 pr-5">Qty</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rx.items.map((i, idx) => (
              <tr key={idx}>
                <td className="px-5 py-2">
                  <p className="font-medium text-slate-900">{i.medicineName}{i.strength ? ` ${i.strength}` : ''}</p>
                  {i.instructions && <p className="text-xs text-slate-500">{i.instructions}</p>}
                </td>
                <td className="px-3 py-2">{i.dosage}</td>
                <td className="px-3 py-2">{i.frequency}</td>
                <td className="px-3 py-2">{i.duration}</td>
                <td className="px-3 py-2 pr-5">{i.quantity ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rx.generalInstructions && (
        <div className="mt-4">
          <h3 className="text-sm font-semibold text-slate-900">General instructions</h3>
          <p className="whitespace-pre-line text-sm text-slate-600">{rx.generalInstructions}</p>
        </div>
      )}
      {rx.versionNumber > 1 && rx.amendmentReason && (
        <p className="mt-4 text-xs text-slate-500">Revision reason: {rx.amendmentReason}</p>
      )}
      <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4">
        {rx.status !== 'DRAFT' && (
          <a href={prescriptionPdfUrl(rx.id)} download
            className="inline-flex items-center gap-2 rounded-lg bg-brand-700 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800">
            <Download className="h-4 w-4" aria-hidden="true" /> Download PDF
          </a>
        )}
        {actions}
        {rx.finalizedAt && <span className="text-xs text-slate-500">Finalized {formatDateTime(rx.finalizedAt)}</span>}
      </div>
    </Card>
  );
}
