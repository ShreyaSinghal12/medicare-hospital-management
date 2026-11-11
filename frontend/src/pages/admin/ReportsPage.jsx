import { useState } from 'react';
import { Download, FileText } from 'lucide-react';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import { previewReport, REPORT_TYPES, reportExportUrl } from '../../services/adminInsightsService.js';
import { addDaysIso, todayIsoLocal } from '../../utils/dates.js';

/** Preview a report, then download it as CSV or PDF. Exports are recorded in the audit log. */
export default function ReportsPage() {
  const today = todayIsoLocal();
  const [type, setType] = useState('APPOINTMENTS');
  const [from, setFrom] = useState(addDaysIso(today, -29));
  const [to, setTo] = useState(today);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const params = { from, to };

  async function preview(e) {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setReport(await previewReport(type, params));
    } catch (err) {
      setReport(null);
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  const link = (format, Icon) => (
    <a href={reportExportUrl(type, format, params)} download
      className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
      <Icon className="h-4 w-4" aria-hidden="true" /> Download {format}
    </a>
  );

  return (
    <>
      <PageHeader title="Reports" description="Operational data only — no diagnoses, visit reasons, prescriptions or test results. Up to 366 days and 10,000 rows." />
      <Card>
        <form onSubmit={preview} className="grid gap-3 sm:grid-cols-[1fr_10rem_10rem_auto] sm:items-end">
          <SelectField label="Report" value={type} onChange={(e) => { setType(e.target.value); setReport(null); }}
            options={Object.entries(REPORT_TYPES).map(([value, label]) => ({ value, label }))} />
          <TextField label="From" type="date" value={from} max={to} onChange={(e) => { setFrom(e.target.value); setReport(null); }} />
          <TextField label="To" type="date" value={to} min={from} onChange={(e) => { setTo(e.target.value); setReport(null); }} />
          <Button type="submit" loading={busy}>Preview</Button>
        </form>
        {error && <div className="mt-4"><ErrorState error={error} title="Could not build the report" /></div>}
      </Card>
      {report && (
        <div className="mt-6">
          <Card title={report.title} subtitle={`${report.from} to ${report.to} · ${report.totalRows} row(s)${report.truncated ? ' · preview shows the first 100' : ''}`}
            actions={<div className="flex flex-wrap gap-2">{link('CSV', Download)}{link('PDF', FileText)}</div>}>
            {report.rows.length === 0 ? <EmptyState title="No records in this period" /> : (
              <div className="relative max-h-[28rem] overflow-auto" tabIndex={0} role="region" aria-label="Scrollable table">
                <table className="w-full min-w-[40rem] text-left text-xs">
                  <thead className="sticky top-0 bg-white text-slate-500">
                    <tr>{report.columns.map((c) => <th key={c} scope="col" className="border-b border-slate-200 py-2 pr-3 font-semibold">{c}</th>)}</tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {report.rows.map((r, i) => <tr key={i}>{r.map((v, j) => <td key={j} className="py-1.5 pr-3 text-slate-700">{v}</td>)}</tr>)}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      )}
    </>
  );
}
