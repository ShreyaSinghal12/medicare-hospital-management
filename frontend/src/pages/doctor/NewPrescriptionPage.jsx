import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';
import PrescriptionEditor from '../../components/prescriptions/PrescriptionEditor.jsx';
import Card from '../../components/ui/Card.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import { createPrescription, finalizePrescription } from '../../services/prescriptionService.js';

/** Empty prescription form for a consultation. Nothing is stored until the doctor saves. */
export default function NewPrescriptionPage() {
  const { consultationId } = useParams();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(null);
  const [pending, setPending] = useState(null);

  async function save(payload) {
    setBusy('save');
    try {
      const rx = await createPrescription({ ...payload, consultationId: Number(consultationId) });
      toast.success(`Draft ${rx.prescriptionNo} saved`);
      navigate(`/doctor/prescriptions/${rx.id}`, { replace: true });
    } catch (err) {
      toast.error(err.message);
      setBusy(null);
    }
  }

  async function finalize() {
    setBusy('finalize');
    try {
      const rx = await createPrescription({ ...pending, consultationId: Number(consultationId) });
      await finalizePrescription(rx.id, rx.version);
      toast.success('Prescription finalized; the patient has been notified');
      navigate(`/doctor/prescriptions/${rx.id}`, { replace: true });
    } catch (err) {
      toast.error(err.message);
      setBusy(null);
      setPending(null);
    }
  }

  return (
    <>
      <Link to={`/doctor/consultations/${consultationId}`} className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to consultation
      </Link>
      <PageHeader title="New prescription" description="Enter each medicine exactly as prescribed. Nothing is suggested by the system." />
      <Card>
        <PrescriptionEditor rx={null} busy={busy} onSave={save} onFinalize={(payload) => setPending(payload)} />
      </Card>
      <ConfirmDialog open={!!pending} tone="primary" title="Finalize this prescription?"
        message="It becomes read-only and visible to the patient. Later changes need a new revision."
        confirmLabel="Finalize" loading={busy === 'finalize'} onCancel={() => setPending(null)} onConfirm={finalize} />
    </>
  );
}
