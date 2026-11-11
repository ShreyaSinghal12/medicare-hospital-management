import apiClient from '../api/client.js';

function clean(params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null));
}

export async function getAdminDashboard(params = {}) {
  const { data } = await apiClient.get('/admin/dashboard', { params: clean(params) });
  return data;
}
export async function getHospitalSettings() {
  const { data } = await apiClient.get('/admin/settings');
  return data;
}
export async function updateHospitalSettings(payload) {
  const { data } = await apiClient.put('/admin/settings', payload);
  return data;
}
export async function previewReport(type, params = {}) {
  const { data } = await apiClient.get(`/admin/reports/${type}`, { params: clean(params) });
  return data;
}
export function reportExportUrl(type, format, params = {}) {
  const q = new URLSearchParams(clean({ format, ...params })).toString();
  return `${apiClient.defaults.baseURL}/admin/reports/${type}/export?${q}`;
}
export async function searchAuditLog(params = {}) {
  const { data } = await apiClient.get('/admin/audit', { params: clean({ size: 25, ...params }) });
  return data;
}

export const REPORT_TYPES = {
  APPOINTMENTS: 'Appointments',
  PAYMENTS: 'Payments and refunds',
  INVOICES: 'Invoices',
  LAB_WORKLOAD: 'Laboratory workload',
  INVENTORY: 'Medicine stock (current, dates ignored)',
  AUDIT: 'Audit log',
};
