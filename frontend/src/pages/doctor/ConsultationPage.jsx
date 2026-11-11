import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, FileClock, Lock, Save } from 'lucide-react';
import { toast } from 'sonner';
import AllergiesConditionsPanel from '../../components/clinical/AllergiesConditionsPanel.jsx';
import ConsultationLabCard from '../../components/lab/ConsultationLabCard.jsx';
import ConsultationPrescriptionCard from '../../components/prescriptions/ConsultationPrescriptionCard.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import {
  amendConsultation, finalizeConsultation, getAmendments, getConsultation, getPatientRecord, saveConsultationDraft,
} from '../../services/clinicalService.js';
import { addDaysIso, formatDateLong, hhmm, todayIsoLocal } from '../../utils/dates.js';
import { formatDateTime } from '../../utils/format.js';

const FIELDS = [
  { name: 'symptoms', label: 'Symptoms (as reported)', rows: 3 },
  { name: 'observations', label: 'Clinical observations', rows: 3, hint: 'Not shown to the patient.' },
  { name: 'diagnosis', label: 'Diagnosis', rows: 2, required: true },
  { name: 'treatmentPlan', label: 'Treatment plan', rows: 3 },
  { name: 'notes', label: 'Additional notes', rows: 2, hint: 'Internal; not shown to the patient.' },
];
const LABELS = Object.fromEntries([...FIELDS.map((f) => [f.name, f.label]), ['followUpDate', 'Follow-up date'], ['followUpNotes', 'Follow-up notes']]);

function toForm(c) {
  return {
    symptoms: c.symptoms || '', observations: c.observations || '', diagnosis: c.diagnosis || '',
    treatmentPlan: c.treatmentPlan || '', notes: c.notes || '', followUpDate: c.followUpDate || '', followUpNotes: c.followUpNotes || '',
  };
}

function toPayload(values, version) {
  const p = { version };
  Object.entries(values).forEach(([k, v]) => { p[k] = v === '' ? null : v; });
  return p;
}

/**
 * Consultation workspace. Everything clinical is typed by the doctor — the system never
 * proposes diagnoses, treatments or doses.
 */
