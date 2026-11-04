import {
  fetchAuthStatus,
  loadPersistedToken,
  validateSession,
} from "./api/authApi";
import { useAuthStore } from "./store/authStore";

/**
 * Loads a saved token, validates it, or falls to setup or login.
 */
export async function bootstrapAuth(): Promise<void> {
  const store = useAuthStore.getState();
  try {
    const { setupRequired } = await fetchAuthStatus();
    if (setupRequired) {
      store.setStatus("setup");
      return;
    }

    const token = await loadPersistedToken();
    if (!token) {
      store.clearSession();
      return;
    }

    store.setTokenOnly(token);
    const ok = await validateSession();
    if (ok) {
      store.setSession(token);
      return;
    }

    await window.electronAPI?.clearAuthToken?.();
    store.clearSession();
  } catch {
    store.clearSession();
  }
}
