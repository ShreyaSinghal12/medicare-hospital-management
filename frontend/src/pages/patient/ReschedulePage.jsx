import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';
import SlotPicker from '../../components/scheduling/SlotPicker.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getAppointment, rescheduleAppointment } from '../../services/appointmentService.js';
import { formatDateLong, hhmm } from '../../utils/dates.js';

/** Choose a new slot with the same doctor. The server swaps slots atomically. */
export default function ReschedulePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: a, loading, error, reload } = useApi(() => getAppointment(id), [id]);
  const [selected, setSelected] = useState(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  async function submit() {
    setBusy(true);
    try {
      const updated = await rescheduleAppointment(id, { date: selected.date, startTime: selected.startTime, reason: reason || null });
      toast.success(`Moved to ${formatDateLong(updated.date)} at ${hhmm(updated.startTime)}`);
      navigate(`/patient/appointments/${id}`, { replace: true });
    } catch (err) {
      toast.error(err.message);
      if (err.code === 'SLOT_ALREADY_BOOKED') {
        setSelected(null);
        setRefreshKey((k) => k + 1);
      }
    } finally {
      setBusy(false);
    }
  }

  if (loading && !a) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  return (
    <>
      <Link to={`/patient/appointments/${id}`} className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to appointment
      </Link>
      <PageHeader title="Reschedule appointment"
        description={`${a.doctorName} · currently ${formatDateLong(a.date)} at ${hhmm(a.startTime)} (${a.referenceNo})`} />
      {!a.canReschedule ? (
        <Card>
          <p className="text-sm text-slate-600">This appointment can no longer be rescheduled online. Please contact reception.</p>
        </Card>
      ) : (
        <Card>
          <SlotPicker doctorId={a.doctorId} value={selected} onChange={setSelected} initialDate={a.date} refreshKey={refreshKey} />
          <div className="mt-5 grid gap-4 border-t border-slate-100 pt-4 sm:grid-cols-[1fr_auto] sm:items-end">
            <TextField label="Reason (optional)" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
            <Button onClick={submit} loading={busy} disabled={!selected}>
              {selected ? `Move to ${selected.date} ${selected.startTime}` : 'Choose a new time'}
            </Button>
          </div>
        </Card>
      )}
    </>
  );
}
