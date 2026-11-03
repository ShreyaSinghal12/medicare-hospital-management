import apiClient from '../api/client.js';

// ---- consultations
export async function startConsultation(appointmentId) {
  const { data } = await apiClient.post('/consultations', { appointmentId });
  return data;
}
export async function getConsultation(id) {
  const { data } = await apiClient.get(`/consultations/${id}`);
  return data;
}
export async function getConsultationByAppointment(appointmentId) {
  const { data } = await apiClient.get(`/consultations/by-appointment/${appointmentId}`);
  return data;
}
export async function saveConsultationDraft(id, content) {
  const { data } = await apiClient.patch(`/consultations/${id}`, content);
  return data;
}
export async function finalizeConsultation(id, content) {
  const { data } = await apiClient.post(`/consultations/${id}/finalize`, content);
  return data;
}
export async function amendConsultation(id, content, reason) {
  const { data } = await apiClient.post(`/consultations/${id}/amendments`, { content, reason });
  return data;
}
export async function getAmendments(id) {
  const { data } = await apiClient.get(`/consultations/${id}/amendments`);
  return data;
}

// ---- doctor: patient records
export async function getMyPatients({ search, page = 0, size = 20 } = {}) {
  const params = { page, size };
  if (search) params.search = search;
  const { data } = await apiClient.get('/doctor/patients', { params });
  return data;
}
export async function getPatientRecord(patientId) {
  const { data } = await apiClient.get(`/doctor/patients/${patientId}/record`);
  return data;
}
export async function addAllergy(patientId, payload) {
  const { data } = await apiClient.post(`/doctor/patients/${patientId}/allergies`, payload);
  return data;
}
export async function setAllergyActive(patientId, allergyId, active) {
  const { data } = await apiClient.patch(`/doctor/patients/${patientId}/allergies/${allergyId}`, { active });
  return data;
}
export async function addCondition(patientId, payload) {
  const { data } = await apiClient.post(`/doctor/patients/${patientId}/conditions`, payload);
  return data;
}
export async function setConditionStatus(patientId, conditionId, status) {
  const { data } = await apiClient.patch(`/doctor/patients/${patientId}/conditions/${conditionId}`, { status });
  return data;
}

// ---- patient
export async function getMyMedicalHistory() {
  const { data } = await apiClient.get('/patients/me/medical-history');
  return data;
}
