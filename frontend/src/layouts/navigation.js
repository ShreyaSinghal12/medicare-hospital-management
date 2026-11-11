import {
  Banknote, Bell, BellRing, Boxes, Building2, CalendarCheck, CalendarClock, CalendarOff, CalendarPlus, ClipboardCheck,
  ClipboardList, FileSpreadsheet, FlaskConical, History, IdCard, LayoutDashboard, ListOrdered, NotebookText, PackageSearch,
  Pill, Receipt, ReceiptText, ScrollText, Search, Settings, SlidersHorizontal, Stethoscope, TriangleAlert, UserCog, UserPlus,
  UserRound, Users,
} from 'lucide-react';

const ACCOUNT = {
  title: 'Account',
  items: [
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
};

/**
 * Sidebar navigation per role, in sections. A section without a title is always shown; titled sections can be
 * collapsed (the section holding the current page always stays open).
 */
export const NAVIGATION = {
  PATIENT: [
    {
      items: [
        { to: '/patient', label: 'Dashboard', icon: LayoutDashboard, end: true },
        { to: '/patient/appointments', label: 'My appointments', icon: CalendarCheck },
        { to: '/patient/doctors', label: 'Find a doctor', icon: Search },
      ],
    },
    {
      title: 'My health',
      items: [
        { to: '/patient/history', label: 'Medical history', icon: NotebookText },
        { to: '/patient/prescriptions', label: 'Prescriptions', icon: Pill },
        { to: '/patient/lab-reports', label: 'Lab reports', icon: FlaskConical },
        { to: '/patient/invoices', label: 'Invoices', icon: Receipt },
        { to: '/patient/profile', label: 'My profile', icon: UserRound },
      ],
    },
    ACCOUNT,
  ],
  DOCTOR: [
    {
      items: [
        { to: '/doctor', label: 'Dashboard', icon: Stethoscope, end: true },
        { to: '/doctor/appointments', label: 'Appointments', icon: CalendarCheck },
        { to: '/doctor/patients', label: 'My patients', icon: Users },
      ],
    },
    {
      title: 'Clinical',
      items: [
        { to: '/doctor/prescriptions', label: 'Prescriptions', icon: Pill },
        { to: '/doctor/lab-orders', label: 'Lab orders', icon: FlaskConical },
      ],
    },
    {
      title: 'My practice',
      items: [
        { to: '/doctor/availability', label: 'Availability', icon: CalendarClock },
        { to: '/doctor/profile', label: 'My profile', icon: IdCard },
      ],
    },
    ACCOUNT,
  ],
  RECEPTIONIST: [
    {
      items: [
        { to: '/reception', label: 'Reception desk', icon: ClipboardList, end: true },
        { to: '/reception/appointments', label: 'Appointments', icon: CalendarCheck },
        { to: '/reception/queue', label: 'Queue', icon: ListOrdered },
        { to: '/reception/book', label: 'Book appointment', icon: CalendarPlus },
      ],
    },
    {
      title: 'Patients',
      items: [
        { to: '/reception/patients', label: 'Patients', icon: Users, end: true },
        { to: '/reception/patients/new', label: 'Register walk-in', icon: UserPlus },
      ],
    },
    {
      title: 'Billing',
      items: [
        { to: '/reception/billing', label: 'Invoices', icon: Receipt, end: true },
        { to: '/reception/billing/new', label: 'New invoice', icon: ReceiptText },
      ],
    },
    ACCOUNT,
  ],
  ADMIN: [
    {
      items: [
        { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
        { to: '/admin/reports', label: 'Reports', icon: FileSpreadsheet },
      ],
    },
    {
      title: 'Clinical services',
      items: [
        { to: '/admin/doctors', label: 'Doctors', icon: Stethoscope },
        { to: '/admin/leave', label: 'Leave requests', icon: CalendarOff },
        { to: '/admin/departments', label: 'Departments', icon: Building2 },
        { to: '/admin/lab-tests', label: 'Lab tests', icon: FlaskConical },
      ],
    },
    {
      title: 'Medicines',
      items: [
        { to: '/admin/inventory', label: 'Medicines', icon: Boxes, end: true },
        { to: '/admin/inventory/alerts', label: 'Stock alerts', icon: TriangleAlert },
        { to: '/admin/inventory/ledger', label: 'Stock history', icon: History },
        { to: '/admin/dispensing/new', label: 'Dispense', icon: PackageSearch },
        { to: '/admin/dispensing', label: 'Dispensing records', icon: ClipboardCheck, end: true },
      ],
    },
    {
      title: 'Billing',
      items: [
        { to: '/admin/billing', label: 'Billing summary', icon: Banknote },
        { to: '/admin/invoices', label: 'Invoices', icon: Receipt },
      ],
    },
    {
      title: 'People',
      items: [
        { to: '/admin/staff', label: 'Staff accounts', icon: UserCog },
        { to: '/admin/patients', label: 'Patients', icon: UserRound },
        { to: '/admin/users', label: 'Users & access', icon: Users },
      ],
    },
    {
      title: 'System',
      items: [
        { to: '/admin/reminders', label: 'Reminders', icon: BellRing },
        { to: '/admin/audit', label: 'Audit log', icon: ScrollText },
        { to: '/admin/settings', label: 'Hospital settings', icon: SlidersHorizontal },
      ],
    },
    ACCOUNT,
  ],
};

/** All links of a role, flattened (for tests and quick lookups). */
export function navigationItems(role) {
  return (NAVIGATION[role] || []).flatMap((s) => s.items);
}