export default function ConsultationPage() {
  const { id } = useParams();
  const { data: c, loading, error, reload, } = useApi(() => getConsultation(id), [id]);
  const record = useApi(() => (c ? getPatientRecord(c.patientId) : Promise.resolve(null)), [c?.patientId]);
  const amendments = useApi(() => (c?.status === 'FINALIZED' ? getAmendments(id) : Promise.resolve([])), [id, c?.status, c?.amendmentCount]);
  const [amending, setAmending] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(null);
  const [confirmFinalize, setConfirmFinalize] = useState(false);
  const [busy, setBusy] = useState(false);
  const { register, handleSubmit, reset, getValues, formState: { errors, isDirty } } = useForm();

  useEffect(() => { if (c) reset(toForm(c)); }, [c, reset]);

  const editable = c && (c.canEdit || (c.canAmend && amending));

  async function run(action, success) {
    setBusy(true);
    try {
      await action();
      toast.success(success);
      setAmending(false);
      setReason('');
      reload();
    } catch (err) {
      toast.error(err.code === 'CONCURRENT_MODIFICATION' ? `${err.message}` : err.message);
      if (err.code === 'CONCURRENT_MODIFICATION') reload();
    } finally {
      setBusy(false);
      setConfirmFinalize(false);
    }
  }

  const saveDraft = handleSubmit((v) => run(() => saveConsultationDraft(id, toPayload(v, c.version)), 'Draft saved'));
  const finalize = () => run(() => finalizeConsultation(id, toPayload(getValues(), c.version)), 'Consultation finalized; appointment completed');
  const submitAmendment = handleSubmit((v) => {
    if (reason.trim().length < 5) {
      setReasonError('Give a reason of at least 5 characters');
      return undefined;
    }
    return run(() => amendConsultation(id, toPayload(v, c.version), reason.trim()), 'Amendment saved; previous version kept');
  });

  if (loading && !c) return <Spinner label="Loading consultation…" />;
  if (error) return <ErrorState error={error} onRetry={reload} title={error.status === 403 || error.status === 404 ? 'Consultation not available' : undefined} />;

  return (
    <>
      <Link to="/doctor" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Dashboard
      </Link>
      <PageHeader
        title={`Consultation · ${c.patientName}`}
        description={`${c.patientCode} · ${formatDateLong(c.date)} ${hhmm(c.startTime)} · ${c.appointmentReference}`}
        actions={<StatusBadge tone={c.status === 'FINALIZED' ? 'green' : 'amber'}>{c.status === 'FINALIZED' ? `Finalized${c.amendmentCount ? ` · ${c.amendmentCount} amendment(s)` : ''}` : 'Draft'}</StatusBadge>}
      />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card title="Visit" subtitle={`Reason given at booking: ${c.visitReason || '—'}`}>
            <Link to={`/doctor/patients/${c.patientId}`} className="text-sm font-medium text-brand-700 hover:underline">Open full patient record</Link>
          </Card>

          <Card title="Clinical notes" subtitle={editable ? 'Entered by you. No automated suggestions are made.' : undefined}
            actions={c.status === 'FINALIZED' && !amending && <span className="inline-flex items-center gap-1 text-xs text-slate-500"><Lock className="h-3.5 w-3.5" aria-hidden="true" /> Read-only</span>}>
            <form noValidate className="space-y-4" onSubmit={(e) => e.preventDefault()}>
              {FIELDS.map((f) => (
                <TextAreaField key={f.name} label={f.label} rows={f.rows} required={f.required} hint={f.hint} disabled={!editable}
                  error={errors[f.name]?.message}
                  {...register(f.name, { maxLength: { value: 5000, message: 'Max 5000 characters' } })} />
              ))}
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField label="Follow-up date" type="date" min={addDaysIso(todayIsoLocal(), 1)} disabled={!editable}
                  error={errors.followUpDate?.message}
                  {...register('followUpDate', { validate: (v) => !v || v > todayIsoLocal() || 'Must be in the future' })} />
                <TextField label="Follow-up notes" disabled={!editable} {...register('followUpNotes', { maxLength: { value: 500, message: 'Max 500' } })} />
              </div>

              {c.canEdit && (
                <div className="flex flex-wrap gap-2 border-t border-slate-100 pt-4">
                  <Button variant="secondary" onClick={saveDraft} loading={busy} disabled={!isDirty}>
                    <Save className="h-4 w-4" aria-hidden="true" /> Save draft
                  </Button>
                  <Button onClick={handleSubmit(() => setConfirmFinalize(true))} disabled={busy}>Finalize consultation</Button>
                </div>
              )}
              {c.canAmend && !amending && (
                <div className="border-t border-slate-100 pt-4">
                  <Button variant="secondary" onClick={() => setAmending(true)}>Amend notes</Button>
                </div>
              )}
              {c.canAmend && amending && (
                <div className="space-y-3 border-t border-slate-100 pt-4">
                  <TextField label="Reason for amendment" required value={reason} error={reasonError} maxLength={500}
                    hint="The previous version is kept with this reason."
                    onChange={(e) => { setReason(e.target.value); setReasonError(null); }} />
                  <div className="flex gap-2">
                    <Button onClick={submitAmendment} loading={busy}>Save amendment</Button>
                    <Button variant="secondary" onClick={() => { setAmending(false); reset(toForm(c)); }}>Discard</Button>
                  </div>
                </div>
              )}
            </form>
          </Card>

          {c.status === 'FINALIZED' && (amendments.data || []).length > 0 && (
            <Card title="Amendment history">
              <ol className="space-y-4">
                {amendments.data.map((a) => (
                  <li key={a.id} className="rounded-lg border border-slate-200 p-3 text-sm">
                    <p className="flex items-center gap-2 font-medium text-slate-900">
                      <FileClock className="h-4 w-4 text-slate-500" aria-hidden="true" /> {formatDateTime(a.amendedAt)} · {a.amendedBy}
                    </p>
                    <p className="mt-1 text-slate-600">Reason: {a.reason}</p>
                    <details className="mt-2">
                      <summary className="cursor-pointer text-xs font-medium text-brand-700">Values before this amendment</summary>
                      <dl className="mt-2 space-y-1">
                        {Object.entries(a.previousValues).filter(([, v]) => v).map(([k, v]) => (
                          <div key={k}><dt className="inline text-slate-500">{LABELS[k] || k}: </dt><dd className="inline whitespace-pre-line">{v}</dd></div>
                        ))}
                      </dl>
                    </details>
                  </li>
                ))}
              </ol>
            </Card>
          )}
        </div>
        <div className="space-y-6">
          <ConsultationPrescriptionCard consultationId={c.id} canWrite={c.canEdit || c.canAmend} />
          <ConsultationLabCard consultationId={c.id} canOrder={c.canEdit || c.canAmend} />
          {record.data ? (
            <AllergiesConditionsPanel patientId={c.patientId} allergies={record.data.allergies} conditions={record.data.conditions}
              editable onChanged={record.reload} />
          ) : <Spinner />}
        </div>
      </div>

      <ConfirmDialog open={confirmFinalize} tone="primary" title="Finalize this consultation?"
        message="The notes become read-only (later changes are recorded as amendments with a reason), the patient can see them, and the appointment is marked completed."
        confirmLabel="Finalize" loading={busy} onCancel={() => setConfirmFinalize(false)} onConfirm={finalize} />
    </>
  );
}
