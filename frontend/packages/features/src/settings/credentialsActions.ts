import type { SettingsApi } from "@beamlink/core";
import type { CredentialsState } from "./types.ts";

type SetCredentials = (
  value:
    | CredentialsState
    | ((prev: CredentialsState) => CredentialsState),
) => void;

/**
 * Changes credentials then logs out on success.
 */
export async function changeCredentialsAction(
  settingsApi: SettingsApi,
  credentials: CredentialsState,
  setCredentials: SetCredentials,
  onLogout: () => void,
): Promise<void> {
  const { currentPassword, newUsername, newPassword, confirmNewPassword } =
    credentials;

  if (!currentPassword) {
    setCredentials((prev) => ({
      ...prev,
      error: "Current password is required.",
    }));
    return;
  }
  if (!newUsername.trim()) {
    setCredentials((prev) => ({
      ...prev,
      error: "Username cannot be blank.",
    }));
    return;
  }
  if (newPassword.length < 6) {
    setCredentials((prev) => ({
      ...prev,
      error: "New password must be at least 6 characters.",
    }));
    return;
  }
  if (newPassword !== confirmNewPassword) {
    setCredentials((prev) => ({
      ...prev,
      error: "New passwords do not match.",
    }));
    return;
  }

  setCredentials((prev) => ({ ...prev, save: "saving", error: null }));

  try {
    await settingsApi.changeCredentials({
      currentPassword,
      newUsername,
      newPassword,
    });

    setCredentials({
      currentPassword: "",
      newUsername: "",
      newPassword: "",
      confirmNewPassword: "",
      save: "success",
      error: null,
    });

    setTimeout(() => {
      setCredentials((prev) => ({ ...prev, save: "idle" }));
      onLogout();
    }, 1500);
  } catch (err: unknown) {
    const axiosErr = err as { response?: { data?: { error?: string } } };
    const message =
      axiosErr.response?.data?.error ?? "Failed to update credentials.";
    setCredentials((prev) => ({ ...prev, save: "error", error: message }));
  }
}
