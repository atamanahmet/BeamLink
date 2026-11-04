import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router/dom";
import { PlatformProvider } from "@beamlink/platform";
import "./index.css";
import { router } from "./router.tsx";
import { ThemeWithPlatform } from "./components/ThemeWithPlatform.tsx";
import { createAndroidPlatform } from "./platform/createAndroidPlatform.ts";

/**
 * Creates the Android platform, then mounts the app.
 */
async function bootstrap() {
  const platform = await createAndroidPlatform();
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
