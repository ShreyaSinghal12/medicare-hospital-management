import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, FilePen } from 'lucide-react';
import { toast } from 'sonner';
import PrescriptionEditor from '../../components/prescriptions/PrescriptionEditor.jsx';
import PrescriptionStatusBadge from '../../components/prescriptions/PrescriptionStatusBadge.jsx';
import PrescriptionView from '../../components/prescriptions/PrescriptionView.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import Modal from '../../components/ui/Modal.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import {
  discardPrescription, finalizePrescription, getPrescription, getPrescriptionVersions, revisePrescription, updatePrescription,
} from '../../services/prescriptionService.js';
import { formatDateTime } from '../../utils/format.js';

/** Doctor: edit/finalize a draft, or view a finalized version and start a revision. */
export default function PrescriptionWorkspacePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: rx, loading, error, reload } = useApi(() => getPrescription(id), [id]);
  const versions = useApi(() => getPrescriptionVersions(id), [id, rx?.status, rx?.version]);
  const [busy, setBusy] = useState(null);
  const [confirm, setConfirm] = useState(null); // { payload, dirty } awaiting finalize confirmation
  const [revising, setRevising] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(null);
  const [discarding, setDiscarding] = useState(false);

  async function save(payload) {
    setBusy('save');
    try {
      await updatePrescription(id, { ...payload, version: rx.version });
      toast.success('Draft saved');
      reload();
    } catch (err) {
      toast.error(err.message);
      if (err.code === 'CONCURRENT_MODIFICATION') reload();
    } finally {
      setBusy(null);
    }
  }

  async function doFinalize() {
    setBusy('finalize');
    try {
      const saved = await updatePrescription(id, { ...confirm.payload, version: rx.version });
      await finalizePrescription(id, saved.version);
      toast.success('Prescription finalized; the patient has been notified');
      setConfirm(null);
      reload();
    } catch (err) {
      toast.error(err.message);
      if (err.code === 'CONCURRENT_MODIFICATION') reload();
    } finally {
      setBusy(null);
    }
  }

  async function startRevision() {
    if (reason.trim().length < 5) {
      setReasonError('Give a reason of at least 5 characters');
      return;
    }
    setBusy('revise');
    try {
      const draft = await revisePrescription(id, reason.trim());
      toast.success(`Version ${draft.versionNumber} draft created`);
      setRevising(false);
      setReason('');
      navigate(`/doctor/prescriptions/${draft.id}`);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(null);
    }
  }

  async function discard() {
    setBusy('discard');
    try {
      await discardPrescription(id);
      toast.success('Draft discarded');
      navigate(rx.previousVersionId ? `/doctor/prescriptions/${rx.previousVersionId}` : `/doctor/consultations/${rx.consultationId}`, { replace: true });
    } catch (err) {
      toast.error(err.message);
      setBusy(null);
    }
  }

  if (loading && !rx) return <Spinner label="Loading prescription…" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  return (
    <>
      <Link to={`/doctor/consultations/${rx.consultationId}`} className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to consultation
      </Link>
      <PageHeader title={`${rx.prescriptionNo} · ${rx.patientName}`} description={`${rx.patientCode} · version ${rx.versionNumber}`}
        actions={<PrescriptionStatusBadge status={rx.status} versionNumber={rx.versionNumber} />} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {rx.canEdit ? (
            <Card title={rx.versionNumber > 1 ? `Revision draft (version ${rx.versionNumber})` : 'Draft prescription'}
              subtitle="Enter each medicine exactly as prescribed. Nothing is suggested by the system."
              actions={<Button size="sm" variant="ghost" onClick={() => setDiscarding(true)}>Discard draft</Button>}>
              {rx.amendmentReason && <p className="mb-4 rounded bg-slate-50 p-2 text-sm text-slate-600">Revision reason: {rx.amendmentReason}</p>}
              <PrescriptionEditor rx={rx} busy={busy} onSave={save} onFinalize={(payload) => setConfirm({ payload })} />
            </Card>
          ) : (
            <PrescriptionView rx={rx} actions={rx.canRevise && (
              <Button variant="secondary" onClick={() => setRevising(true)}><FilePen className="h-4 w-4" aria-hidden="true" /> Revise</Button>
            )} />
          )}
        </div>
        <Card title="Versions">
          {versions.loading && <Spinner />}
          <ul className="space-y-2 text-sm">
            {(versions.data || []).map((v) => (
              <li key={v.id} className="flex items-center justify-between gap-2">
                <Link to={`/doctor/prescriptions/${v.id}`} className={`font-medium hover:underline ${String(v.id) === String(id) ? 'text-slate-900' : 'text-brand-700'}`}>
                  Version {v.versionNumber}
                </Link>
                <span className="flex items-center gap-2">
                  <span className="text-xs text-slate-400">{formatDateTime(v.finalizedAt || v.createdAt)}</span>
                  <PrescriptionStatusBadge status={v.status} />
                </span>
              </li>
            ))}
          </ul>
        </Card>
      </div>

      <ConfirmDialog open={!!confirm} tone="primary" title="Finalize this prescription?"
        message={`It becomes read-only and visible to the patient${rx.versionNumber > 1 ? '; the previous version will be marked superseded' : ''}. Later changes need a new revision.`}
        confirmLabel="Finalize" loading={busy === 'finalize'} onCancel={() => setConfirm(null)} onConfirm={doFinalize} />
      <ConfirmDialog open={discarding} title="Discard this draft?" message="The draft is deleted. The patient never saw it."
        confirmLabel="Discard" loading={busy === 'discard'} onCancel={() => setDiscarding(false)} onConfirm={discard} />
      <Modal open={revising} title="Revise prescription" description="A new draft version is created; the current version stays valid until you finalize the revision."
        onClose={() => setRevising(false)} busy={busy === 'revise'}>
        <TextField label="Reason for revision" required value={reason} error={reasonError} maxLength={500}
          onChange={(e) => { setReason(e.target.value); setReasonError(null); }} />
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setRevising(false)}>Cancel</Button>
          <Button onClick={startRevision} loading={busy === 'revise'}>Create revision draft</Button>
        </div>
      </Modal>
    </>
  );
}
