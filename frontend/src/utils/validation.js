/** Client-side rules mirroring the backend (the backend remains the source of truth). */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const PHONE_PATTERN = /^\+?[0-9 ()-]{7,20}$/;
export const NAME_PATTERN = /^[\p{L} .'-]{2,120}$/u;

export function passwordChecks(value = '') {
  return [
    { label: '8–64 characters', ok: value.length >= 8 && value.length <= 64 },
    { label: 'Upper-case letter', ok: /[A-Z]/.test(value) },
    { label: 'Lower-case letter', ok: /[a-z]/.test(value) },
    { label: 'Number', ok: /\d/.test(value) },
    { label: 'Special character', ok: /[^A-Za-z0-9\s]/.test(value) },
    { label: 'No spaces', ok: value.length > 0 && !/\s/.test(value) },
  ];
}

export function isStrongPassword(value) {
  return passwordChecks(value).every((c) => c.ok) || 'Password does not meet the requirements';
}

export const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

export const GENDERS = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'OTHER', label: 'Other' },
  { value: 'PREFER_NOT_TO_SAY', label: 'Prefer not to say' },
];

export function todayIso() {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
