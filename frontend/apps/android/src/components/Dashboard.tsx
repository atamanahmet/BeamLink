import { Outlet, useLocation, useNavigate } from "react-router";
import { ArrowUpFromLine, List, Settings } from "lucide-react";
import { AppFrame } from "@beamlink/ui";
import appLogo from "../assets/icon.png";

const PAGE_TITLE: Record<string, string> = {
  upload: "Send",
  transfers: "Transfers",
  settings: "Settings",
  connect: "Connect",
};

export const Dashboard = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const activeKey = location.pathname.replace(/^\//, "") || "upload";

  const navItems = [
    {
      key: "upload",
      label: "Send",
      to: "/upload",
      icon: <ArrowUpFromLine size={20} />,
    },
    {
      key: "transfers",
      label: "Transfers",
      to: "/transfers",
      icon: <List size={20} />,
    },
    {
      key: "settings",
      label: "Settings",
      to: "/settings",
      icon: <Settings size={20} />,
    },
  ];

  return (
    <div
      style={{
        position: "relative",
        height: "100%",
        overflow: "hidden",
      }}
    >
      <div style={{ position: "relative", zIndex: 10, height: "100%" }}>
        <AppFrame
          brand="BeamLink Peer"
          phoneShell
          title={PAGE_TITLE[activeKey] ?? "Send"}
          logo={
            <img
              src={appLogo}
              alt=""
              width={32}
              height={32}
              style={{ objectFit: "contain", flexShrink: 0 }}
            />
          }
          activeKey={activeKey}
          onNavigate={(to) => navigate(to)}
          navItems={navItems}
        >
          <Outlet />
        </AppFrame>
      </div>
    </div>
  );
};
