import { apiClient } from "./client";
import { useAuthStore, type SessionDays } from "../store/authStore";

type AuthIpcResult =
  | { ok: true; data?: string | null }
  | { ok: false; code: string };

/**
 * Saves the admin token via Electron safeStorage when available.
 */
async function persistToken(token: string): Promise<void> {
  const save = window.electronAPI?.saveAuthToken;
  if (!save) return;
  const result = (await save(token)) as AuthIpcResult;
  if (!result.ok) {
    console.warn("[auth] save-token failed:", result.code);
  }
}

/**
 * Clears the persisted admin token.
 */
async function clearPersistedToken(): Promise<void> {
  const clear = window.electronAPI?.clearAuthToken;
  if (!clear) return;
  await clear();
}

/**
 * Loads a persisted admin token, or null.
 */
export async function loadPersistedToken(): Promise<string | null> {
  const load = window.electronAPI?.loadAuthToken;
  if (!load) return null;
  const result = (await load()) as AuthIpcResult;
  if (!result.ok) return null;
  return result.data ?? null;
}

/**
 * Reads whether first-run setup is required.
 */
export async function fetchAuthStatus(): Promise<{ setupRequired: boolean }> {
  const response = await apiClient.get<{ setupRequired: boolean }>(
    "/auth/status",
  );
  return response.data;
}

/**
 * Sets the first admin password.
 */
export async function setupAdmin(
  username: string,
  password: string,
): Promise<string> {
  const response = await apiClient.post<{ recoveryCode: string }>(
    "/auth/setup",
    {
      username: username.trim(),
      password,
    },
  );
  return response.data.recoveryCode;
}

/**
 * Resets the admin password with the recovery code. Returns the new code once.
 */
export async function resetPassword(
  recoveryCode: string,
  newPassword: string,
): Promise<string> {
  const response = await apiClient.post<{ recoveryCode: string }>(
    "/auth/reset-password",
    { recoveryCode, newPassword },
  );
  return response.data.recoveryCode;
}

/**
 * Issues a new recovery code. Needs the current password.
 */
export async function regenerateRecoveryCode(
  currentPassword: string,
): Promise<string> {
  const response = await apiClient.post<{ recoveryCode: string }>(
    "/auth/regenerate-recovery-code",
    { currentPassword },
  );
  return response.data.recoveryCode;
}

/**
 * Wipes nexus data. Loopback plus UI secret only.
 */
export async function eraseAll(): Promise<void> {
  await apiClient.post("/auth/erase");
}

/**
 * Reads whether a recovery code hash exists.
 */
export async function fetchMe(): Promise<{ hasRecoveryCode: boolean }> {
  const response = await apiClient.get<{ hasRecoveryCode: boolean }>("/auth/me");
  return response.data;
}

/**
 * Logs in, persists the token, and writes the session to the auth store.
 */
export async function login(
  username: string,
  password: string,
  sessionDays: SessionDays = "1",
): Promise<void> {
  username = username.trim();
  const response = await apiClient.post("/auth/login", {
    username,
    password,
    sessionDays,
  });
  const token = response.data.token as string;
  await persistToken(token);
  useAuthStore.getState().setSession(token);
}

/**
 * Validates a stored token with /me.
 */
export async function validateSession(): Promise<boolean> {
  try {
    await apiClient.get("/auth/me");
    return true;
  } catch {
    return false;
  }
}

/**
 * Logs out, clears memory and persisted token.
 */
export async function logout(): Promise<void> {
  try {
    await apiClient.post("/auth/logout");
  } finally {
    await clearPersistedToken();
    useAuthStore.getState().clearSession();
  }
}

/**
 * Revokes all admin sessions on the server and clears local state.
 */
export async function logoutAll(): Promise<void> {
  try {
    await apiClient.post("/auth/logout-all");
  } finally {
    await clearPersistedToken();
    useAuthStore.getState().clearSession();
  }
}
