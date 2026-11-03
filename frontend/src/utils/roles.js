/** Landing page of each role after login. */
export const ROLE_HOME = {
  PATIENT: '/patient',
  DOCTOR: '/doctor',
  RECEPTIONIST: '/reception',
  ADMIN: '/admin',
  LAB_TECHNICIAN: '/lab',
  PHARMACIST: '/pharmacy',
};

export const ROLE_LABEL = {
  PATIENT: 'Patient',
  DOCTOR: 'Doctor',
  RECEPTIONIST: 'Receptionist',
  ADMIN: 'Administrator',
  LAB_TECHNICIAN: 'Lab technician',
  PHARMACIST: 'Pharmacist',
};

export const ALL_ROLES = Object.keys(ROLE_HOME);

export function homeFor(user) {
  return (user && ROLE_HOME[user.role]) || '/';
}

/** Only allow same-site relative redirects (prevents open redirects via ?redirect=). */
export function safeRedirect(path) {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') ? path : null;
}
