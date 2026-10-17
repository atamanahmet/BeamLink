import { createHashRouter, Navigate } from "react-router";
import { RouteErrorScreen } from "@beamlink/ui";
import App from "./App";
import { Dashboard } from "./components/Dashboard";
import { Login } from "./components/Login";
import { Register } from "./components/Register";
import { RedirectIfAuthenticated, RequireAuth } from "./RequireAuth";

export const router = createHashRouter([
  {
    path: "/",
    element: <App />,
    errorElement: <RouteErrorScreen />,
    children: [
      {
        element: <RedirectIfAuthenticated />,
        children: [
          { path: "login", element: <Login /> },
          { path: "register", element: <Register /> },
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
                path: "info",
                lazy: () =>
                  import("./components/AgentInfo").then((m) => ({
                    Component: m.AgentInfo,
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
