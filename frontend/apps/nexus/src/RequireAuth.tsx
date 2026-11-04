import { Navigate, Outlet } from "react-router";
import { useAuthStore } from "./store/authStore";

/**
 * Blocks the dashboard until the user is logged in.
 */
export function RequireAuth() {
  const status = useAuthStore((s) => s.status);
  if (status === "booting") {
    return null;
  }
  if (status === "setup") {
    return <Navigate to="/setup" replace />;
  }
  if (status !== "authenticated") {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}

/**
 * Sends a logged in user away from login and setup.
 */
export function RedirectIfAuthenticated() {
  const status = useAuthStore((s) => s.status);
  if (status === "booting") {
    return null;
  }
  if (status === "authenticated") {
    return <Navigate to="/upload" replace />;
  }
  if (status === "setup") {
    return <Navigate to="/setup" replace />;
  }
  return <Outlet />;
}

/**
 * Shows setup only while setup is required.
 */
export function RequireSetup() {
  const status = useAuthStore((s) => s.status);
  if (status === "booting") {
    return null;
  }
  if (status === "authenticated") {
    return <Navigate to="/upload" replace />;
  }
  if (status !== "setup") {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
