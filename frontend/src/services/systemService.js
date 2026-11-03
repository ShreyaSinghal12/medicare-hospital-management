import apiClient from '../api/client.js';

/** GET /api/health — backend + MySQL status. A 503 still carries a useful body. */
export async function getHealth() {
  try {
    const { data } = await apiClient.get('/health');
    return data;
  } catch (err) {
    if (err.status === 503 && err.data?.database) return err.data;
    throw err;
  }
}

/** GET /api/public/hospital-info — hospital details and active departments. */
export async function getHospitalInfo() {
  const { data } = await apiClient.get('/public/hospital-info');
  return data;
}

/** GET /api/auth/csrf — makes the server set the XSRF-TOKEN cookie. */
export async function initCsrf() {
  await apiClient.get('/auth/csrf');
}
