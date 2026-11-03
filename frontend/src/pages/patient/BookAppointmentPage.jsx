import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, CalendarCheck } from 'lucide-react';
import { toast } from 'sonner';
import SlotPicker from '../../components/scheduling/SlotPicker.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextAreaField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { bookAppointment } from '../../services/appointmentService.js';
import { getDoctor } from '../../services/doctorService.js';
import { formatDateLong } from '../../utils/dates.js';
import { formatCurrency } from '../../utils/format.js';

/** Step 2 of booking: confirm slot, enter the reason, submit. Handles "slot just taken" (409). */
export default function BookAppointmentPage() {
  const { doctorId } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { data: doctor, loading, error, reload } = useApi(() => getDoctor(doctorId), [doctorId]);
  const [selected, setSelected] = useState(
    params.get('date') && params.get('time') ? { date: params.get('date'), startTime: params.get('time') } : null,
  );
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const followUpOfId = params.get('followUpOf');

  async function submit() {
    if (!selected) {
      toast.error('Please choose a time slot');
      return;
    }
    if (reason.trim().length < 3) {
      setReasonError('Please describe the reason for your visit (at least 3 characters)');
      return;
    }
    setSubmitting(true);
    try {
      const appt = await bookAppointment({
        doctorId: Number(doctorId), date: selected.date, startTime: selected.startTime, reason: reason.trim(),
        followUpOfId: followUpOfId ? Number(followUpOfId) : null,
      });
      toast.success(`Booked! Reference ${appt.referenceNo}`);
      navigate(`/patient/appointments/${appt.id}?booked=1`, { replace: true });
    } catch (err) {
      if (err.code === 'SLOT_ALREADY_BOOKED') {
        toast.error(err.message);
        setSelected(null);
        setRefreshKey((k) => k + 1);
      } else if (err.fieldErrors?.length) {
        setReasonError(err.fieldErrors.map((f) => f.message).join('. '));
      } else {
        toast.error(err.message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <Spinner label="Loading doctor…" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  return (
    <>
      <Link to={`/patient/doctors/${doctorId}`} className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to doctor profile
      </Link>
      <PageHeader title="Book an appointment" description={`${doctor.fullName} · ${doctor.specialization} · ${doctor.departmentName}`} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <Card title="1. Choose a time">
            <SlotPicker doctorId={doctorId} value={selected} onChange={setSelected} initialDate={selected?.date} refreshKey={refreshKey} />
          </Card>
        </div>
        <Card title="2. Confirm">
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between gap-3"><dt className="text-slate-500">Doctor</dt><dd className="text-right font-medium">{doctor.fullName}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-slate-500">Date</dt><dd className="text-right font-medium">{selected ? formatDateLong(selected.date) : '—'}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-slate-500">Time</dt><dd className="text-right font-medium">{selected ? selected.startTime : '—'}</dd></div>
            <div className="flex justify-between gap-3"><dt className="text-slate-500">Fee</dt><dd className="text-right font-medium">{formatCurrency(doctor.consultationFee)}</dd></div>
            {followUpOfId && <div className="rounded bg-brand-50 p-2 text-brand-800">Follow-up appointment</div>}
          </dl>
          <div className="mt-4">
            <TextAreaField label="Reason for visit" required rows={3} maxLength={500} value={reason} error={reasonError}
              hint="Shared only with your doctor and the clinic staff handling your booking."
              onChange={(e) => { setReason(e.target.value); setReasonError(null); }} />
          </div>
          <Button className="mt-4 w-full" onClick={submit} loading={submitting} disabled={!selected}>
            <CalendarCheck className="h-4 w-4" aria-hidden="true" /> Confirm booking
          </Button>
        </Card>
      </div>
    </>
  );
}
