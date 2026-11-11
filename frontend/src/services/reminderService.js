import apiClient from '../api/client.js';

export async function getReminderSettings() {
  const { data } = await apiClient.get('/admin/settings/reminders');
  return data;
}
export async function updateReminderSettings(payload) {
  const { data } = await apiClient.put('/admin/settings/reminders', payload);
  return data;
}
export async function runRemindersNow() {
  const { data } = await apiClient.post('/admin/reminders/run');
  return data;
}
export async function getReminderLog(params = {}) {
  const { data } = await apiClient.get('/admin/reminders', { params: { size: 20, ...params } });
  return data;
}
