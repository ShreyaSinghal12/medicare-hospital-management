import { toast } from 'sonner';
import LeaveForm from '../../components/scheduling/LeaveForm.jsx';
import LeaveList from '../../components/scheduling/LeaveList.jsx';
import WeeklyScheduleEditor from '../../components/scheduling/WeeklyScheduleEditor.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import {
  cancelLeave, getMyAvailability, getMyLeave, requestLeave, updateMyAvailability,
} from '../../services/scheduleService.js';
import { todayIsoLocal } from '../../utils/dates.js';

/** Doctor: weekly hours (applied immediately) and leave requests (need admin approval). */
export default function AvailabilityPage() {
  const schedule = useApi(getMyAvailability);
  const leave = useApi(getMyLeave);
  const today = todayIsoLocal();

  async function withdraw(id) {
    try {
      await cancelLeave(id);
      toast.success('Request withdrawn');
      leave.reload();
    } catch (err) {
      toast.error(err.message);
    }
  }

  return (
    <>
      <PageHeader title="Availability" description="Your weekly consultation hours, leave and special unavailability." />
      <div className="space-y-6">
        <Card title="Weekly hours">
          {schedule.loading && !schedule.data && <Spinner />}
          {schedule.error && <ErrorState error={schedule.error} onRetry={schedule.reload} />}
          {schedule.data && (
            <WeeklyScheduleEditor schedule={schedule.data}
              onSave={async (days) => { const r = await updateMyAvailability(days); schedule.reload(); return r; }} />
          )}
        </Card>
        <Card title="Request leave or unavailability" subtitle="Requests take effect once an administrator approves them.">
          <LeaveForm submit={requestLeave} onDone={() => leave.reload()} />
        </Card>
        <Card title="My requests">
          {leave.loading && !leave.data && <Spinner />}
          {leave.error && <ErrorState error={leave.error} onRetry={leave.reload} />}
          {leave.data && leave.data.length === 0 && <EmptyState title="No leave requests yet" />}
          {leave.data && leave.data.length > 0 && (
            <LeaveList items={leave.data} actions={(l) => (l.status === 'PENDING' || l.status === 'APPROVED') && l.endDate >= today && (
              <Button size="sm" variant="ghost" onClick={() => withdraw(l.id)}>Withdraw</Button>
            )} />
          )}
        </Card>
      </div>
    </>
  );
}
