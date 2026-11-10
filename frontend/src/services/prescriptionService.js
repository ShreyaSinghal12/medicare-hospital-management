import apiClient from '../api/client.js';

export async function createPrescription(payload) {
  const { data } = await apiClient.post('/prescriptions', payload);
  return data;
}
export async function updatePrescription(id, payload) {
  const { data } = await apiClient.put(`/prescriptions/${id}`, payload);
  return data;
}
export async function discardPrescription(id) {
  await apiClient.delete(`/prescriptions/${id}`);
}
export async function finalizePrescription(id, version) {
  const { data } = await apiClient.post(`/prescriptions/${id}/finalize`, null, { params: { version } });
  return data;
}
export async function revisePrescription(id, reason) {
  const { data } = await apiClient.post(`/prescriptions/${id}/revisions`, { reason });
  return data;
}
export async function getPrescription(id) {
  const { data } = await apiClient.get(`/prescriptions/${id}`);
  return data;
}
export async function getPrescriptionVersions(id) {
  const { data } = await apiClient.get(`/prescriptions/${id}/versions`);
  return data;
}
export async function getConsultationPrescriptions(consultationId) {
  const { data } = await apiClient.get(`/consultations/${consultationId}/prescriptions`);
  return data;
}
function clean(params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null));
}
export async function getMyPrescriptions(params = {}) {
  const { data } = await apiClient.get('/patients/me/prescriptions', { params: clean({ size: 10, ...params }) });
  return data;
}
export async function getIssuedPrescriptions(params = {}) {
  const { data } = await apiClient.get('/doctor/prescriptions', { params: clean({ size: 10, ...params }) });
  return data;
}

/** Same-origin link; the browser sends the session cookie, the server checks access. */
export function prescriptionPdfUrl(id) {
  return `${apiClient.defaults.baseURL}/prescriptions/${id}/pdf`;
}
