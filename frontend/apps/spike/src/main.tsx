import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { ThemeController } from "@beamlink/ui";
import { router } from "./router.tsx";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeController>
      <RouterProvider router={router} />
    </ThemeController>
  </StrictMode>,
);
