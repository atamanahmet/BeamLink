import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router/dom";
import {
  createElectronPlatform,
  PlatformProvider,
} from "@beamlink/platform";
import "./index.css";
import { router } from "./router.tsx";
import { ThemeWithPlatform } from "./components/ThemeWithPlatform.tsx";

/**
 * Creates the platform, then mounts the app.
 */
async function bootstrap() {
  const platform = await createElectronPlatform();
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <PlatformProvider platform={platform}>
        <ThemeWithPlatform>
          <RouterProvider router={router} />
        </ThemeWithPlatform>
      </PlatformProvider>
    </StrictMode>,
  );
}

void bootstrap();
