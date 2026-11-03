import { BadgeCheck, Building2, Clock, Mail, Phone } from 'lucide-react';
import RecentNotifications from '../../components/RecentNotifications.jsx';
import Card from '../../components/ui/Card.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { formatDateTime } from '../../utils/format.js';
import { ROLE_LABEL } from '../../utils/roles.js';

const ROLE_INTRO = {
  DOCTOR: 'Your consultations, schedule and patient records will appear here.',
  RECEPTIONIST: 'Front-desk tools for registration, scheduling, check-in and billing.',
  LAB_TECHNICIAN: 'Laboratory test orders and report uploads.',
  PHARMACIST: 'Medicine inventory and prescription dispensing.',
  ADMIN: 'Hospital administration, users, configuration and analytics.',
};

/**
 * Home page for staff roles. Phase 2 shows the real account details and notifications;
 * each module phase replaces the intro with that role's operational dashboard.
 */
export default function StaffHomePage({ title }) {
  const { user } = useAuth();
  const rows = [
    { icon: BadgeCheck, label: 'Role', value: ROLE_LABEL[user.role] },
    user.departmentName && { icon: Building2, label: 'Department', value: user.departmentName },
    user.employeeCode && { icon: BadgeCheck, label: 'Employee code', value: user.employeeCode },
    { icon: Mail, label: 'E-mail', value: user.email },
    { icon: Phone, label: 'Phone', value: user.phone || '—' },
    { icon: Clock, label: 'Last login', value: formatDateTime(user.lastLoginAt) },
  ].filter(Boolean);

  return (
    <>
      <PageHeader title={title} description={`Signed in as ${user.fullName}. ${ROLE_INTRO[user.role] || ''}`} />
      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="Your account">
          <ul className="space-y-3 text-sm">
            {rows.map(({ icon: Icon, label, value }) => (
              <li key={label} className="flex items-start gap-3">
                <Icon className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" aria-hidden="true" />
                <span className="text-slate-500">{label}</span>
                <span className="ml-auto break-all text-right font-medium text-slate-900">{value}</span>
              </li>
            ))}
          </ul>
        </Card>
        <div className="lg:col-span-2">
          <RecentNotifications />
        </div>
      </div>
    </>
  );
}
