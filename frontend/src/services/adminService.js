import apiClient from '../api/client.js';
import { cleanParams } from './doctorService.js';

// ---- Doctors
export async function adminSearchDoctors(params = {}) {
  const { data } = await apiClient.get('/admin/doctors', { params: cleanParams({ size: 20, ...params }) });
  return data;
}
export async function adminGetDoctor(id) {
  const { data } = await apiClient.get(`/admin/doctors/${id}`);
  return data;
}
export async function adminCreateDoctor(payload) {
  const { data } = await apiClient.post('/admin/doctors', payload);
  return data;
}
export async function adminUpdateDoctor(id, payload) {
  const { data } = await apiClient.put(`/admin/doctors/${id}`, payload);
  return data;
}

// ---- Departments
export async function adminListDepartments() {
  const { data } = await apiClient.get('/admin/departments');
  return data;
}
export async function adminCreateDepartment(payload) {
  const { data } = await apiClient.post('/admin/departments', payload);
  return data;
}
export async function adminUpdateDepartment(id, payload) {
  const { data } = await apiClient.put(`/admin/departments/${id}`, payload);
  return data;
}
export async function adminSetDepartmentActive(id, active) {
  const { data } = await apiClient.patch(`/admin/departments/${id}/status`, { active });
  return data;
}

// ---- Staff
export async function adminSearchStaff(params = {}) {
  const { data } = await apiClient.get('/admin/staff', { params: cleanParams({ size: 20, ...params }) });
  return data;
}
export async function adminCreateStaff(payload) {
  const { data } = await apiClient.post('/admin/staff', payload);
  return data;
}

// ---- Patients
export async function adminSearchPatients(params = {}) {
  const { data } = await apiClient.get('/admin/patients', { params: cleanParams({ size: 20, ...params }) });
  return data;
}
export async function adminFindDuplicatePatients() {
  const { data } = await apiClient.get('/admin/patients/duplicates');
  return data;
}
export async function adminCorrectPatient(id, payload) {
  const { data } = await apiClient.put(`/admin/patients/${id}`, payload);
  return data;
}
