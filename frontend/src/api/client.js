import axios from 'axios';

/**
 * Shared Axios instance for every backend call.
 *
 * - baseURL '/api' goes through the Vite proxy in development (same origin).
 * - withCredentials sends the HTTP-only MEDICARE_SESSION cookie.
 * - Axios copies the XSRF-TOKEN cookie into the X-XSRF-TOKEN header for
 *   same-origin requests, satisfying Spring Security's CSRF protection.
 * - No auth token is ever stored in localStorage.
 */
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 15000,
  headers: { Accept: 'application/json' },
});

/** Normalises any Axios error into { status, code, message, fieldErrors }. */
export function toApiError(error) {
  if (error?.response) {
    const data = error.response.data || {};
    return {
      status: error.response.status,
      code: data.code || 'HTTP_ERROR',
      message: data.message || `Request failed with status ${error.response.status}`,
      fieldErrors: data.fieldErrors || [],
      data,
    };
  }
  if (error?.code === 'ECONNABORTED') {
    return { status: 0, code: 'TIMEOUT', message: 'The server took too long to respond.', fieldErrors: [] };
  }
  return {
    status: 0,
    code: 'NETWORK_ERROR',
    message: 'Cannot reach the MediCare server. Check that the backend is running.',
    fieldErrors: [],
  };
}

let unauthorizedHandler = null;

/**
 * Registers a callback invoked when an authenticated request returns 401
 * (session expired, revoked or logged out elsewhere). Used by AuthContext.
 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const apiError = toApiError(error);
    const url = error?.config?.url || '';
    if (apiError.status === 401 && !url.startsWith('/auth/') && unauthorizedHandler) {
      unauthorizedHandler(apiError);
    }
    return Promise.reject(apiError);
  },
);

export default apiClient;
