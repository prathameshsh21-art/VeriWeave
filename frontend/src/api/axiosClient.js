import axios from 'axios';

const axiosClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('veriweave_access_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    const activeWorkspaceId = localStorage.getItem('veriweave_active_workspace_id');
    if (activeWorkspaceId) {
      config.headers['X-Workspace-Id'] = activeWorkspaceId;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      const refreshToken = localStorage.getItem('veriweave_refresh_token');
      if (refreshToken) {
        try {
          const res = await axios.post('/api/auth/refresh', { refreshToken });
          if (res.data?.success && res.data?.data?.accessToken) {
            const newAccess = res.data.data.accessToken;
            const newRefresh = res.data.data.refreshToken;
            localStorage.setItem('veriweave_access_token', newAccess);
            if (newRefresh) localStorage.setItem('veriweave_refresh_token', newRefresh);
            originalRequest.headers.Authorization = `Bearer ${newAccess}`;
            return axiosClient(originalRequest);
          }
        } catch (refreshErr) {
          localStorage.removeItem('veriweave_access_token');
          localStorage.removeItem('veriweave_refresh_token');
          window.dispatchEvent(new Event('auth:unauthorized'));
        }
      } else {
        window.dispatchEvent(new Event('auth:unauthorized'));
      }
    }
    return Promise.reject(error);
  }
);

export default axiosClient;
