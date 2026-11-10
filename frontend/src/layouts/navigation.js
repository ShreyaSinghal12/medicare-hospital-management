import {
  Bell, Building2, CalendarCheck, CalendarPlus, ListOrdered, UserPlus, CalendarClock, CalendarOff, ClipboardList, IdCard, LayoutDashboard,
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
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
  ADMIN: [
    { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
    { to: '/admin/doctors', label: 'Doctors', icon: Stethoscope },
    { to: '/admin/leave', label: 'Leave requests', icon: CalendarOff },
    { to: '/admin/departments', label: 'Departments', icon: Building2 },
    { to: '/admin/staff', label: 'Staff accounts', icon: UserCog },
    { to: '/admin/patients', label: 'Patients', icon: UserRound },
    { to: '/admin/users', label: 'Users & access', icon: Users },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
};

