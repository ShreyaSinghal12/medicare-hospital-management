import { useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { toast } from 'sonner';
import QueueStatusBadge, { TokenChip } from '../../components/reception/QueueStatusBadge.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getQueue, requeueEntry, skipQueueEntry } from '../../services/receptionService.js';
import { hhmm } from '../../utils/dates.js';
import { formatDateTime } from '../../utils/format.js';

/** Live queue board per doctor (refreshes every 20 s). */
export default function QueuePage() {
  const { data, loading, error, reload } = useApi(getQueue);
  const [busy, setBusy] = useState(null);
  useEffect(() => {
    const id = setInterval(reload, 20000);
    return () => clearInterval(id);
  }, [reload]);

  const groups = new Map();
  (data || []).forEach((q) => {
    if (!groups.has(q.doctorId)) groups.set(q.doctorId, { name: q.doctorName, entries: [] });
    groups.get(q.doctorId).entries.push(q);
  });

  async function act(fn, q, msg) {
    setBusy(q.id);
    try {
      await fn(q.id);
      toast.success(msg);
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <PageHeader title="Queue" description="Today's checked-in patients by doctor. Status follows the consultation automatically."
        actions={<Button variant="secondary" onClick={reload} loading={loading}><RefreshCw className="h-4 w-4" aria-hidden="true" /> Refresh</Button>} />
      {loading && !data && <Spinner />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && data.length === 0 && <Card><EmptyState title="Nobody checked in yet" message="Check patients in from the Appointments page." /></Card>}
      <div className="grid gap-6 lg:grid-cols-2">
        {[...groups.entries()].map(([doctorId, g]) => {
          const now = g.entries.find((q) => q.status === 'IN_CONSULTATION');
          const waiting = g.entries.filter((q) => q.status === 'WAITING').length;
          return (
            <Card key={doctorId} title={g.name} subtitle={`${waiting} waiting${now ? ` · now seeing token ${now.tokenNumber}` : ''}`}>
              <ul className="divide-y divide-slate-100">
                {g.entries.map((q) => (
                  <li key={q.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                    <div className="flex items-center gap-3">
                      <TokenChip token={q.tokenNumber} status={q.status} />
                      <div>
                        <p className="text-sm font-medium text-slate-900">{q.patientName}</p>
                        <p className="text-xs text-slate-500">{hhmm(q.appointmentTime)} slot · in at {formatDateTime(q.checkedInAt).split(', ').pop()}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <QueueStatusBadge status={q.status} />
                      {q.status === 'WAITING' && <Button size="sm" variant="ghost" loading={busy === q.id} onClick={() => act(skipQueueEntry, q, 'Skipped')}>Skip</Button>}
                      {q.status === 'SKIPPED' && q.appointmentStatus === 'CHECKED_IN' && (
                        <Button size="sm" variant="secondary" loading={busy === q.id} onClick={() => act(requeueEntry, q, 'Back in the queue')}>Put back</Button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </Card>
          );
        })}
      </div>
    </>
  );
}
