import { problemFromUnknown } from "@beamlink/core";
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
      correlationId: null,
    }));
    return;
  }
  if (!newUsername.trim()) {
    setCredentials((prev) => ({
      ...prev,
      error: "Username cannot be blank.",
      correlationId: null,
    }));
    return;
  }
  if (newPassword.length < 8) {
    setCredentials((prev) => ({
      ...prev,
      error: "New password must be at least 8 characters.",
      correlationId: null,
    }));
    return;
  }
  if (newPassword !== confirmNewPassword) {
    setCredentials((prev) => ({
      ...prev,
      error: "New passwords do not match.",
      correlationId: null,
    }));
    return;
  }

  setCredentials((prev) => ({
    ...prev,
    save: "saving",
    error: null,
    correlationId: null,
  }));

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
      correlationId: null,
    });

    setTimeout(() => {
      setCredentials((prev) => ({ ...prev, save: "idle" }));
      onLogout();
    }, 1500);
  } catch (err: unknown) {
    const problem = problemFromUnknown(err);
    const message = problem.detail ?? "Failed to update credentials.";
    setCredentials((prev) => ({
      ...prev,
      save: "error",
      error: message,
      correlationId: problem.correlationId,
    }));
  }
}
