import axios from "axios";
import { useAuthStore } from "../store/authStore";

export const apiClient = axios.create({
  baseURL: import.meta.env.DEV
    ? "/api/agent"
    : "http://localhost:9090/api/agent",
  timeout: 10000,
  withCredentials: false,
});

export const healthClient = axios.create({
  baseURL: import.meta.env.DEV ? "" : "http://localhost:9090",
  timeout: 5000,
});

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.set("X-Auth-Token", token);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = String(error.config?.url ?? "");
    if (status === 401 && !url.includes("/auth/login")) {
      useAuthStore.getState().clearSession();
    }
    return Promise.reject(error);
  },
);

/**
 * Points clients at the runtime backend URL in production.
 */
export function setApiBaseUrl(backendUrl: string) {
  if (import.meta.env.DEV) return;
  apiClient.defaults.baseURL = `${backendUrl}/api/agent`;
  healthClient.defaults.baseURL = backendUrl;
}
