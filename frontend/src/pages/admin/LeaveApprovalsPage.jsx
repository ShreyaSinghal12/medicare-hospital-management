import { useState } from 'react';
import { CalendarOff } from 'lucide-react';
import { toast } from 'sonner';
import LeaveList from '../../components/scheduling/LeaveList.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import Tabs from '../../components/ui/Tabs.jsx';
import useApi from '../../hooks/useApi.js';
import { adminDecideLeave, adminListLeave } from '../../services/scheduleService.js';

/** Admin: approve or reject doctors' leave requests. Approval flags affected bookings. */
export default function LeaveApprovalsPage() {
  const [status, setStatus] = useState('PENDING');
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState(null);
  const { data, loading, error, reload } = useApi(() => adminListLeave({ status: status || undefined, page }), [status, page]);
  const rows = data?.content || [];

  async function decide(l, approve) {
    setBusyId(l.id);
    try {
      const res = await adminDecideLeave(l.id, approve);
      toast.success(approve
        ? `Approved${res.affectedAppointments ? `; ${res.affectedAppointments} appointment(s) flagged and patients notified` : ''}`
        : 'Rejected');
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <PageHeader title="Leave requests" description="Approved leave blocks booking; existing appointments are flagged for rescheduling, never cancelled." />
      <Card>
        <Tabs label="Leave status" value={status} onChange={(v) => { setStatus(v); setPage(0); }} tabs={[
          { value: 'PENDING', label: 'Pending' }, { value: 'APPROVED', label: 'Approved' },
          { value: 'REJECTED', label: 'Rejected' }, { value: '', label: 'All' },
        ]} />
        {loading && !data && <Spinner />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {!loading && !error && rows.length === 0 && <EmptyState icon={CalendarOff} title="Nothing here" />}
        {rows.length > 0 && (
          <LeaveList items={rows} showDoctor actions={(l) => l.status === 'PENDING' && (
            <>
              <Button size="sm" onClick={() => decide(l, true)} loading={busyId === l.id}>Approve</Button>
              <Button size="sm" variant="secondary" onClick={() => decide(l, false)} disabled={busyId === l.id}>Reject</Button>
            </>
          )} />
        )}
        <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
      </Card>
    </>
  );
}
