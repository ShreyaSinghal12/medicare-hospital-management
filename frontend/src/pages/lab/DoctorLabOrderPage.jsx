import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Download, FileText, Lock, Trash2, Upload } from 'lucide-react';
import { toast } from 'sonner';
import LabResultsTable from '../../components/lab/LabResultsTable.jsx';
import LabStatusBadge from '../../components/lab/LabStatusBadge.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextAreaField } from '../../components/ui/FormField.jsx';
import Modal from '../../components/ui/Modal.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import {
  changeLabOrderStatus, finalizeLabReport, getLabOrder, labReportUrl, removeLabReport, saveLabResults, uploadLabReport,
} from '../../services/labService.js';
import { formatDateLong } from '../../utils/dates.js';
import { formatDateTime } from '../../utils/format.js';

const MAX_BYTES = 5 * 1024 * 1024;
const STEP_LABEL = { SAMPLE_COLLECTED: 'Mark sample collected', PROCESSING: 'Mark processing' };

function fileSize(bytes) {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/** Ordering doctor's workspace: status, results typed by the doctor, PDF report and finalization. */
export default function DoctorLabOrderPage() {
  const { id } = useParams();
  const [order, setOrder] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [results, setResults] = useState({});
  const [dirty, setDirty] = useState(false);
  const [file, setFile] = useState(null);
  const [fileError, setFileError] = useState(null);
  const [busy, setBusy] = useState(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(null);
  const [confirm, setConfirm] = useState(null); // 'finalize' | 'remove'
  const fileInput = useRef(null);

  function apply(o) {
    setOrder(o);
    setResults(Object.fromEntries(o.items.map((i) => [i.id, { resultValue: i.resultValue || '', resultComment: i.resultComment || '' }])));
    setDirty(false);
  }

  async function load() {
    setLoadError(null);
    try {
      apply(await getLabOrder(id));
    } catch (err) {
      setLoadError(err);
    }
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { load(); }, [id]);

  async function run(key, fn, success) {
    setBusy(key);
    try {
      const o = await fn();
      apply(o);
      if (success) toast.success(success);
      return true;
    } catch (err) {
      toast.error(err.message);
      if (err.code === 'CONCURRENT_MODIFICATION') load();
      return false;
    } finally {
      setBusy(null);
    }
  }

  if (loadError) return <ErrorState error={loadError} onRetry={load} />;
  if (!order) return <Spinner />;

  const manage = order.canManage;
  const sampleIn = order.status === 'SAMPLE_COLLECTED' || order.status === 'PROCESSING';
  const steps = order.allowedTransitions.filter((s) => s !== 'CANCELLED');

  function pick(e) {
    const f = e.target.files?.[0] || null;
    setFileError(null);
    if (f && !f.name.toLowerCase().endsWith('.pdf')) setFileError('Choose a PDF file (.pdf)');
    else if (f && f.size > MAX_BYTES) setFileError('The file is larger than 5 MB');
    else if (f && f.size === 0) setFileError('The file is empty');
    setFile(f);
  }

  async function upload() {
    if (!file || fileError) {
      setFileError(fileError || 'Choose a PDF file first');
      return;
    }
    const ok = await run('upload', () => uploadLabReport(order.id, order.version, file), 'Report uploaded');
    if (ok) {
      setFile(null);
      if (fileInput.current) fileInput.current.value = '';
    }
  }

  async function cancelOrder() {
    if (reason.trim().length < 3) {
      setReasonError('Enter a reason (at least 3 characters)');
      return;
    }
    const ok = await run('cancel', () => changeLabOrderStatus(order.id, { status: 'CANCELLED', version: order.version, reason: reason.trim() }), 'Order cancelled');
    if (ok) setCancelOpen(false);
  }

  return (
    <>
      <Link to="/doctor/lab-orders" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Lab orders
      </Link>
      <PageHeader title={`Lab order ${order.orderNo}`}
        description={`${order.patientName} · ${order.patientCode} · visit ${formatDateLong(order.visitDate)}`}
        actions={<LabStatusBadge status={order.status} priority={order.priority} />} />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="min-w-0 space-y-6 lg:col-span-2">
          <Card title="Results" subtitle={manage && sampleIn ? 'Type the values exactly as reported. MediCare does not flag or interpret results.' : undefined}
            actions={!manage && <span className="inline-flex items-center gap-1 text-xs text-slate-500"><Lock className="h-3.5 w-3.5" aria-hidden="true" /> Read-only</span>}>
            {manage && sampleIn ? (
              <form onSubmit={(e) => { e.preventDefault(); run('results', () => saveLabResults(order.id, order.version,
                order.items.map((i) => ({ itemId: i.id, ...results[i.id] }))), 'Results saved'); }}>
                <div className="space-y-4">
                  {order.items.map((i) => (
                    <fieldset key={i.id} className="grid gap-3 rounded-lg border border-slate-200 p-3 sm:grid-cols-[1fr_9rem_1fr]">
                      <legend className="px-1 text-sm font-semibold text-slate-900">{i.testName}</legend>
                      <p className="text-xs text-slate-500 sm:col-span-3">
                        Reference: {i.referenceRange ? `${i.referenceRange}${i.unit ? ` ${i.unit}` : ''}` : 'not set'}
                      </p>
                      <label className="text-sm">
                        <span className="mb-1 block font-medium text-slate-700">Result{i.unit ? ` (${i.unit})` : ''}</span>
                        <input value={results[i.id]?.resultValue || ''} maxLength={100}
                          aria-label={`Result for ${i.testName}`}
                          onChange={(e) => { setResults((r) => ({ ...r, [i.id]: { ...r[i.id], resultValue: e.target.value } })); setDirty(true); }}
                          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
                      </label>
                      <span className="hidden sm:block" />
                      <label className="text-sm sm:col-span-3">
                        <span className="mb-1 block font-medium text-slate-700">Comment</span>
                        <input value={results[i.id]?.resultComment || ''} maxLength={500}
                          aria-label={`Comment for ${i.testName}`}
                          onChange={(e) => { setResults((r) => ({ ...r, [i.id]: { ...r[i.id], resultComment: e.target.value } })); setDirty(true); }}
                          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
                      </label>
                    </fieldset>
                  ))}
                </div>
                <Button type="submit" className="mt-4" loading={busy === 'results'} disabled={!dirty}>Save results</Button>
              </form>
            ) : (
              <>
                {manage && order.status === 'ORDERED' && <p className="mb-3 text-sm text-slate-500">Results can be entered once the sample is collected.</p>}
                <LabResultsTable items={order.items} />
              </>
            )}
          </Card>

          <Card title="Report (PDF)" actions={<FileText className="h-5 w-5 text-brand-700" aria-hidden="true" />}>
            {order.report ? (
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 p-3 text-sm">
                <div>
                  <p className="font-medium text-slate-900">{order.report.fileName}</p>
                  <p className="text-slate-500">{fileSize(order.report.sizeBytes)} · uploaded {formatDateTime(order.report.uploadedAt)} by {order.report.uploadedBy}</p>
                  {order.report.finalized && <p className="text-emerald-700">Finalized {formatDateTime(order.report.finalizedAt)} · visible to the patient</p>}
                </div>
                <div className="flex gap-2">
                  <a href={labReportUrl(order.id)} download
                    className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50">
                    <Download className="h-4 w-4" aria-hidden="true" /> Download
                  </a>
                  {manage && (
                    <Button size="sm" variant="ghost" onClick={() => setConfirm('remove')} aria-label="Remove report">
                      <Trash2 className="h-4 w-4" aria-hidden="true" />
                    </Button>
                  )}
                </div>
              </div>
            ) : <p className="text-sm text-slate-500">No report uploaded yet.</p>}

            {manage && sampleIn && (
              <div className="mt-4 space-y-2">
                <label htmlFor="report-file" className="block text-sm font-medium text-slate-700">
                  {order.report ? 'Replace report' : 'Upload report'} <span className="font-normal text-slate-500">(PDF, max 5 MB)</span>
                </label>
                <div className="flex flex-wrap items-center gap-2">
                  <input id="report-file" ref={fileInput} type="file" accept="application/pdf,.pdf" onChange={pick}
                    aria-invalid={!!fileError} aria-describedby={fileError ? 'report-file-error' : undefined}
                    className="block text-sm text-slate-700 file:mr-3 file:rounded-lg file:border-0 file:bg-brand-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-brand-700 hover:file:bg-brand-100" />
                  <Button size="sm" onClick={upload} loading={busy === 'upload'} disabled={!file}>
                    <Upload className="h-4 w-4" aria-hidden="true" /> Upload
                  </Button>
                </div>
                {fileError && <p id="report-file-error" role="alert" className="text-sm text-red-600">{fileError}</p>}
              </div>
            )}
            {manage && order.status === 'ORDERED' && <p className="mt-3 text-sm text-slate-500">Mark the sample as collected before uploading the report.</p>}
          </Card>
        </div>

        <div className="min-w-0 space-y-6">
          {manage && (
            <Card title="Actions">
              <div className="flex flex-col gap-2">
                {steps.map((s) => (
                  <Button key={s} variant="secondary" loading={busy === s}
                    onClick={() => run(s, () => changeLabOrderStatus(order.id, { status: s, version: order.version }), 'Status updated')}>
                    {STEP_LABEL[s]}
                  </Button>
                ))}
                <Button onClick={() => setConfirm('finalize')} disabled={!order.report || order.status !== 'PROCESSING' || dirty}>
                  Finalize and release to patient
                </Button>
                {dirty && <p className="text-xs text-amber-700">Save the results before finalizing.</p>}
                {order.allowedTransitions.includes('CANCELLED') && (
                  <Button variant="ghost" onClick={() => { setReason(''); setReasonError(null); setCancelOpen(true); }}>Cancel order</Button>
                )}
              </div>
            </Card>
          )}
          <Card title="Order">
            <dl className="space-y-2 text-sm">
              <div><dt className="text-slate-500">Ordered by</dt><dd className="font-medium text-slate-900">{order.doctorName}</dd></div>
              <div><dt className="text-slate-500">Ordered</dt><dd>{formatDateTime(order.orderedAt)}</dd></div>
              {order.sampleCollectedAt && <div><dt className="text-slate-500">Sample collected</dt><dd>{formatDateTime(order.sampleCollectedAt)}</dd></div>}
              {order.completedAt && <div><dt className="text-slate-500">Completed</dt><dd>{formatDateTime(order.completedAt)}</dd></div>}
              {order.cancelledAt && <div><dt className="text-slate-500">Cancelled</dt><dd>{formatDateTime(order.cancelledAt)} · {order.cancellationReason}</dd></div>}
              <div><dt className="text-slate-500">Clinical notes</dt><dd className="whitespace-pre-line">{order.clinicalNotes || '—'}</dd></div>
            </dl>
            <div className="mt-4 flex flex-col gap-1 text-sm">
              <Link to={`/doctor/consultations/${order.consultationId}`} className="font-medium text-brand-700 hover:underline">Open consultation</Link>
              <Link to={`/doctor/patients/${order.patientId}`} className="font-medium text-brand-700 hover:underline">Patient record</Link>
            </div>
          </Card>
        </div>
      </div>

      <Modal open={cancelOpen} title={`Cancel ${order.orderNo}?`} onClose={() => setCancelOpen(false)} busy={busy === 'cancel'}>
        <TextAreaField label="Reason" required rows={3} maxLength={500} value={reason} error={reasonError}
          onChange={(e) => { setReason(e.target.value); setReasonError(null); }} />
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCancelOpen(false)} disabled={busy === 'cancel'}>Keep order</Button>
          <Button variant="danger" onClick={cancelOrder} loading={busy === 'cancel'}>Cancel order</Button>
        </div>
      </Modal>

      <ConfirmDialog open={confirm === 'finalize'} title="Finalize and release this report?"
        message="The results and the PDF become read-only and the patient is notified that the report is ready."
        confirmLabel="Finalize" loading={busy === 'finalize'} onCancel={() => setConfirm(null)}
        onConfirm={async () => { await run('finalize', () => finalizeLabReport(order.id, order.version), 'Report released to the patient'); setConfirm(null); }} />
      <ConfirmDialog open={confirm === 'remove'} tone="danger" title="Remove the uploaded report?"
        message="The PDF is deleted. You can upload another one." confirmLabel="Remove" loading={busy === 'remove'}
        onCancel={() => setConfirm(null)}
        onConfirm={async () => { await run('remove', () => removeLabReport(order.id, order.version), 'Report removed'); setConfirm(null); }} />
    </>
  );
}
