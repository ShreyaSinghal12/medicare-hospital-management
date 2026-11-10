import { useState } from 'react';
import { toast } from 'sonner';
import { rescheduleAppointment } from '../../services/appointmentService.js';
import { formatDateShort, hhmm } from '../../utils/dates.js';
import SlotPicker from '../scheduling/SlotPicker.jsx';
import Button from '../ui/Button.jsx';
import { TextField } from '../ui/FormField.jsx';
import Modal from '../ui/Modal.jsx';

/** Front-desk reschedule: same doctor, new slot; the server swaps slots atomically. */
export default function RescheduleDialog({ appointment: a, onClose, onDone }) {
  const [selected, setSelected] = useState(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);

  async function submit() {
    setBusy(true);
    try {
      const updated = await rescheduleAppointment(a.id, { date: selected.date, startTime: selected.startTime, reason: reason || null });
      toast.success(`Moved to ${formatDateShort(updated.date)} ${hhmm(updated.startTime)}`);
      onDone(updated);
    } catch (err) {
      toast.error(err.message);
      if (err.code === 'SLOT_ALREADY_BOOKED') {
        setSelected(null);
        setRefresh((r) => r + 1);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={!!a} size="lg" onClose={onClose} busy={busy}
      title={a ? `Reschedule ${a.patientName}` : ''}
      description={a ? `${a.doctorName} · currently ${formatDateShort(a.date)} ${hhmm(a.startTime)} (${a.referenceNo})` : ''}>
      {a && (
        <>
          <SlotPicker doctorId={a.doctorId} value={selected} onChange={setSelected} initialDate={a.date} refreshKey={refresh} />
          <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
            <TextField label="Reason (optional)" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
            <Button onClick={submit} loading={busy} disabled={!selected}>
              {selected ? `Move to ${selected.date} ${selected.startTime}` : 'Choose a time'}
            </Button>
          </div>
        </>
      )}
    </Modal>
  );
}
