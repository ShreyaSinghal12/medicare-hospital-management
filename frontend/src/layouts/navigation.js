import {
  Banknote, Bell, BellRing, Boxes, Receipt, ReceiptText, Building2, ClipboardCheck, FlaskConical, History, PackageSearch, TriangleAlert, CalendarCheck, CalendarPlus, ListOrdered, UserPlus, CalendarClock, CalendarOff, ClipboardList, IdCard, LayoutDashboard,
  NotebookText, Pill, Search, Settings, Stethoscope, UserCog, UserRound, Users,
} from 'lucide-react';

/**
 * Sidebar navigation per role. Only pages that are actually implemented are listed;
 * each later phase adds its own entries here.
 */
export const NAVIGATION = {
  PATIENT: [
    { to: '/patient', label: 'Dashboard', icon: LayoutDashboard, end: true },
    { to: '/patient/appointments', label: 'My appointments', icon: CalendarCheck },
    { to: '/patient/history', label: 'Medical history', icon: NotebookText },
    { to: '/patient/prescriptions', label: 'Prescriptions', icon: Pill },
    { to: '/patient/lab-reports', label: 'Lab reports', icon: FlaskConical },
    { to: '/patient/invoices', label: 'Invoices', icon: Receipt },
    { to: '/patient/doctors', label: 'Find a doctor', icon: Search },
    { to: '/patient/profile', label: 'My profile', icon: UserRound },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
  DOCTOR: [
    { to: '/doctor', label: 'Dashboard', icon: Stethoscope, end: true },
    { to: '/doctor/appointments', label: 'Appointments', icon: CalendarCheck },
    { to: '/doctor/patients', label: 'My patients', icon: Users },
    { to: '/doctor/prescriptions', label: 'Prescriptions', icon: Pill },
    { to: '/doctor/lab-orders', label: 'Lab orders', icon: FlaskConical },
    { to: '/doctor/availability', label: 'Availability', icon: CalendarClock },
    { to: '/doctor/profile', label: 'My profile', icon: IdCard },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
  RECEPTIONIST: [
    { to: '/reception', label: 'Reception desk', icon: ClipboardList, end: true },
    { to: '/reception/appointments', label: 'Appointments', icon: CalendarCheck },
    { to: '/reception/queue', label: 'Queue', icon: ListOrdered },
    { to: '/reception/book', label: 'Book appointment', icon: CalendarPlus },
    { to: '/reception/patients', label: 'Patients', icon: Users, end: true },
    { to: '/reception/patients/new', label: 'Register walk-in', icon: UserPlus },
    { to: '/reception/billing', label: 'Invoices', icon: Receipt, end: true },
    { to: '/reception/billing/new', label: 'New invoice', icon: ReceiptText },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
  ADMIN: [
    { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
    { to: '/admin/doctors', label: 'Doctors', icon: Stethoscope },
    { to: '/admin/leave', label: 'Leave requests', icon: CalendarOff },
    { to: '/admin/departments', label: 'Departments', icon: Building2 },
    { to: '/admin/lab-tests', label: 'Lab tests', icon: FlaskConical },
    { to: '/admin/billing', label: 'Billing', icon: Banknote },
    { to: '/admin/invoices', label: 'Invoices', icon: Receipt },
    { to: '/admin/inventory', label: 'Medicines', icon: Boxes, end: true },
    { to: '/admin/inventory/alerts', label: 'Stock alerts', icon: TriangleAlert },
    { to: '/admin/inventory/ledger', label: 'Stock history', icon: History },
    { to: '/admin/dispensing/new', label: 'Dispense', icon: PackageSearch },
    { to: '/admin/dispensing', label: 'Dispensing records', icon: ClipboardCheck, end: true },
    { to: '/admin/staff', label: 'Staff accounts', icon: UserCog },
    { to: '/admin/patients', label: 'Patients', icon: UserRound },
    { to: '/admin/users', label: 'Users & access', icon: Users },
    { to: '/admin/reminders', label: 'Reminders', icon: BellRing },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
};

