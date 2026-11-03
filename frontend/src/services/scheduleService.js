import apiClient from '../api/client.js';

// ---- doctor (own)
export async function getMyAvailability() {
  const { data } = await apiClient.get('/doctor/availability');
  return data;
}
export async function updateMyAvailability(days) {
  const { data } = await apiClient.put('/doctor/availability', { days });
  return data;
}
export async function getMyLeave() {
  const { data } = await apiClient.get('/doctor/leave');
  return data;
}
export async function requestLeave(payload) {
  const { data } = await apiClient.post('/doctor/leave', payload);
  return data;
}
export async function cancelLeave(id) {
  const { data } = await apiClient.patch(`/doctor/leave/${id}/cancel`);
  return data;
}
export async function getDoctorAppointments({ from, to, status, page = 0, size = 200 } = {}) {
  const params = { page, size };
  if (from) params.from = from;
  if (to) params.to = to;
  if (status) params.status = status;
  const { data } = await apiClient.get('/doctor/appointments', { params });
  return data;
}
export async function getDoctorDashboard() {
  const { data } = await apiClient.get('/doctor/dashboard');
  return data;
}

// ---- admin
export async function adminGetAvailability(doctorId) {
  const { data } = await apiClient.get(`/admin/doctors/${doctorId}/availability`);
  return data;
}
export async function adminUpdateAvailability(doctorId, days) {
  const { data } = await apiClient.put(`/admin/doctors/${doctorId}/availability`, { days });
  return data;
}
export async function adminRecordLeave(doctorId, payload) {
  const { data } = await apiClient.post(`/admin/doctors/${doctorId}/leave`, payload);
  return data;
}
export async function adminListLeave({ status, page = 0, size = 20 } = {}) {
  const params = { page, size };
  if (status) params.status = status;
  const { data } = await apiClient.get('/admin/leave', { params });
  return data;
}
export async function adminDecideLeave(id, approve) {
  const { data } = await apiClient.patch(`/admin/leave/${id}/decision`, { approve });
  return data;
}
