import type { CSSProperties, ReactNode } from "react";
import {
  AppShell,
  Box,
  NavLink,
  Stack,
  Text,
  Tooltip,
} from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";
import { Notifications } from "@mantine/notifications";
import { glassSurfaceStyle } from "./glass.ts";

const SIDEBAR_FULL = 220;
const SIDEBAR_RAIL = 72;

export type AppFrameNavItem = {
  key: string;
  label: string;
  icon: ReactNode;
  to: string;
};

export type AppFrameProps = {
  brand: string;
  navItems: AppFrameNavItem[];
  activeKey: string;
  onNavigate: (to: string) => void;
  footer?: ReactNode;
  children: ReactNode;
};

const noDrag = { WebkitAppRegion: "no-drag" } as CSSProperties;

/**
 * Shared app shell with sidebar nav and a glass main area.
 */
export function AppFrame({
  brand,
  navItems,
  activeKey,
  onNavigate,
  footer,
  children,
}: AppFrameProps) {
  const isRail = useMediaQuery("(max-width: 899px)") ?? false;
  const navbarWidth = isRail ? SIDEBAR_RAIL : SIDEBAR_FULL;

  return (
    <AppShell
      mode="static"
      withBorder
      padding="md"
      navbar={{ width: navbarWidth, breakpoint: 0 }}
      styles={{
        root: {
          height: "100%",
          ...glassSurfaceStyle(),
        },
        navbar: {
          ...glassSurfaceStyle(),
          borderColor: "var(--bl-border)",
          ...noDrag,
        },
        main: {
          ...glassSurfaceStyle(),
          overflow: "auto",
        },
      }}
    >
      <Notifications />
      <AppShell.Navbar p={isRail ? "xs" : "md"}>
        <AppShell.Section>
          <Text
            fw={700}
            size={isRail ? "sm" : "lg"}
            c="var(--bl-primary-text)"
            ta={isRail ? "center" : "left"}
            mb="md"
            lineClamp={1}
          >
            {isRail ? brand.slice(0, 1) : brand}
          </Text>
        </AppShell.Section>

        <AppShell.Section grow>
          <Stack gap={4}>
            {navItems.map((item) => {
              const active = item.key === activeKey;
              const link = (
                <NavLink
                  key={item.key}
                  active={active}
                  label={isRail ? undefined : item.label}
                  leftSection={item.icon}
                  onClick={() => onNavigate(item.to)}
                  variant={active ? "light" : "subtle"}
                  color="brand"
                  style={noDrag}
                  styles={{
                    root: {
                      color: active
                        ? undefined
                        : "var(--bl-text-secondary)",
                      borderRadius: 8,
                    },
                  }}
                />
              );

              if (!isRail) {
                return link;
              }

              return (
                <Tooltip key={item.key} label={item.label} position="right">
                  <Box>{link}</Box>
                </Tooltip>
              );
            })}
          </Stack>
        </AppShell.Section>

        {footer ? (
          <AppShell.Section mt="md" style={noDrag}>
            {footer}
          </AppShell.Section>
        ) : null}
      </AppShell.Navbar>

      <AppShell.Main>{children}</AppShell.Main>
    </AppShell>
  );
}
