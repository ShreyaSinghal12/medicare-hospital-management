import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Printer } from 'lucide-react';
import Button from '../../components/ui/Button.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getDispensing } from '../../services/inventoryService.js';
import { formatDateShort } from '../../utils/dates.js';
import { formatCurrency, formatDateTime } from '../../utils/format.js';
import PageHeading from '../../components/ui/PageHeading.jsx';

/** A dispensing record, printable as a slip for the patient. */
export default function DispensingRecordPage() {
  const { id } = useParams();
  const { data: d, loading, error, reload } = useApi(() => getDispensing(id), [id]);
  if (loading) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Link to="/admin/dispensing" className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Dispensing records
        </Link>
        <Button onClick={() => window.print()}><Printer className="h-4 w-4" aria-hidden="true" /> Print</Button>
      </div>
      <article className="mx-auto max-w-2xl rounded-xl border border-slate-300 bg-white p-6 print:border-0 print:p-0">
        <PageHeading title={`Dispensing record ${d.dispensingNo}`} className="text-lg font-semibold text-slate-900">Dispensing record {d.dispensingNo}</PageHeading>
        <dl className="mt-3 grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
          <div><dt className="inline text-slate-500">Patient: </dt><dd className="inline">{d.patientName} ({d.patientCode})</dd></div>
          <div><dt className="inline text-slate-500">Prescription: </dt><dd className="inline">{d.prescriptionNo}</dd></div>
          <div><dt className="inline text-slate-500">Prescribed by: </dt><dd className="inline">{d.doctorName}</dd></div>
          <div><dt className="inline text-slate-500">Dispensed: </dt><dd className="inline">{formatDateTime(d.dispensedAt)} by {d.dispensedBy}</dd></div>
        </dl>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[32rem] text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="py-2 pr-3 font-semibold">Medicine</th>
                <th scope="col" className="py-2 pr-3 font-semibold">Batch / expiry</th>
                <th scope="col" className="py-2 pr-3 text-right font-semibold">Qty</th>
                <th scope="col" className="py-2 text-right font-semibold">Unit price</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {d.items.map((i, idx) => (
                <tr key={idx}>
                  <td className="py-2 pr-3">
                    <p className="font-medium text-slate-900">{i.medicineName} {i.strength}</p>
                    <p className="text-xs text-slate-500">Prescribed as: {i.prescribedMedicine}</p>
                  </td>
                  <td className="py-2 pr-3 text-slate-600">{i.batchNumber} · {formatDateShort(i.expiryDate)}</td>
                  <td className="py-2 pr-3 text-right">{i.quantity} {i.unit}(s)</td>
                  <td className="py-2 text-right">{formatCurrency(i.unitPrice)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {d.notes && <p className="mt-3 text-sm text-slate-600">Notes: {d.notes}</p>}
        <p className="mt-4 text-xs text-slate-500">Follow the dosage instructions on your prescription.</p>
      </article>
    </>
  );
}
