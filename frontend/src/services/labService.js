import apiClient from '../api/client.js';

function clean(params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null));
}

// ---- catalog
export async function getLabCatalog(search) {
  const { data } = await apiClient.get('/lab-tests', { params: clean({ search }) });
  return data;
}
export async function adminListLabTests(search) {
  const { data } = await apiClient.get('/admin/lab-tests', { params: clean({ search }) });
  return data;
}
export async function adminCreateLabTest(payload) {
  const { data } = await apiClient.post('/admin/lab-tests', payload);
  return data;
}
export async function adminUpdateLabTest(id, payload) {
  const { data } = await apiClient.put(`/admin/lab-tests/${id}`, payload);
  return data;
}
export async function adminSetLabTestActive(id, active) {
  const { data } = await apiClient.patch(`/admin/lab-tests/${id}/status`, { active });
  return data;
}

// ---- orders
export async function createLabOrder(payload) {
  const { data } = await apiClient.post('/lab-orders', payload);
  return data;
}
export async function getLabOrder(id) {
  const { data } = await apiClient.get(`/lab-orders/${id}`);
  return data;
}
export async function changeLabOrderStatus(id, { status, version, reason }) {
  const { data } = await apiClient.patch(`/lab-orders/${id}/status`, { status, version, reason });
  return data;
}
export async function saveLabResults(id, version, items) {
  const { data } = await apiClient.put(`/lab-orders/${id}/results`, { version, items });
  return data;
}
export async function uploadLabReport(id, version, file) {
  const form = new FormData();
  form.append('file', file);
  const { data } = await apiClient.post(`/lab-orders/${id}/report`, form, { params: { version }, timeout: 60000 });
  return data;
}
export async function removeLabReport(id, version) {
  const { data } = await apiClient.delete(`/lab-orders/${id}/report`, { params: { version } });
  return data;
}
export async function finalizeLabReport(id, version) {
  const { data } = await apiClient.post(`/lab-orders/${id}/finalize`, null, { params: { version } });
  return data;
}
export function labReportUrl(id) {
  return `${apiClient.defaults.baseURL}/lab-orders/${id}/report`;
}
export async function getConsultationLabOrders(consultationId) {
  const { data } = await apiClient.get(`/consultations/${consultationId}/lab-orders`);
  return data;
}
export async function getDoctorLabOrders(params = {}) {
  const { data } = await apiClient.get('/doctor/lab-orders', { params: clean({ size: 10, ...params }) });
  return data;
}
export async function getPatientLabOrders(patientId, params = {}) {
  const { data } = await apiClient.get(`/doctor/patients/${patientId}/lab-orders`, { params: clean({ size: 10, ...params }) });
  return data;
}
export async function getMyLabOrders(params = {}) {
  const { data } = await apiClient.get('/patients/me/lab-orders', { params: clean({ size: 10, ...params }) });
  return data;
}
