import axios from "axios";

export const apiClient = axios.create({
  baseURL: import.meta.env.DEV
    ? "/api/device"
    : "http://localhost:9090/api/device",
  timeout: 10000,
  withCredentials: false,
});

export const healthClient = axios.create({
  baseURL: import.meta.env.DEV ? "" : "http://localhost:9090",
  timeout: 5000,
});

/**
 * Points clients at the runtime backend URL in production.
 */
export function setApiBaseUrl(backendUrl: string) {
  if (import.meta.env.DEV) return;
  apiClient.defaults.baseURL = `${backendUrl}/api/device`;
  healthClient.defaults.baseURL = backendUrl;
}
