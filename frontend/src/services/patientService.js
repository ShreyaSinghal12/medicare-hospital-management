import apiClient from '../api/client.js';

export async function getMyProfile() {
  const { data } = await apiClient.get('/patients/me');
  return data;
}

export async function updateMyProfile(payload) {
  const { data } = await apiClient.put('/patients/me', payload);
  return data;
}
