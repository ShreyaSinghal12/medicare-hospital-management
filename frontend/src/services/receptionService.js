import apiClient from '../api/client.js';

export async function getReceptionDashboard() {
  const { data } = await apiClient.get('/reception/dashboard');
  return data;
}
export async function registerWalkIn(payload) {
  const { data } = await apiClient.post('/reception/patients', payload);
  return data;
}
export async function searchReceptionPatients({ search, page = 0, size = 10 } = {}) {
  const params = { page, size };
  if (search) params.search = search;
  const { data } = await apiClient.get('/reception/patients', { params });
  return data;
}
export async function getReceptionPatient(id) {
  const { data } = await apiClient.get(`/reception/patients/${id}`);
  return data;
}
export async function updatePatientContact(id, payload) {
  const { data } = await apiClient.patch(`/reception/patients/${id}/contact`, payload);
  return data;
}
export async function getPatientAppointments(id) {
  const { data } = await apiClient.get(`/reception/patients/${id}/appointments`);
  return data;
}
export async function getDayAppointments({ date, doctorId, status } = {}) {
  const params = {};
  if (date) params.date = date;
  if (doctorId) params.doctorId = doctorId;
  if (status) params.status = status;
  const { data } = await apiClient.get('/reception/appointments', { params });
  return data;
}
export async function checkIn(appointmentId) {
  const { data } = await apiClient.patch(`/appointments/${appointmentId}/check-in`);
  return data;
}
export async function getQueue({ date, doctorId } = {}) {
  const params = {};
  if (date) params.date = date;
  if (doctorId) params.doctorId = doctorId;
  const { data } = await apiClient.get('/reception/queue', { params });
  return data;
}
export async function skipQueueEntry(id) {
  const { data } = await apiClient.patch(`/reception/queue/${id}/skip`);
  return data;
}
export async function requeueEntry(id) {
  const { data } = await apiClient.patch(`/reception/queue/${id}/requeue`);
  return data;
}
export async function getDoctorQueue() {
  const { data } = await apiClient.get('/doctor/queue');
  return data;
}
