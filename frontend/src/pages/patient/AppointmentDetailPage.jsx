import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, CalendarClock, CheckCircle2, Printer } from 'lucide-react';
import AppointmentStatusBadge from '../../components/appointments/AppointmentStatusBadge.jsx';
import CancelAppointmentDialog from '../../components/appointments/CancelAppointmentDialog.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getAppointment, getAppointmentHistory } from '../../services/appointmentService.js';
import { formatDateLong, hhmm } from '../../utils/dates.js';
import { formatCurrency, formatDateTime } from '../../utils/format.js';

const ACTION_LABEL = { BOOKED: 'Booked', CANCELLED: 'Cancelled', RESCHEDULED: 'Rescheduled', NO_SHOW: 'Marked no-show' };

/** Appointment confirmation / detail page (printable). */
export default function AppointmentDetailPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const { data: a, loading, error, reload } = useApi(() => getAppointment(id), [id]);
  const history = useApi(() => getAppointmentHistory(id), [id]);
  const [cancelling, setCancelling] = useState(null);

  if (loading && !a) return <Spinner label="Loading appointment…" />;
  if (error) return <ErrorState error={error} onRetry={error.status === 404 ? undefined : reload} title={error.status === 404 ? 'Appointment not found' : undefined} />;

  return (
    <>
      <Link to="/patient/appointments" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline print:hidden">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> My appointments
      </Link>

      {params.get('booked') && a.status === 'SCHEDULED' && (
        <div role="status" className="mb-6 flex items-start gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-emerald-800">
          <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
          <div>
            <p className="font-semibold">Your appointment is confirmed.</p>
            <p className="text-sm">Please arrive 10 minutes early and show reference <strong>{a.referenceNo}</strong> at reception.</p>
          </div>
        </div>
      )}
      {a.needsReschedule && a.status === 'SCHEDULED' && (
        <div role="alert" className="mb-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          The doctor is no longer available at this time. Your booking is kept; please choose a new time.
        </div>
      )}

      <Card
        title={`Appointment ${a.referenceNo}`}
        subtitle={`Booked ${formatDateTime(a.createdAt)}`}
        actions={<AppointmentStatusBadge status={a.status} needsReschedule={a.needsReschedule} />}
      >
        <dl className="grid gap-4 sm:grid-cols-2">
          {[
            ['Doctor', `${a.doctorName} (${a.specialization})`],
            ['Department', a.departmentName],
            ['Date', formatDateLong(a.date)],
            ['Time', `${hhmm(a.startTime)} – ${hhmm(a.endTime)}`],
            ['Patient', `${a.patientName} (${a.patientCode})`],
            ['Consultation fee', formatCurrency(a.consultationFee)],
            ['Type', a.type === 'FOLLOW_UP' ? 'Follow-up' : a.type === 'WALK_IN' ? 'Walk-in' : 'New consultation'],
            ['Reason for visit', a.reason],
          ].map(([k, v]) => (
            <div key={k}>
              <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{k}</dt>
              <dd className="mt-0.5 text-sm text-slate-900">{v}</dd>
            </div>
          ))}
          {a.status === 'CANCELLED' && (
            <div className="sm:col-span-2">
              <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Cancellation</dt>
              <dd className="mt-0.5 text-sm text-slate-900">{formatDateTime(a.cancelledAt)} — {a.cancellationReason}</dd>
            </div>
          )}
        </dl>
        <div className="mt-6 flex flex-wrap gap-2 border-t border-slate-100 pt-4 print:hidden">
          {a.canReschedule && (
            <Button to={`/patient/appointments/${a.id}/reschedule`}>
              <CalendarClock className="h-4 w-4" aria-hidden="true" /> Reschedule
            </Button>
          )}
          {a.canCancel && <Button variant="secondary" onClick={() => setCancelling(a)}>Cancel appointment</Button>}
          <Button variant="ghost" onClick={() => window.print()}>
            <Printer className="h-4 w-4" aria-hidden="true" /> Print
          </Button>
          {!a.canCancel && a.status === 'SCHEDULED' && (
            <p className="self-center text-xs text-slate-500">Online changes close shortly before the appointment. Please contact reception.</p>
          )}
        </div>
      </Card>

      <div className="mt-6 print:hidden">
        <Card title="History">
          {history.loading && <Spinner />}
          {history.data && (
            <ol className="space-y-3 border-l-2 border-slate-200 pl-4">
              {history.data.map((h, i) => (
                <li key={i} className="relative">
                  <span className="absolute -left-[1.4rem] top-1.5 h-2.5 w-2.5 rounded-full bg-brand-500" aria-hidden="true" />
                  <p className="text-sm font-medium text-slate-900">{ACTION_LABEL[h.action] || h.action}</p>
                  {h.details && <p className="text-sm text-slate-600">{h.details}</p>}
                  <p className="text-xs text-slate-400">{formatDateTime(h.changedAt)}</p>
                </li>
              ))}
            </ol>
          )}
        </Card>
      </div>

      <CancelAppointmentDialog appointment={cancelling} onClose={() => setCancelling(null)}
        onCancelled={() => { setCancelling(null); reload(); history.reload(); }} />
    </>
  );
}
