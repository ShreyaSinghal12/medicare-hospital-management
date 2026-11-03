import apiClient from '../api/client.js';

export async function updateAccount(payload) {
  const { data } = await apiClient.put('/account', payload);
  return data;
}

export async function changePassword(payload) {
  const { data } = await apiClient.put('/account/password', payload);
  return data;
}
