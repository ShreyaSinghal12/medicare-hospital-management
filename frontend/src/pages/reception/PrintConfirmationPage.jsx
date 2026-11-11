import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Printer } from 'lucide-react';
import Button from '../../components/ui/Button.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getAppointment } from '../../services/appointmentService.js';
import { getHospitalInfo } from '../../services/systemService.js';
import { formatDateLong, hhmm } from '../../utils/dates.js';
import { formatCurrency } from '../../utils/format.js';
import PageHeading from '../../components/ui/PageHeading.jsx';

/** Printable appointment confirmation slip (no clinical information). */
export default function PrintConfirmationPage() {
  const { id } = useParams();
  const { data: a, loading, error, reload } = useApi(() => getAppointment(id), [id]);
  const hospital = useApi(getHospitalInfo);
  if (loading && !a) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Link to="/reception/appointments" className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Appointments
        </Link>
        <Button onClick={() => window.print()}><Printer className="h-4 w-4" aria-hidden="true" /> Print</Button>
      </div>
      <article className="mx-auto max-w-lg rounded-xl border border-slate-300 bg-white p-8 print:border-0 print:p-0">
        <PageHeading title={'Appointment confirmation'} className="text-xl font-bold text-brand-700">{hospital.data?.hospitalName || 'MediCare'}</PageHeading>
        <p className="text-xs text-slate-500">{hospital.data?.address} · {hospital.data?.phone}</p>
        <h2 className="mt-6 text-lg font-semibold text-slate-900">Appointment confirmation</h2>
        <dl className="mt-4 grid grid-cols-[9rem_1fr] gap-y-2 text-sm">
          {[
            ['Reference', a.referenceNo], ['Patient', `${a.patientName} (${a.patientCode})`], ['Doctor', a.doctorName],
            ['Department', a.departmentName], ['Date', formatDateLong(a.date)], ['Time', `${hhmm(a.startTime)} – ${hhmm(a.endTime)}`],
            ['Consultation fee', formatCurrency(a.consultationFee)], a.queueToken && ['Queue token', String(a.queueToken)],
            ['Status', a.status.replace('_', ' ').toLowerCase()],
          ].filter(Boolean).map(([k, v]) => (
            <div key={k} className="contents"><dt className="text-slate-500">{k}</dt><dd className="font-medium text-slate-900">{v}</dd></div>
          ))}
        </dl>
        <p className="mt-6 text-xs text-slate-500">Please arrive 10 minutes early and bring this slip or your reference number. {hospital.data?.cancellationPolicy}</p>
      </article>
    </>
  );
}
