import apiClient from '../api/client.js';

// ---- public scheduling
export async function getDoctorSchedule(doctorId) {
  const { data } = await apiClient.get(`/doctors/${doctorId}/schedule`);
  return data;
}
export async function getSlots(doctorId, date) {
  const { data } = await apiClient.get(`/doctors/${doctorId}/slots`, { params: { date } });
  return data;
}
/** Returns null when nothing is available within the booking window (HTTP 204). */
export async function getNextAvailable(doctorId, from) {
  const response = await apiClient.get(`/doctors/${doctorId}/next-available`, { params: from ? { from } : {} });
  return response.status === 204 ? null : response.data;
}

// ---- appointments
export async function bookAppointment(payload) {
  const { data } = await apiClient.post('/appointments', payload);
  return data;
}
export async function getMyAppointments({ scope = 'UPCOMING', page = 0, size = 10 } = {}) {
  const { data } = await apiClient.get('/appointments/me', { params: { scope, page, size } });
  return data;
}
export async function getMyAppointmentSummary() {
  const { data } = await apiClient.get('/appointments/me/summary');
  return data;
}
export async function getAppointment(id) {
  const { data } = await apiClient.get(`/appointments/${id}`);
  return data;
}
export async function getAppointmentHistory(id) {
  const { data } = await apiClient.get(`/appointments/${id}/history`);
  return data;
}
export async function cancelAppointment(id, reason) {
  const { data } = await apiClient.patch(`/appointments/${id}/cancel`, { reason });
  return data;
}
export async function rescheduleAppointment(id, payload) {
  const { data } = await apiClient.patch(`/appointments/${id}/reschedule`, payload);
  return data;
}
export async function markNoShow(id) {
  const { data } = await apiClient.patch(`/appointments/${id}/no-show`);
  return data;
}
