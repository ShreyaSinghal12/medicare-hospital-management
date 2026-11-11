import apiClient from '../api/client.js';

function clean(params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null && v !== false));
}

export async function listMedicines(params = {}) {
  const { data } = await apiClient.get('/admin/inventory/medicines', { params: clean({ size: 20, ...params }) });
  return data;
}
export async function getMedicine(id) {
  const { data } = await apiClient.get(`/admin/inventory/medicines/${id}`);
  return data;
}
export async function createMedicine(payload) {
  const { data } = await apiClient.post('/admin/inventory/medicines', payload);
  return data;
}
export async function updateMedicine(id, payload) {
  const { data } = await apiClient.put(`/admin/inventory/medicines/${id}`, payload);
  return data;
}
export async function setMedicineActive(id, active) {
  const { data } = await apiClient.patch(`/admin/inventory/medicines/${id}/status`, { active });
  return data;
}
export async function receiveStock(medicineId, payload) {
  const { data } = await apiClient.post(`/admin/inventory/medicines/${medicineId}/batches`, payload);
  return data;
}
export async function adjustBatch(batchId, payload) {
  const { data } = await apiClient.post(`/admin/inventory/batches/${batchId}/adjustments`, payload);
  return data;
}
export async function writeOffBatch(batchId) {
  const { data } = await apiClient.post(`/admin/inventory/batches/${batchId}/write-off`);
  return data;
}
export async function listTransactions(params = {}) {
  const { data } = await apiClient.get('/admin/inventory/transactions', { params: clean({ size: 20, ...params }) });
  return data;
}
export async function getInventoryAlerts() {
  const { data } = await apiClient.get('/admin/inventory/alerts');
  return data;
}
export async function lookupPrescriptionForDispensing(prescriptionNo) {
  const { data } = await apiClient.get(`/admin/dispensing/prescriptions/${encodeURIComponent(prescriptionNo)}`);
  return data;
}
export async function dispense(payload) {
  const { data } = await apiClient.post('/admin/dispensing', payload);
  return data;
}
export async function listDispensings(params = {}) {
  const { data } = await apiClient.get('/admin/dispensing', { params: clean({ size: 10, ...params }) });
  return data;
}
export async function getDispensing(id) {
  const { data } = await apiClient.get(`/admin/dispensing/${id}`);
  return data;
}

export const MEDICINE_CATEGORIES = ['TABLET', 'CAPSULE', 'SYRUP', 'INJECTION', 'OINTMENT', 'DROPS', 'INHALER', 'OTHER'];
export const TXN_LABELS = {
  STOCK_IN: 'Stock in',
  STOCK_OUT: 'Returned to supplier',
  DISPENSE: 'Dispensed',
  ADJUSTMENT_IN: 'Adjustment +',
  ADJUSTMENT_OUT: 'Adjustment −',
  EXPIRED_WRITE_OFF: 'Expired write-off',
};
export function titleCase(s) {
  return s ? s.charAt(0) + s.slice(1).toLowerCase().replaceAll('_', ' ') : '';
}
