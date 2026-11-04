import { createHashRouter, Navigate } from "react-router";
import { RouteErrorScreen } from "@beamlink/ui";
import App from "./App";
import { Dashboard } from "./components/Dashboard";
import { Login } from "./components/Login";
import { ResetPassword } from "./components/ResetPassword";
import { Setup } from "./components/Setup";
import {
  RedirectIfAuthenticated,
  RequireAuth,
  RequireSetup,
} from "./RequireAuth";

export const router = createHashRouter([
  {
    path: "/",
    element: <App />,
    errorElement: <RouteErrorScreen />,
    children: [
      {
        element: <RequireSetup />,
        children: [{ path: "setup", element: <Setup /> }],
      },
      {
        element: <RedirectIfAuthenticated />,
        children: [
          { path: "login", element: <Login /> },
          { path: "reset-password", element: <ResetPassword /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <Dashboard />,
            children: [
              { index: true, element: <Navigate to="/upload" replace /> },
              {
                path: "upload",
                lazy: () => import("./routes/UploadRoute"),
              },
              {
                path: "network",
                lazy: () =>
                  import("./components/NetworkView").then((m) => ({
                    Component: m.NetworkView,
                  })),
              },
              {
                path: "transfers",
                lazy: () => import("./routes/TransferRoute"),
              },
              {
                path: "settings",
                lazy: () => import("./routes/SettingsRoute"),
              },
            ],
          },
        ],
      },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
]);
