import { useState } from 'react';
import { ScrollText } from 'lucide-react';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { searchAuditLog } from '../../services/adminInsightsService.js';
import { addDaysIso, todayIsoLocal } from '../../utils/dates.js';
import { formatDateTime } from '../../utils/format.js';

const TONE = { SUCCESS: 'green', DENIED: 'red', FAILURE: 'amber' };

/** Read-only audit log with filters. Entries cannot be edited or deleted. */
export default function AuditLogPage() {
  const today = todayIsoLocal();
  const [actorInput, setActorInput] = useState('');
  const [actionInput, setActionInput] = useState('');
  const actor = useDebouncedValue(actorInput);
  const action = useDebouncedValue(actionInput);
  const [outcome, setOutcome] = useState('');
  const [from, setFrom] = useState(addDaysIso(today, -6));
  const [to, setTo] = useState(today);
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useApi(() => searchAuditLog({ actor, action, outcome, from, to, page }), [actor, action, outcome, from, to, page]);
  const rows = data?.content || [];
  const reset = (fn) => (e) => { fn(e.target.value); setPage(0); };

  return (
    <>
      <PageHeader title="Audit log" description="Sign-ins, record access, denials and every change. Read-only; export it from Reports." />
      <Card>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <TextField label="User e-mail" placeholder="contains…" value={actorInput} onChange={reset(setActorInput)} />
          <TextField label="Action" placeholder="e.g. LOGIN, DENIED, INVOICE" value={actionInput} onChange={reset(setActionInput)} />
          <SelectField label="Outcome" placeholder="All" value={outcome} onChange={reset(setOutcome)}
            options={[{ value: 'SUCCESS', label: 'Success' }, { value: 'DENIED', label: 'Denied' }, { value: 'FAILURE', label: 'Failure' }]} />
          <TextField label="From" type="date" value={from} max={to} onChange={reset(setFrom)} />
          <TextField label="To" type="date" value={to} min={from} onChange={reset(setTo)} />
        </div>
        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && rows.length === 0 && <EmptyState icon={ScrollText} title="No matching entries" />}
          {rows.length > 0 && (
            <div className="relative overflow-x-auto">
              <table className="w-full min-w-[52rem] text-left text-xs">
                <thead className="border-b border-slate-200 uppercase tracking-wide text-slate-500">
                  <tr>
                    {['Time', 'User', 'Action', 'Record', 'Outcome', 'Details', 'IP'].map((c) => <th key={c} scope="col" className="py-2 pr-3 font-semibold">{c}</th>)}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((a) => (
                    <tr key={a.id} className="align-top">
                      <td className="whitespace-nowrap py-2 pr-3 text-slate-600">{formatDateTime(a.createdAt)}</td>
                      <td className="py-2 pr-3"><p className="text-slate-900">{a.actorEmail || '—'}</p><p className="text-slate-500">{a.actorRole}</p></td>
                      <td className="py-2 pr-3 font-mono text-slate-800">{a.action}</td>
                      <td className="py-2 pr-3 text-slate-600">{a.entityType ? `${a.entityType} ${a.entityId ?? ''}` : '—'}</td>
                      <td className="py-2 pr-3"><StatusBadge tone={TONE[a.outcome] || 'slate'}>{a.outcome.toLowerCase()}</StatusBadge></td>
                      <td className="max-w-xs py-2 pr-3 text-slate-600">{a.details || '—'}</td>
                      <td className="py-2 font-mono text-slate-500">{a.ipAddress || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>
    </>
  );
}
