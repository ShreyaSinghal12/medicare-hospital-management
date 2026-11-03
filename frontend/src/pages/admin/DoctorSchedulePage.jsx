import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import LeaveForm from '../../components/scheduling/LeaveForm.jsx';
import WeeklyScheduleEditor from '../../components/scheduling/WeeklyScheduleEditor.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { adminGetAvailability, adminRecordLeave, adminUpdateAvailability } from '../../services/scheduleService.js';

/** Admin: manage one doctor's weekly hours and record (pre-approved) leave. */
export default function DoctorSchedulePage() {
  const { id } = useParams();
  const { data, loading, error, reload } = useApi(() => adminGetAvailability(id), [id]);

  return (
    <>
      <Link to="/admin/doctors" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to doctors
      </Link>
      <PageHeader title={`Schedule: ${data?.doctorName || ''}`} description="Changes apply immediately. Affected bookings are flagged for rescheduling." />
      {loading && !data && <Spinner />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && (
        <div className="space-y-6">
          <Card title="Weekly hours">
            <WeeklyScheduleEditor schedule={data} onSave={async (days) => { const r = await adminUpdateAvailability(id, days); reload(); return r; }} />
          </Card>
          <Card title="Record leave" subtitle="Leave recorded by an administrator is approved immediately.">
            <LeaveForm submit={(payload) => adminRecordLeave(id, payload)} submitLabel="Record leave" />
          </Card>
        </div>
      )}
    </>
  );
}
