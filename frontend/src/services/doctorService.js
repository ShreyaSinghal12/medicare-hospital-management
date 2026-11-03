import apiClient from '../api/client.js';

function clean(params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null));
}

/** Public doctor directory (no login required). */
export async function searchDoctors({ search, departmentId, specialization, page = 0, size = 12 } = {}) {
  const { data } = await apiClient.get('/doctors', { params: clean({ search, departmentId, specialization, page, size }) });
  return data;
}

export async function getDoctor(id) {
  const { data } = await apiClient.get(`/doctors/${id}`);
  return data;
}

export async function getSpecializations() {
  const { data } = await apiClient.get('/doctors/specializations');
  return data;
}

export async function getPublicDepartments() {
  const { data } = await apiClient.get('/public/departments');
  return data;
}

/** The logged-in doctor's own profile. */
export async function getOwnDoctorProfile() {
  const { data } = await apiClient.get('/doctor/profile');
  return data;
}

export async function updateOwnDoctorProfile(payload) {
  const { data } = await apiClient.put('/doctor/profile', payload);
  return data;
}

export { clean as cleanParams };
