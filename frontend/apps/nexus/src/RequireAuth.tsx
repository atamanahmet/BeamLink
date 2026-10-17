import { Navigate, Outlet } from "react-router";
import { useAuthStore } from "./store/authStore";

/**
 * Blocks the dashboard until the user is logged in.
 */
export function RequireAuth() {
  const status = useAuthStore((s) => s.status);
  if (status !== "authenticated") {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}

/**
 * Sends a logged in user away from login and register.
 */
export function RedirectIfAuthenticated() {
  const status = useAuthStore((s) => s.status);
  if (status === "authenticated") {
    return <Navigate to="/upload" replace />;
  }
  return <Outlet />;
}
