import { apiClient } from "./client";
import { useAuthStore } from "../store/authStore";

/**
 * Logs in and writes the session to the auth store.
 */
export async function login(username: string, password: string): Promise<void> {
  username = username.trim();
  const response = await apiClient.post("/auth/login", {
    username,
    password,
  });
  useAuthStore.getState().setSession(response.data.token);
}

/**
 * Logs out and clears the auth store.
 */
export async function logout(): Promise<void> {
  try {
    await apiClient.post("/auth/logout");
  } finally {
    useAuthStore.getState().clearSession();
  }
}
