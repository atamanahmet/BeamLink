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
            path: "transfers",
            lazy: () => import("./routes/TransfersRoute"),
          },
          {
            path: "settings",
            lazy: () => import("./routes/SettingsRoute"),
          },
          {
            path: "connect",
            lazy: () => import("./routes/ConnectRoute"),
          },
        ],
      },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
]);
