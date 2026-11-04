import { createHashRouter, Navigate } from "react-router";
import { RouteErrorScreen } from "@beamlink/ui";
import App from "./App";
import { Dashboard } from "./components/Dashboard";

export const router = createHashRouter([
  {
    path: "/",
    element: <App />,
    errorElement: <RouteErrorScreen />,
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
              import("./components/DeviceInfo").then((m) => ({
                Component: m.DeviceInfo,
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
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
]);
