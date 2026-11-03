import { describe, expect, it, vi } from "vitest";
import { changeCredentialsAction } from "./credentialsActions.ts";
import type { CredentialsState } from "./types.ts";

function baseCredentials(
  patch: Partial<CredentialsState> = {},
): CredentialsState {
  return {
    currentPassword: "old-password",
    newUsername: "admin",
    newPassword: "short",
    confirmNewPassword: "short",
    save: "idle",
    error: null,
    ...patch,
  };
}

describe("changeCredentialsAction", () => {
  it("rejects passwords shorter than 8 characters", async () => {
    const setCredentials = vi.fn();
    const onLogout = vi.fn();
    const settingsApi = {
      changeCredentials: vi.fn(),
    };

    await changeCredentialsAction(
      settingsApi as never,
      baseCredentials({ newPassword: "1234567", confirmNewPassword: "1234567" }),
      setCredentials,
      onLogout,
    );

    expect(settingsApi.changeCredentials).not.toHaveBeenCalled();
    expect(setCredentials).toHaveBeenCalled();
    const updater = setCredentials.mock.calls[0][0] as (
      prev: CredentialsState,
    ) => CredentialsState;
    expect(updater(baseCredentials()).error).toBe(
      "New password must be at least 8 characters.",
    );
  });
});
