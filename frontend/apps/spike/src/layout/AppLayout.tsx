import { AppShell, Box, Group, NavLink } from "@mantine/core";
import { FolderInput, Home, Layers, LogIn, Palette } from "lucide-react";
import { Outlet, useLocation, useNavigate } from "react-router";

/**
 * Responsive shell: full nav, icon rail, or bottom bar by breakpoint.
 * Uses xs for mobile collapse so 400/700/1100 map to the three layouts.
 */
export function AppLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const homeActive = location.pathname === "/";
  const lazyActive = location.pathname === "/lazy";
  const dropActive = location.pathname === "/drop";
  const themeActive = location.pathname === "/theme";
  const loginActive = location.pathname === "/login";

  return (
    <AppShell
      navbar={{
        width: { xs: 64, md: 220 },
        breakpoint: "xs",
        collapsed: { mobile: true },
      }}
      footer={{ height: { base: 56, xs: 0 } }}
      padding="md"
    >
      <AppShell.Navbar p="xs" data-testid="navbar">
        <NavLink
          label={
            <Box component="span" visibleFrom="md" data-testid="nav-label">
              Home
            </Box>
          }
          leftSection={<Home size={18} />}
          active={homeActive}
          onClick={() => navigate("/")}
        />
        <NavLink
          label={
            <Box component="span" visibleFrom="md" data-testid="nav-label">
              Lazy
            </Box>
          }
          leftSection={<Layers size={18} />}
          active={lazyActive}
          onClick={() => navigate("/lazy")}
        />
        <NavLink
          label={
            <Box component="span" visibleFrom="md" data-testid="nav-label">
              Drop
            </Box>
          }
          leftSection={<FolderInput size={18} />}
          active={dropActive}
          onClick={() => navigate("/drop")}
        />
        <NavLink
          label={
            <Box component="span" visibleFrom="md" data-testid="nav-label">
              Theme
            </Box>
          }
          leftSection={<Palette size={18} />}
          active={themeActive}
          onClick={() => navigate("/theme")}
        />
        <NavLink
          label={
            <Box component="span" visibleFrom="md" data-testid="nav-label">
              Login
            </Box>
          }
          leftSection={<LogIn size={18} />}
          active={loginActive}
          onClick={() => navigate("/login")}
        />
      </AppShell.Navbar>

      <AppShell.Footer hiddenFrom="xs" data-testid="bottom-nav" p="xs">
        <Group justify="center" gap="xl" grow>
          <NavLink
            label="Home"
            leftSection={<Home size={18} />}
            active={homeActive}
            onClick={() => navigate("/")}
          />
          <NavLink
            label="Lazy"
            leftSection={<Layers size={18} />}
            active={lazyActive}
            onClick={() => navigate("/lazy")}
          />
          <NavLink
            label="Drop"
            leftSection={<FolderInput size={18} />}
            active={dropActive}
            onClick={() => navigate("/drop")}
          />
          <NavLink
            label="Theme"
            leftSection={<Palette size={18} />}
            active={themeActive}
            onClick={() => navigate("/theme")}
          />
          <NavLink
            label="Login"
            leftSection={<LogIn size={18} />}
            active={loginActive}
            onClick={() => navigate("/login")}
          />
        </Group>
      </AppShell.Footer>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
