import { apiClient } from "./client";
import { useAuthStore, type AuthUser } from "../store/authStore";

/**
 * Logs in and writes the session to the auth store.
 */
export async function login(username: string, password: string): Promise<void> {
  const response = await apiClient.post("/auth/login", {
    username: username.trim(),
    password,
  });
  useAuthStore
    .getState()
    .setSession(response.data.token, response.data.identity ?? null);
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

/**
 * Registers a new local agent user.
 */
export async function register(
  username: string,
  password: string,
): Promise<void> {
  await apiClient.post("/auth/register", { username, password });
}

/**
 * Loads /auth/me into the auth store.
 */
export async function me(): Promise<AuthUser | null> {
  try {
    const response = await apiClient.get<AuthUser>("/auth/me");
    const token = useAuthStore.getState().token;
    if (token) {
      useAuthStore.getState().setSession(token, response.data);
    } else {
      useAuthStore.getState().setUser(response.data);
    }
    return response.data;
  } catch {
    useAuthStore.getState().clearSession();
    return null;
  }
}
