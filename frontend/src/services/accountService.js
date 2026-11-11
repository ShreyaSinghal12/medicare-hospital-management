import apiClient from '../api/client.js';

export async function updateAccount(payload) {
  const { data } = await apiClient.put('/account', payload);
  return data;
}

export async function changePassword(payload) {
  const { data } = await apiClient.put('/account/password', payload);
  return data;
}

export async function getNotificationPreferences() {
  const { data } = await apiClient.get('/account/notification-preferences');
  return data;
}

export async function updateNotificationPreferences(payload) {
  const { data } = await apiClient.put('/account/notification-preferences', payload);
  return data;
}
