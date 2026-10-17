export type SaveState = "idle" | "saving" | "success" | "error";

export interface UploadDirState {
  current: string;
  save: SaveState;
  error: string | null;
}

export interface LogState {
  opening: boolean;
  error: string | null;
}

export interface CredentialsState {
  currentPassword: string;
  newUsername: string;
  newPassword: string;
  confirmNewPassword: string;
  save: SaveState;
  error: string | null;
}

export interface SpeedCapState {
  value: string;
  save: SaveState;
  error: string | null;
}

export interface ResumePolicyState {
  value: "OWNER_ONLY" | "ANYONE";
  save: SaveState;
  error: string | null;
}
