import axios from "axios";

export const apiClient = axios.create({
  timeout: 10000,
  withCredentials: false,
});

export const healthClient = axios.create({
  timeout: 5000,
});

/**
 * Points clients at the runtime backend URL.
 */
export function setApiBaseUrl(backendUrl: string) {
  if (!backendUrl) return;
  apiClient.defaults.baseURL = `${backendUrl}/api/device`;
  healthClient.defaults.baseURL = backendUrl;
}
