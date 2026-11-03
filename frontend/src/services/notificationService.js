import apiClient from '../api/client.js';

export async function listNotifications({ unreadOnly = false, page = 0, size = 20 } = {}) {
  const { data } = await apiClient.get('/notifications', { params: { unreadOnly, page, size } });
  return data;
}

export async function getUnreadCount() {
  const { data } = await apiClient.get('/notifications/unread-count');
  return data.unread;
}

export async function markNotificationRead(id) {
  const { data } = await apiClient.patch(`/notifications/${id}/read`);
  return data;
}

export async function markAllNotificationsRead() {
  const { data } = await apiClient.patch('/notifications/read-all');
  return data;
}
