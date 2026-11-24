import { Outlet } from "react-router";
import { FirstRunHost } from "@beamlink/features";
import { settingsApi } from "./api/settingsApi";

/**
 * Authenticated layout. Renders only under RequireAuth.
 */
export function AuthenticatedShell() {
  return (
    <>
      <Outlet />
      <FirstRunHost settingsApi={settingsApi} includeResumePolicy />
    </>
  );
}
