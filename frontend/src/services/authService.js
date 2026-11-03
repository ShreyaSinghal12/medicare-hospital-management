import apiClient from '../api/client.js';

/** GET /api/auth/me — the logged-in user, or null when not logged in (204). */
export async function fetchCurrentUser() {
  const response = await apiClient.get('/auth/me');
  return response.status === 204 ? null : response.data;
}

export async function login(email, password) {
  const { data } = await apiClient.post('/auth/login', { email, password });
  return data;
}

export async function logout() {
  await apiClient.post('/auth/logout');
}

export async function registerPatient(payload) {
  const { data } = await apiClient.post('/auth/register', payload);
  return data;
}

export async function forgotPassword(email) {
  const { data } = await apiClient.post('/auth/forgot-password', { email });
  return data;
}

export async function resetPassword(payload) {
  const { data } = await apiClient.post('/auth/reset-password', payload);
  return data;
}
