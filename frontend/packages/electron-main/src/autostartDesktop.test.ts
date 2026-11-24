import { describe, expect, test } from "vitest";
import {
  buildDesktopExecLine,
  buildDesktopFileText,
  desktopExecMatches,
  isDesktopAutostartEnabled,
  parseDesktopExec,
  quoteDesktopExecPath,
} from "./autostartDesktop.ts";

describe("quoteDesktopExecPath", () => {
  test("wraps path in double quotes", () => {
    expect(quoteDesktopExecPath("/opt/Beamlink.AppImage")).toBe(
      '"/opt/Beamlink.AppImage"',
    );
  });

  test("keeps spaces inside quotes", () => {
    expect(quoteDesktopExecPath("/opt/Beam Link/App.AppImage")).toBe(
      '"/opt/Beam Link/App.AppImage"',
    );
  });

  test("escapes percent", () => {
    expect(quoteDesktopExecPath("/opt/a%b.AppImage")).toBe(
      '"/opt/a%%b.AppImage"',
    );
  });

  test("escapes dollar", () => {
    expect(quoteDesktopExecPath("/opt/a$b.AppImage")).toBe(
      '"/opt/a\\$b.AppImage"',
    );
  });

  test("escapes backtick", () => {
    expect(quoteDesktopExecPath("/opt/a`b.AppImage")).toBe(
      '"/opt/a\\`b.AppImage"',
    );
  });

  test("escapes backslash", () => {
    expect(quoteDesktopExecPath("/opt/a\\b.AppImage")).toBe(
      '"/opt/a\\\\b.AppImage"',
    );
  });

  test("escapes quote", () => {
    expect(quoteDesktopExecPath('/opt/a"b.AppImage')).toBe(
      '"/opt/a\\"b.AppImage"',
    );
  });
});

describe("buildDesktopExecLine", () => {
  test("builds quoted Exec with --hidden", () => {
    expect(buildDesktopExecLine("/opt/Beam Link/App.AppImage")).toBe(
      'Exec="/opt/Beam Link/App.AppImage" --hidden',
    );
  });
});

describe("buildDesktopFileText", () => {
  test("writes a desktop entry with display Name and quoted Exec", () => {
    const text = buildDesktopFileText({
      name: "Beamlink Peer",
      appImagePath: "/home/user/My Apps/peer.AppImage",
    });
    expect(text).toContain("Name=Beamlink Peer");
    expect(text).toContain('Exec="/home/user/My Apps/peer.AppImage" --hidden');
    expect(text).toContain("Terminal=false");
    expect(text).toContain("Type=Application");
    expect(text).toContain("X-GNOME-Autostart-enabled=true");
  });
});

describe("isDesktopAutostartEnabled", () => {
  test("true when key missing", () => {
    expect(isDesktopAutostartEnabled("[Desktop Entry]\nExec=x\n")).toBe(true);
  });

  test("false when key is false", () => {
    expect(
      isDesktopAutostartEnabled("X-GNOME-Autostart-enabled=false\n"),
    ).toBe(false);
  });

  test("true when key is true", () => {
    expect(
      isDesktopAutostartEnabled("X-GNOME-Autostart-enabled=true\n"),
    ).toBe(true);
  });
});

describe("desktopExecMatches", () => {
  test("matches current AppImage Exec", () => {
    const path = "/opt/Beam Link/app.AppImage";
    const content = buildDesktopFileText({
      name: "app",
      appImagePath: path,
    });
    expect(desktopExecMatches(content, path)).toBe(true);
  });

  test("rejects different Exec", () => {
    const content = buildDesktopFileText({
      name: "app",
      appImagePath: "/old/app.AppImage",
    });
    expect(desktopExecMatches(content, "/new/app.AppImage")).toBe(false);
  });

  test("parseDesktopExec reads Exec value", () => {
    expect(
      parseDesktopExec('Exec="/opt/app.AppImage" --hidden\n'),
    ).toBe('"/opt/app.AppImage" --hidden');
  });
});
