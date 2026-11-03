import { useState } from 'react';
import { toast } from 'sonner';
import { cancelAppointment } from '../../services/appointmentService.js';
import { formatDateShort, hhmm } from '../../utils/dates.js';
import ConfirmDialog from '../ui/ConfirmDialog.jsx';
import { TextAreaField } from '../ui/FormField.jsx';

/** Confirmation dialog that collects a reason and cancels the appointment. */
export default function CancelAppointmentDialog({ appointment, onClose, onCancelled }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function confirm() {
    if (reason.trim().length < 3) {
      setError('Please give a short reason (at least 3 characters)');
      return;
    }
    setBusy(true);
    try {
      const updated = await cancelAppointment(appointment.id, reason.trim());
      toast.success(`Appointment ${updated.referenceNo} cancelled`);
      setReason('');
      onCancelled(updated);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <ConfirmDialog
      open={!!appointment}
      title="Cancel this appointment?"
      message={appointment ? `${appointment.doctorName} · ${formatDateShort(appointment.date)} at ${hhmm(appointment.startTime)} (${appointment.referenceNo}). The slot will be released for other patients.` : ''}
      confirmLabel="Cancel appointment"
      cancelLabel="Keep it"
      loading={busy}
      onCancel={() => { setReason(''); setError(null); onClose(); }}
      onConfirm={confirm}
    >
      <TextAreaField label="Reason for cancelling" required rows={2} value={reason} maxLength={500} error={error}
        onChange={(e) => { setReason(e.target.value); setError(null); }} />
    </ConfirmDialog>
  );
}
