import { create } from "zustand";

export type AuthUser = {
  agentId: string;
  agentName: string;
  state: string;
};

export type AuthStatus = "anonymous" | "authenticated";

type AuthState = {
  token: string | null;
  user: AuthUser | null;
  status: AuthStatus;
  setSession: (token: string, user: AuthUser | null) => void;
  clearSession: () => void;
  setUser: (user: AuthUser | null) => void;
};

/**
 * In-memory auth session for the peer app.
 */
export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  status: "anonymous",
  setSession: (token, user) =>
    set({ token, user, status: "authenticated" }),
  clearSession: () =>
    set({ token: null, user: null, status: "anonymous" }),
  setUser: (user) => set({ user }),
}));
