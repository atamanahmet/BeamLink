import type { CSSProperties, ReactNode } from "react";
import {
  AppShell,
  Box,
  Group,
  NavLink,
  Stack,
  Text,
  Tooltip,
  UnstyledButton,
} from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";
import { Notifications } from "@mantine/notifications";
import { glassSurfaceStyle } from "./glass.ts";

const SIDEBAR_FULL = 220;
const RAIL_ITEM = 36;
const RAIL_PAD = 8;
const SIDEBAR_RAIL = RAIL_ITEM + RAIL_PAD * 2;
const PHONE_NAV_HEIGHT = 56;
const PHONE_HEADER_HEIGHT = 56;

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
  /** Phone shell: bottom nav, no rail, no glass. Desktop callers omit. */
  phoneShell?: boolean;
  /** Phone header title. Falls back to brand. */
  title?: string;
  /** Phone header logo. One image node from the app assets. */
  logo?: ReactNode;
};

const noDrag = { WebkitAppRegion: "no-drag" } as CSSProperties;

/**
 * Phone shell: header and 56px bottom nav, padded by CSS env safe-area insets.
 */
function PhoneAppFrame({
  brand,
  title,
  logo,
  navItems,
  activeKey,
  onNavigate,
  children,
}: AppFrameProps) {
  return (
    <AppShell
      mode="static"
      withBorder={false}
      padding="md"
      header={{
        height: `calc(${PHONE_HEADER_HEIGHT}px + env(safe-area-inset-top, 0px))`,
      }}
      footer={{
        height: `calc(${PHONE_NAV_HEIGHT}px + env(safe-area-inset-bottom, 0px))`,
      }}
      styles={{
        root: {
          height: "100%",
          backgroundColor: "var(--bl-background)",
        },
        header: {
          backgroundColor: "var(--bl-background)",
          borderColor: "var(--bl-border)",
          paddingTop: "env(safe-area-inset-top, 0px)",
        },
        footer: {
          backgroundColor: "var(--bl-surface)",
          borderColor: "var(--bl-border)",
          paddingBottom: "env(safe-area-inset-bottom, 0px)",
        },
        main: {
          backgroundColor: "var(--bl-background)",
          overflow: "auto",
        },
      }}
    >
      <Notifications />
      <AppShell.Header>
        <Group h={PHONE_HEADER_HEIGHT} px="md" align="center" gap="sm" wrap="nowrap">
          {logo}
          <Text fw={700} size="md" c="var(--bl-text-primary)" lineClamp={1}>
            {title ?? brand}
          </Text>
        </Group>
      </AppShell.Header>
      <AppShell.Main>{children}</AppShell.Main>
      <AppShell.Footer>
        <Group grow gap={0} h={PHONE_NAV_HEIGHT} wrap="nowrap">
          {navItems.map((item) => {
            const active = item.key === activeKey;
            return (
              <UnstyledButton
                key={item.key}
                onClick={() => onNavigate(item.to)}
                aria-current={active ? "page" : undefined}
                style={{
                  height: PHONE_NAV_HEIGHT,
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  backgroundColor: "transparent",
                }}
              >
                <Box
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: 2,
                    padding: "4px 12px",
                    borderRadius: 12,
                    color: active
                      ? "var(--bl-primary-text)"
                      : "var(--bl-text-secondary)",
                    backgroundColor: active
                      ? "var(--bl-primary-subtle)"
                      : "transparent",
                  }}
                >
                  {item.icon}
                  <Text size="sm" fw={active ? 700 : 500} c="inherit">
                    {item.label}
                  </Text>
                </Box>
              </UnstyledButton>
            );
          })}
        </Group>
      </AppShell.Footer>
    </AppShell>
  );
}

/**
 * Shared app shell with sidebar nav and a glass main area.
 */
export function AppFrame(props: AppFrameProps) {
  if (props.phoneShell) {
    return <PhoneAppFrame {...props} />;
  }
  return <DesktopAppFrame {...props} />;
}

/**
 * Desktop and tablet shell. Sidebar or rail. Unchanged for non-phone callers.
 */
function DesktopAppFrame({
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
      <AppShell.Navbar p={isRail ? RAIL_PAD : "md"}>
        <AppShell.Section grow>
          <Stack gap={4} align={isRail ? "center" : "stretch"}>
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
                  aria-label={isRail ? item.label : undefined}
                  w={isRail ? RAIL_ITEM : undefined}
                  h={isRail ? RAIL_ITEM : undefined}
                  p={isRail ? 0 : undefined}
                  style={noDrag}
                  styles={{
                    root: {
                      color: active
                        ? undefined
                        : "var(--bl-text-secondary)",
                      borderRadius: 8,
                      justifyContent: isRail ? "center" : undefined,
                    },
                    body: isRail ? { display: "none" } : undefined,
                    section: isRail ? { margin: 0 } : undefined,
                  }}
                />
              );

              if (!isRail) {
                return link;
              }

              return (
                <Tooltip key={item.key} label={item.label} position="right">
                  {link}
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
