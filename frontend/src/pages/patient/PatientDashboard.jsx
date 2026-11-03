import { Droplet, IdCard, UserRound } from 'lucide-react';
import RecentNotifications from '../../components/RecentNotifications.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import useApi from '../../hooks/useApi.js';
import { getMyProfile } from '../../services/patientService.js';

/**
 * Patient home. Phase 2 shows the real profile summary and notifications;
 * appointment, prescription and timeline widgets are added in Phases 4–6.
 */
export default function PatientDashboard() {
  const { user } = useAuth();
  const { data: profile, loading, error, reload } = useApi(getMyProfile);
  const firstName = user.fullName.split(' ')[0];

  return (
    <>
      <PageHeader title={`Welcome, ${firstName}`} description="Your health information in one place."
        actions={<Button to="/patient/doctors">Find a doctor</Button>} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-1">
          <Card title="Profile summary" actions={<Button to="/patient/profile" variant="secondary" size="sm">View profile</Button>}>
            {loading && <Spinner />}
            {error && <ErrorState error={error} onRetry={reload} />}
            {profile && (
              <ul className="space-y-3 text-sm">
                <li className="flex items-center gap-3">
                  <IdCard className="h-4 w-4 text-brand-600" aria-hidden="true" />
                  <span className="text-slate-500">Patient ID</span>
                  <span className="ml-auto font-semibold text-slate-900">{profile.patientCode}</span>
                </li>
                <li className="flex items-center gap-3">
                  <UserRound className="h-4 w-4 text-brand-600" aria-hidden="true" />
                  <span className="text-slate-500">Age</span>
                  <span className="ml-auto font-semibold text-slate-900">{profile.age ?? '—'}</span>
                </li>
                <li className="flex items-center gap-3">
                  <Droplet className="h-4 w-4 text-brand-600" aria-hidden="true" />
                  <span className="text-slate-500">Blood group</span>
                  <span className="ml-auto font-semibold text-slate-900">{profile.bloodGroup || '—'}</span>
                </li>
              </ul>
            )}
          </Card>
        </div>
        <div className="lg:col-span-2">
          <RecentNotifications />
        </div>
      </div>
    </>
  );
}
