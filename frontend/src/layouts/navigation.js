import {
  Bell, ClipboardList, FlaskConical, LayoutDashboard, Pill, Settings, Stethoscope, UserRound, Users,
} from 'lucide-react';

/**
 * Sidebar navigation per role. Only pages that are actually implemented are listed;
 * each later phase adds its own entries here.
 */
export const NAVIGATION = {
  PATIENT: [
    { to: '/patient', label: 'Dashboard', icon: LayoutDashboard, end: true },
    { to: '/patient/profile', label: 'My profile', icon: UserRound },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
  DOCTOR: [
    { to: '/doctor', label: 'Dashboard', icon: Stethoscope, end: true },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
  RECEPTIONIST: [
    { to: '/reception', label: 'Reception desk', icon: ClipboardList, end: true },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
  ADMIN: [
    { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
    { to: '/admin/users', label: 'Users & access', icon: Users },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
  LAB_TECHNICIAN: [
    { to: '/lab', label: 'Laboratory', icon: FlaskConical, end: true },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
  PHARMACIST: [
    { to: '/pharmacy', label: 'Pharmacy', icon: Pill, end: true },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/account', label: 'Account settings', icon: Settings },
  ],
};

