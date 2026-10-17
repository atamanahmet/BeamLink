import { create } from "zustand";

export type AuthStatus = "anonymous" | "authenticated";

type AuthState = {
  token: string | null;
  user: null;
  status: AuthStatus;
  setSession: (token: string) => void;
  clearSession: () => void;
};

/**
 * In-memory auth session for the nexus app.
 */
export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  status: "anonymous",
  setSession: (token) => set({ token, status: "authenticated" }),
  clearSession: () =>
    set({ token: null, user: null, status: "anonymous" }),
}));
