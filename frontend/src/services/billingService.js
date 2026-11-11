import apiClient from '../api/client.js';

function clean(params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null));
}

export async function getBillableCharges(patientId) {
  const { data } = await apiClient.get(`/billing/patients/${patientId}/billable`);
  return data;
}
export async function createInvoice(payload) {
  const { data } = await apiClient.post('/billing/invoices', payload);
  return data;
}
export async function searchInvoices(params = {}) {
  const { data } = await apiClient.get('/billing/invoices', { params: clean({ size: 10, ...params }) });
  return data;
}
export async function getInvoice(id) {
  const { data } = await apiClient.get(`/billing/invoices/${id}`);
  return data;
}
export function invoicePdfUrl(id) {
  return `${apiClient.defaults.baseURL}/billing/invoices/${id}/pdf`;
}
export async function recordPayment(id, payload) {
  const { data } = await apiClient.post(`/billing/invoices/${id}/payments`, payload);
  return data;
}
export async function recordRefund(id, payload) {
  const { data } = await apiClient.post(`/billing/invoices/${id}/refunds`, payload);
  return data;
}
export async function cancelInvoice(id, reason) {
  const { data } = await apiClient.post(`/billing/invoices/${id}/cancel`, { reason });
  return data;
}
export async function getMyInvoices(params = {}) {
  const { data } = await apiClient.get('/patients/me/invoices', { params: clean({ size: 10, ...params }) });
  return data;
}
export async function getBillingSummary(params = {}) {
  const { data } = await apiClient.get('/admin/billing/summary', { params: clean(params) });
  return data;
}

export const PAYMENT_METHODS = {
  CASH: 'Cash',
  UPI_SIMULATED: 'UPI (reference only)',
  CARD_SIMULATED: 'Card (terminal reference only)',
  BANK_TRANSFER_SIMULATED: 'Bank transfer (reference only)',
};
export const ITEM_TYPES = { CONSULTATION: 'Consultation', LAB_TEST: 'Lab test', PHARMACY: 'Medicine', PROCEDURE: 'Procedure', OTHER: 'Other' };

/** Sum in paise to avoid floating-point drift; returns rupees. */
export function sumMoney(values) {
  return values.reduce((s, v) => s + Math.round(Number(v || 0) * 100), 0) / 100;
}
/** Same rule as the server: 13+ digits in a row (ignoring spaces/dashes) looks like a card number. */
export function looksLikeCardNumber(reference) {
  return /\d{13,}/.test(String(reference || '').replace(/[\s-]/g, ''));
}
