import { useState, useEffect, useRef } from "react";
import { Outlet, useLocation, useNavigate } from "react-router";
import { Button } from "@mantine/core";
import {
  LogOut,
  Upload,
  LayoutDashboard,
  FolderOpen,
  Settings,
} from "lucide-react";
import { AppFrame, WarpBackground } from "@beamlink/ui";
import {
  useHasActiveTransfers,
  useTransfersQuery,
} from "@beamlink/features";
import { logout } from "../api/authApi";
import { transferApi } from "../api/transferApi";
import { useAuthStore } from "../store/authStore";

export const Dashboard = () => {
  const status = useAuthStore((s) => s.status);
  useTransfersQuery(transferApi, { enabled: status === "authenticated" });
  const hasActive = useHasActiveTransfers();

  const [warpActive, setWarpActive] = useState(false);
  const warpOffTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  if (hasActive && !warpActive) {
    setWarpActive(true);
  }

  useEffect(() => {
    if (hasActive) {
      if (warpOffTimer.current) {
        clearTimeout(warpOffTimer.current);
        warpOffTimer.current = null;
      }
      return;
    }
    warpOffTimer.current = setTimeout(() => {
      setWarpActive(false);
      warpOffTimer.current = null;
    }, 1200);
    return () => {
      if (warpOffTimer.current) {
        clearTimeout(warpOffTimer.current);
        warpOffTimer.current = null;
      }
    };
  }, [hasActive]);

  const navigate = useNavigate();
  const location = useLocation();
  const activeKey = location.pathname.replace(/^\//, "") || "upload";

  return (
    <div
      style={{
        position: "relative",
        height: "calc(100vh - 40px)",
        overflow: "hidden",
      }}
    >
      <WarpBackground active={warpActive} />

      <div style={{ position: "relative", zIndex: 10, height: "100%" }}>
        <AppFrame
          brand="BeamLink Agent"
          activeKey={activeKey}
          onNavigate={(to) => navigate(to)}
          navItems={[
            {
              key: "upload",
              label: "Upload",
              to: "/upload",
              icon: <Upload size={16} />,
            },
            {
              key: "info",
              label: "Info",
              to: "/info",
              icon: <LayoutDashboard size={16} />,
            },
            {
              key: "transfers",
              label: "Transfers",
              to: "/transfers",
              icon: <FolderOpen size={16} />,
            },
            {
              key: "settings",
              label: "Settings",
              to: "/settings",
              icon: <Settings size={16} />,
            },
          ]}
          footer={
            <Button
              variant="subtle"
              color="brand"
              fullWidth
              leftSection={<LogOut size={16} />}
              onClick={() => void logout()}
              styles={{
                root: { WebkitAppRegion: "no-drag" } as React.CSSProperties,
              }}
            >
              Logout
            </Button>
          }
        >
          <Outlet />
        </AppFrame>
      </div>
    </div>
  );
};
