import { create } from "zustand";

export type AuthStatus =
  | "booting"
  | "setup"
  | "anonymous"
  | "authenticated";

export type SessionDays = "1" | "7" | "30" | "365";

type AuthState = {
  token: string | null;
  user: null;
  status: AuthStatus;
  setSession: (token: string) => void;
  clearSession: () => void;
  setStatus: (status: AuthStatus) => void;
  setTokenOnly: (token: string | null) => void;
};

/**
 * In-memory auth session for the nexus app.
 */
export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  status: "booting",
  setSession: (token) => set({ token, status: "authenticated" }),
  clearSession: () =>
    set({ token: null, user: null, status: "anonymous" }),
  setStatus: (status) => set({ status }),
  setTokenOnly: (token) => set({ token }),
}));
