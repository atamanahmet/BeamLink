import { useState, useEffect, useRef } from "react";
import { Outlet, useLocation, useNavigate } from "react-router";
import {
  Upload,
  LayoutDashboard,
  FolderOpen,
  Settings,
} from "lucide-react";
import { AppFrame, WarpBackground } from "@beamlink/ui";
import { usePlatform } from "@beamlink/platform";
import {
  ConnectModal,
  ConnectOpenProvider,
  useConnectFlow,
  useHasActiveTransfers,
  useTransfersQuery,
} from "@beamlink/features";
import { getDeviceStatus } from "../api/deviceApi";
import { nexusTlsPinAdapter, settingsApi } from "../api/settingsApi";
import { transferApi } from "../api/transferApi";
import { useDeviceStatusStore } from "../store/deviceStatusStore";

export const Dashboard = () => {
  const { capabilities } = usePlatform();
  useTransfersQuery(transferApi, { enabled: true });
  const hasActive = useHasActiveTransfers();

  const [warpActive, setWarpActive] = useState(false);
  const warpOffTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  if (capabilities.warp && hasActive && !warpActive) {
    setWarpActive(true);
  }

  useEffect(() => {
    if (!capabilities.warp) return;
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
      }
    };
  }, [capabilities.warp, hasActive]);

  const navigate = useNavigate();
  const location = useLocation();
  const activeKey = location.pathname.replace(/^\//, "") || "upload";
  const deviceState = useDeviceStatusStore((s) => s.deviceStatus?.state);
  const setDeviceStatus = useDeviceStatusStore((s) => s.setDeviceStatus);
  const flow = useConnectFlow(nexusTlsPinAdapter, deviceState);

  /**
   * Registers again when the pin is already stored.
   */
  const registerPinned = async () => {
    await settingsApi.reRegister?.();
    setDeviceStatus(await getDeviceStatus());
  };

  const navItems = [
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
    ...(capabilities.transferHistory
      ? [
          {
            key: "transfers",
            label: "Transfers",
            to: "/transfers",
            icon: <FolderOpen size={16} />,
          },
        ]
      : []),
    {
      key: "settings",
      label: "Settings",
      to: "/settings",
      icon: <Settings size={16} />,
    },
  ];

  return (
    <ConnectOpenProvider value={flow.openModal}>
      <div
        style={{
          position: "relative",
          height: "calc(100vh - 40px)",
          overflow: "hidden",
        }}
      >
        {capabilities.warp ? <WarpBackground active={warpActive} /> : null}

        <div style={{ position: "relative", zIndex: 10, height: "100%" }}>
          <AppFrame
            brand="BeamLink Peer"
            activeKey={activeKey}
            onNavigate={(to) => navigate(to)}
            navItems={navItems}
          >
            <Outlet />
          </AppFrame>
        </div>
        <ConnectModal
          open={flow.open}
          tlsPin={flow.tlsPin}
          deviceState={deviceState}
          onRegister={registerPinned}
          registerAfterConfirm={false}
          onLater={flow.later}
        />
      </div>
    </ConnectOpenProvider>
  );
};
