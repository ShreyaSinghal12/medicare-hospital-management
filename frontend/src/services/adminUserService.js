import apiClient from '../api/client.js';

export async function searchUsers({ search, role, active, page = 0, size = 20 } = {}) {
  const params = { page, size };
  if (search) params.search = search;
  if (role) params.role = role;
  if (active !== undefined && active !== '') params.active = active;
  const { data } = await apiClient.get('/admin/users', { params });
  return data;
}

export async function setUserActive(id, active, reason) {
  const { data } = await apiClient.patch(`/admin/users/${id}/status`, { active, reason });
  return data;
}

export async function unlockUser(id) {
  const { data } = await apiClient.patch(`/admin/users/${id}/unlock`);
  return data;
}
