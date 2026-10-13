import { app, BrowserWindow } from "electron";
import fs from "fs";
import os from "os";
import path from "path";
import { contrastProbeSource } from "./contrastProbe";

type CheckResult = { name: string; pass: boolean; detail: string };

type LoginPair = {
  name: string;
  ratio: number;
  minimum: number;
  pass: boolean;
  foreground: string;
  background: string;
};

/**
 * Waits for a number of milliseconds.
 */
function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Runs one boolean check in the renderer and returns PASS/FAIL detail.
 */
async function rendererCheck(
  win: BrowserWindow,
  name: string,
  expression: string,
): Promise<CheckResult> {
  try {
    const pass = Boolean(
      await win.webContents.executeJavaScript(expression),
    );
    return { name, pass, detail: pass ? "ok" : "check returned false" };
  } catch (err) {
    return { name, pass: false, detail: String(err) };
  }
}

/**
 * Measures navbar width, label visibility, and bottom-nav visibility.
 */
async function layoutSnapshot(win: BrowserWindow): Promise<{
  navbarWidth: number;
  labelsVisible: boolean;
  bottomVisible: boolean;
}> {
  return win.webContents.executeJavaScript(`
    (() => {
      const navbar = document.querySelector('[data-testid="navbar"]');
      const bottom = document.querySelector('[data-testid="bottom-nav"]');
      const labels = Array.from(document.querySelectorAll('[data-testid="nav-label"]'));
      const navRect = navbar ? navbar.getBoundingClientRect() : null;
      // Collapsed mobile navbar is translated off-screen; treat as width 0.
      const navbarOnScreen = !!navRect && navRect.right > 1 && navRect.width > 0;
      const navbarWidth = navbarOnScreen ? navRect.width : 0;
      const labelsVisible = labels.some((el) => {
        const style = window.getComputedStyle(el);
        const rect = el.getBoundingClientRect();
        return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
      });
      const bottomVisible = (() => {
        if (!bottom) return false;
        const style = window.getComputedStyle(bottom);
        const rect = bottom.getBoundingClientRect();
        return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
      })();
      return { navbarWidth, labelsVisible, bottomVisible };
    })()
  `);
}

/**
 * Resizes the window so the renderer inner width matches the target.
 */
async function resizeRenderer(
  win: BrowserWindow,
  width: number,
  height = 700,
): Promise<number> {
  win.setMinimumSize(Math.min(320, width), 400);
  for (let attempt = 0; attempt < 12; attempt += 1) {
    const [contentW, contentH] = win.getContentSize();
    const [winW, winH] = win.getSize();
    const frameW = Math.max(0, winW - contentW);
    const frameH = Math.max(0, winH - contentH);
    win.setSize(width + frameW, height + frameH);
    win.setContentSize(width, height);
    await sleep(250);
    const innerWidth: number = await win.webContents.executeJavaScript(
      "window.innerWidth",
    );
    if (Math.abs(innerWidth - width) <= 2) {
      return innerWidth;
    }
  }
  return win.webContents.executeJavaScript("window.innerWidth");
}

/**
 * Checks layout at one window width after resize settle time.
 */
async function checkLayout(
  win: BrowserWindow,
  width: number,
  expect: {
    navbarWidth: number;
    labelsVisible: boolean;
    bottomVisible: boolean;
  },
): Promise<CheckResult[]> {
  const innerWidth = await resizeRenderer(win, width);
  if (Math.abs(innerWidth - width) > 2) {
    return [
      {
        name: `layout-${width}-navbar-width`,
        pass: false,
        detail: `innerWidth expected ${width}, got ${innerWidth}`,
      },
      {
        name: `layout-${width}-labels`,
        pass: false,
        detail: `innerWidth expected ${width}, got ${innerWidth}`,
      },
      {
        name: `layout-${width}-bottom-nav`,
        pass: false,
        detail: `innerWidth expected ${width}, got ${innerWidth}`,
      },
    ];
  }
  const snap = await layoutSnapshot(win);
  const widthOk = Math.abs(snap.navbarWidth - expect.navbarWidth) <= 2;
  const name = `layout-${width}`;
  return [
    {
      name: `${name}-navbar-width`,
      pass: widthOk,
      detail: `expected ${expect.navbarWidth}, got ${snap.navbarWidth}`,
    },
    {
      name: `${name}-labels`,
      pass: snap.labelsVisible === expect.labelsVisible,
      detail: `expected ${expect.labelsVisible}, got ${snap.labelsVisible}`,
    },
    {
      name: `${name}-bottom-nav`,
      pass: snap.bottomVisible === expect.bottomVisible,
      detail: `expected ${expect.bottomVisible}, got ${snap.bottomVisible}`,
    },
  ];
}

/**
 * Clicks a theme page control by data-testid.
 */
async function clickTestId(win: BrowserWindow, testId: string): Promise<void> {
  await win.webContents.executeJavaScript(`
    (() => {
      const el = document.querySelector('[data-testid="${testId}"]');
      if (!el) throw new Error('missing ${testId}');
      el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    })()
  `);
  await sleep(700);
}

/**
 * Sets the theme NativeSelect value.
 */
async function setThemeSelect(
  win: BrowserWindow,
  themeId: string,
): Promise<void> {
  await win.webContents.executeJavaScript(`
    (() => {
      const el = document.querySelector('[data-testid="theme-select"]');
      if (!el) throw new Error('missing theme-select');
      const setter = Object.getOwnPropertyDescriptor(
        window.HTMLSelectElement.prototype,
        'value',
      ).set;
      setter.call(el, '${themeId}');
      el.dispatchEvent(new Event('change', { bubbles: true }));
    })()
  `);
  await sleep(700);
}

/**
 * Sets the ColorInput seed with a native value setter and input event.
 */
async function setSeedInput(win: BrowserWindow, seed: string): Promise<void> {
  await win.webContents.executeJavaScript(`
    (() => {
      const root = document.querySelector('[data-testid="seed-input"]');
      if (!root) throw new Error('missing seed-input');
      const input = root.querySelector('input') || root;
      const setter = Object.getOwnPropertyDescriptor(
        window.HTMLInputElement.prototype,
        'value',
      ).set;
      setter.call(input, '${seed}');
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    })()
  `);
  await sleep(900);
}

/**
 * Sets theme id, mode and seed on the theme page.
 */
async function prepareThemeCombo(
  win: BrowserWindow,
  themeId: string,
  mode: "light" | "dark",
  seed: string,
): Promise<void> {
  await win.webContents.executeJavaScript('location.hash = "#/theme"');
  await sleep(700);
  await clickTestId(win, mode === "light" ? "mode-light" : "mode-dark");
  await setThemeSelect(win, themeId);
  await setSeedInput(win, seed);
  await sleep(400);
}

/**
 * Opens login after setting theme id and color scheme on the theme page.
 */
async function prepareLoginCombo(
  win: BrowserWindow,
  themeId: string,
  mode: "light" | "dark",
  seed: string,
): Promise<void> {
  await prepareThemeCombo(win, themeId, mode, seed);
  await win.webContents.executeJavaScript('location.hash = "#/login"');
  await sleep(800);
  const errorVisible = await win.webContents.executeJavaScript(`
    !!document.querySelector('[data-testid="login-error"]')
  `);
  if (!errorVisible) {
    await clickTestId(win, "login-toggle-error");
  }
  await sleep(400);
}

/**
 * Measures theme Input borders and filled secondary/tertiary text ratios.
 */
async function themeBorderAndAccentChecks(
  win: BrowserWindow,
  prefix: string,
): Promise<CheckResult[]> {
  const payload = await win.webContents.executeJavaScript(`
    (() => {
      ${contrastProbeSource()}
      var surround = getComputedStyle(document.body).backgroundColor;
      var select = document.querySelector('[data-testid="theme-select"]');
      var seedRoot = document.querySelector('[data-testid="seed-input"]');
      var seedInput = seedRoot && (seedRoot.querySelector('input') || seedRoot);
      var secondary = document.querySelector('[data-testid="preview-filled-secondary"]');
      var tertiary = document.querySelector('[data-testid="preview-filled-tertiary"]');
      if (!select || !seedInput || !secondary || !tertiary) {
        throw new Error('theme border controls missing');
      }
      var selectStyle = getComputedStyle(select);
      var seedStyle = getComputedStyle(seedInput);
      var secondaryStyle = getComputedStyle(secondary);
      var tertiaryStyle = getComputedStyle(tertiary);
      return {
        selectBorder: measurePair(
          'native-select-border',
          selectStyle.borderTopColor,
          surround,
          3,
        ),
        colorInputBorder: measurePair(
          'color-input-border',
          seedStyle.borderTopColor,
          surround,
          3,
        ),
        secondary: measurePair(
          'filled-secondary-text',
          secondaryStyle.color,
          secondaryStyle.backgroundColor,
          4.5,
        ),
        tertiary: measurePair(
          'filled-tertiary-text',
          tertiaryStyle.color,
          tertiaryStyle.backgroundColor,
          4.5,
        ),
      };
    })()
  `);

  return [
    {
      name: `${prefix}-${payload.selectBorder.name}`,
      pass: payload.selectBorder.pass,
      detail: `ratio=${payload.selectBorder.ratio.toFixed(2)} min=3 fg=${payload.selectBorder.foreground} bg=${payload.selectBorder.background}`,
    },
    {
      name: `${prefix}-${payload.colorInputBorder.name}`,
      pass: payload.colorInputBorder.pass,
      detail: `ratio=${payload.colorInputBorder.ratio.toFixed(2)} min=3 fg=${payload.colorInputBorder.foreground} bg=${payload.colorInputBorder.background}`,
    },
    {
      name: `${prefix}-${payload.secondary.name}`,
      pass: payload.secondary.pass,
      detail: `ratio=${payload.secondary.ratio.toFixed(2)} min=4.5 fg=${payload.secondary.foreground} bg=${payload.secondary.background}`,
    },
    {
      name: `${prefix}-${payload.tertiary.name}`,
      pass: payload.tertiary.pass,
      detail: `ratio=${payload.tertiary.ratio.toFixed(2)} min=4.5 fg=${payload.tertiary.foreground} bg=${payload.tertiary.background}`,
    },
  ];
}

/**
 * Measures Login rendered contrast pairs for the current screen.
 */
async function loginContrastChecks(
  win: BrowserWindow,
  prefix: string,
): Promise<CheckResult[]> {
  const pairs: LoginPair[] = await win.webContents.executeJavaScript(`
    (() => {
      ${contrastProbeSource()}
      var card = document.querySelector('[data-testid="login-card"]');
      var username = document.querySelector('[data-testid="login-username-wrap"]');
      var error = document.querySelector('[data-testid="login-error"]');
      var link = document.querySelector('[data-testid="login-register-link"]');
      var submit = document.querySelector('[data-testid="login-submit"]');
      var label = document.querySelector('[data-testid="login-username-label"]');
      if (!card || !username || !error || !link || !submit || !label) {
        throw new Error('login controls missing');
      }
      var userInput = username.querySelector('input');
      if (!userInput) {
        throw new Error('login username input missing');
      }
      var cardBg = getComputedStyle(card).backgroundColor;
      var inputStyle = getComputedStyle(userInput);
      var placeholderColor = getComputedStyle(userInput, '::placeholder').color;
      var errorStyle = getComputedStyle(error);
      var errorText = error.querySelector('.mantine-Alert-message') || error;
      var errorTextStyle = getComputedStyle(errorText);
      var linkStyle = getComputedStyle(link);
      var submitStyle = getComputedStyle(submit);
      return [
        measurePair('label-on-card', getComputedStyle(label).color, cardBg, 4.5),
        measurePair('input-text-on-input', inputStyle.color, inputStyle.backgroundColor, 4.5),
        measurePair('placeholder-on-input', placeholderColor, inputStyle.backgroundColor, 4.5),
        measurePair('error-on-error-bg', errorTextStyle.color, errorStyle.backgroundColor, 4.5),
        measurePair('link-on-card', linkStyle.color, cardBg, 4.5),
        measurePair('input-border-on-card', inputStyle.borderTopColor, cardBg, 3),
        measurePair('submit-text-on-submit', submitStyle.color, submitStyle.backgroundColor, 4.5)
      ];
    })()
  `);

  return pairs.map((pair) => ({
    name: `${prefix}-${pair.name}`,
    pass: pair.pass,
    detail: `ratio=${pair.ratio.toFixed(2)} min=${pair.minimum} fg=${pair.foreground} bg=${pair.background}`,
  }));
}

/**
 * Measures role x variant rendered text and outline border ratios.
 */
async function roleVariantRenderedChecks(
  win: BrowserWindow,
  namePrefix: string,
): Promise<CheckResult[]> {
  const payload = await win.webContents.executeJavaScript(`
    (() => {
      ${contrastProbeSource()}
      var rows = Array.from(
        document.querySelectorAll('[data-testid^="contrast-row-rendered "]'),
      ).concat(
        Array.from(
          document.querySelectorAll('[data-testid^="contrast-row-rendered"]'),
        ),
      );
      // Prefer data-role rows for the matrix.
      var matrix = Array.from(
        document.querySelectorAll('[data-testid^="contrast-row-"][data-role]:not([data-role=""])'),
      ).map(function (row) {
        return {
          role: row.getAttribute('data-role'),
          variant: row.getAttribute('data-variant'),
          pass: row.getAttribute('data-pass') === 'true',
          ratio: Number(row.getAttribute('data-ratio') || '0'),
          name: row.getAttribute('data-testid'),
          fg: row.getAttribute('data-fg'),
          bg: row.getAttribute('data-bg'),
        };
      });
      var raisedToken = document
        .querySelector('[data-testid="token-surface-raised"]')
        ?.getAttribute('data-value');
      var modal = document.querySelector('[data-modal-content]')
        || document.querySelector('.mantine-Modal-content');
      var menu = document.querySelector('[data-testid="preview-menu-dropdown"]');
      var menuItem = document.querySelector('[data-testid="preview-menu-item"]');
      var modalBody = document.querySelector('[data-testid="preview-modal-body"]');
      var selectDropdown = document.querySelector('.preview-select-dropdown')
        || document.querySelector('.mantine-Select-dropdown')
        || document.querySelector('.mantine-Combobox-dropdown');
      var selectOption = selectDropdown
        ? selectDropdown.querySelector('[data-combobox-option]')
          || selectDropdown.querySelector('[role="option"]')
          || selectDropdown.querySelector('.mantine-Combobox-option')
        : null;
      function toHex(value) {
        var canvas = document.createElement('canvas');
        var ctx = canvas.getContext('2d');
        if (!ctx || !value) return '';
        ctx.fillStyle = '#000';
        ctx.fillStyle = value;
        return String(ctx.fillStyle);
      }
      var modalBg = modal ? toHex(getComputedStyle(modal).backgroundColor) : '';
      var menuBg = menu ? toHex(getComputedStyle(menu).backgroundColor) : '';
      var selectBg = selectDropdown
        ? toHex(getComputedStyle(selectDropdown).backgroundColor)
        : '';
      var raisedHex = raisedToken ? toHex(raisedToken) : '';
      var modalText = modalBody && modal
        ? measurePair(
            'modal-text',
            getComputedStyle(modalBody).color,
            getComputedStyle(modal).backgroundColor,
            4.5,
          )
        : null;
      var menuText = menuItem && menu
        ? measurePair(
            'menu-text',
            getComputedStyle(menuItem).color,
            getComputedStyle(menu).backgroundColor,
            4.5,
          )
        : null;
      var selectText = selectOption && selectDropdown
        ? measurePair(
            'select-text',
            getComputedStyle(selectOption).color,
            getComputedStyle(selectDropdown).backgroundColor,
            4.5,
          )
        : null;
      return {
        matrix: matrix,
        modalBg: modalBg,
        menuBg: menuBg,
        selectBg: selectBg,
        raisedHex: raisedHex,
        modalText: modalText,
        menuText: menuText,
        selectText: selectText,
      };
    })()
  `);

  const checks: CheckResult[] = [];
  const matrix = Array.isArray(payload.matrix) ? payload.matrix : [];
  for (const row of matrix) {
    const isBorder = String(row.variant) === "outline-border";
    const min = isBorder ? 3 : 4.5;
    checks.push({
      name: `${namePrefix}-role-${row.role}-${row.variant}`,
      pass: !!row.pass && Number(row.ratio) >= min,
      detail: `ratio=${Number(row.ratio).toFixed(3)} min=${min} fg=${row.fg} bg=${row.bg}`,
    });
  }
  checks.push({
    name: `${namePrefix}-modal-surface-raised`,
    pass:
      !!payload.modalBg &&
      !!payload.raisedHex &&
      payload.modalBg === payload.raisedHex,
    detail: `modal=${payload.modalBg} raised=${payload.raisedHex}`,
  });
  checks.push({
    name: `${namePrefix}-menu-surface-raised`,
    pass:
      !!payload.menuBg &&
      !!payload.raisedHex &&
      payload.menuBg === payload.raisedHex,
    detail: `menu=${payload.menuBg} raised=${payload.raisedHex}`,
  });
  checks.push({
    name: `${namePrefix}-select-surface-raised`,
    pass:
      !!payload.selectBg &&
      !!payload.raisedHex &&
      payload.selectBg === payload.raisedHex,
    detail: `select=${payload.selectBg} raised=${payload.raisedHex}`,
  });
  for (const key of ["modalText", "menuText", "selectText"] as const) {
    const pair = payload[key] as
      | { name: string; pass: boolean; ratio: number; foreground: string; background: string }
      | null;
    checks.push({
      name: `${namePrefix}-${key.replace("Text", "-text")}`,
      pass: !!pair && pair.pass,
      detail: pair
        ? `ratio=${pair.ratio.toFixed(3)} min=4.5 fg=${pair.foreground} bg=${pair.background}`
        : "missing",
    });
  }
  return checks;
}

/**
 * Collects theme page assertions for the current mode and theme.
 */
async function themeSnapshotChecks(
  win: BrowserWindow,
  namePrefix: string,
): Promise<CheckResult[]> {
  const payload = await win.webContents.executeJavaScript(`
    (() => {
      const tokenBg = document
        .querySelector('[data-testid="token-background"]')
        ?.getAttribute('data-value');
      const cssBg = getComputedStyle(document.documentElement)
        .getPropertyValue('--bl-background')
        .trim();
      const bodyBg = getComputedStyle(document.body).backgroundColor;
      const rootBg = getComputedStyle(document.documentElement).backgroundColor;
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      function toRgb(value) {
        if (!ctx) return value;
        ctx.fillStyle = '#000';
        ctx.fillStyle = value;
        const computed = ctx.fillStyle;
        return computed;
      }
      const tokenRgb = tokenBg ? toRgb(tokenBg) : '';
      const cssRgb = cssBg ? toRgb(cssBg) : '';
      const bodyRgb = toRgb(bodyBg);
      const rootRgb = toRgb(rootBg);
      const rendered = Array.from(
        document.querySelectorAll('[data-testid^="contrast-row-rendered"]'),
      ).map((row) => ({
        name: row.getAttribute('data-testid'),
        pass: row.getAttribute('data-pass') === 'true',
        fg: row.getAttribute('data-fg'),
        bg: row.getAttribute('data-bg'),
      }));
      const allRows = Array.from(
        document.querySelectorAll('[data-testid^="contrast-row-"]'),
      ).map((row) => ({
        name: row.getAttribute('data-testid'),
        pass: row.getAttribute('data-pass') === 'true',
        text: row.innerText,
      }));
      const primary = document
        .querySelector('[data-testid="token-primary"]')
        ?.getAttribute('data-value');
      const cssPrimary = getComputedStyle(document.documentElement)
        .getPropertyValue('--bl-primary')
        .trim();
      const swatches = Array.from(
        document.querySelectorAll('[data-testid^="brand-swatch-"]'),
      ).map((el) => el.getAttribute('data-color'));
      return {
        tokenBg,
        cssBg,
        tokenRgb,
        cssRgb,
        bodyRgb,
        rootRgb,
        rendered,
        allRows,
        primary,
        cssPrimary,
        swatches,
        scheme: document.documentElement.getAttribute('data-mantine-color-scheme'),
        themeSelect: document.querySelector('[data-testid="theme-select"]')?.value,
        timeOrigin: performance.timeOrigin,
      };
    })()
  `);

  const bgMatches =
    !!payload.tokenBg &&
    payload.tokenBg.toLowerCase() === String(payload.cssBg).toLowerCase();
  const bodyMatches =
    payload.tokenRgb === payload.bodyRgb || payload.tokenRgb === payload.rootRgb;
  const renderedPass =
    Array.isArray(payload.rendered) &&
    payload.rendered.length >= 3 &&
    payload.rendered.every((row: { pass: boolean }) => row.pass);

  const roleChecks = await roleVariantRenderedChecks(win, namePrefix);

  return [
    {
      name: `${namePrefix}-bl-background`,
      pass: bgMatches,
      detail: `token=${payload.tokenBg} css=${payload.cssBg}`,
    },
    {
      name: `${namePrefix}-body-background`,
      pass: bodyMatches,
      detail: `tokenRgb=${payload.tokenRgb} body=${payload.bodyRgb} root=${payload.rootRgb}`,
    },
    {
      name: `${namePrefix}-rendered-filled-text`,
      pass: renderedPass,
      detail: JSON.stringify(payload.rendered),
    },
    ...roleChecks,
  ];
}

/**
 * Runs spike self tests and writes one PASS/FAIL line per check.
 */
export async function runSelfTest(win: BrowserWindow): Promise<void> {
  const outPath = path.join(os.tmpdir(), "spike-selftest.txt");
  const lines: string[] = [];
  let mainFrameNav = false;
  const onNavigate = () => {
    mainFrameNav = true;
  };
  win.webContents.on("did-navigate", onNavigate);

  try {
    win.show();
    win.focus();
    win.webContents.setZoomFactor(1);
    await win.webContents.executeJavaScript('location.hash = "#/"');
    await sleep(1000);

    const styleChecks = [
      await rendererCheck(
        win,
        "mantine-probe-bg",
        `(() => {
          const el = document.querySelector('[data-testid="mantine-probe"]');
          if (!el) return false;
          const bg = window.getComputedStyle(el).backgroundColor;
          return !!bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent';
        })()`,
      ),
      await rendererCheck(
        win,
        "tailwind-probe-padding",
        `(() => {
          const el = document.querySelector('[data-testid="tailwind-probe"]');
          if (!el) return false;
          return window.getComputedStyle(el).paddingTop === '32px';
        })()`,
      ),
      await rendererCheck(
        win,
        "override-probe-radius",
        `(() => {
          const el = document.querySelector('[data-testid="override-probe"]');
          if (!el) return false;
          return window.getComputedStyle(el).borderRadius === '0px';
        })()`,
      ),
    ];

    const layoutChecks = [
      ...(await checkLayout(win, 1100, {
        navbarWidth: 220,
        labelsVisible: true,
        bottomVisible: false,
      })),
      ...(await checkLayout(win, 700, {
        navbarWidth: 64,
        labelsVisible: false,
        bottomVisible: false,
      })),
      ...(await checkLayout(win, 400, {
        navbarWidth: 0,
        labelsVisible: false,
        bottomVisible: true,
      })),
    ];

    await win.webContents.executeJavaScript('location.hash = "#/lazy"');
    await sleep(1500);
    const lazyText: string = await win.webContents.executeJavaScript(
      "document.body.innerText",
    );
    const lazyCheck: CheckResult = {
      name: "lazy-route",
      pass: lazyText.includes("lazy route loaded"),
      detail: lazyText.slice(0, 120),
    };

    await win.webContents.executeJavaScript('location.hash = "#/drop"');
    await sleep(1500);
    const dropPresent = await win.webContents.executeJavaScript(`
      (() => {
        const plain = document.querySelector('[data-testid="drop-plain-area"]');
        const mantine = document.querySelector('[data-testid="drop-mantine-area"]');
        return { plain: !!plain, mantine: !!mantine };
      })()
    `);
    const dropCheck: CheckResult = {
      name: "drop-route-areas",
      pass: !!dropPresent.plain && !!dropPresent.mantine,
      detail: JSON.stringify(dropPresent),
    };

    await resizeRenderer(win, 1100);
    await win.webContents.executeJavaScript('location.hash = "#/theme"');
    await sleep(1000);

    const timeOriginBefore: number = await win.webContents.executeJavaScript(
      "performance.timeOrigin",
    );
    mainFrameNav = false;

    await clickTestId(win, "mode-light");
    await setThemeSelect(win, "beam");
    await setSeedInput(win, "#2563EB");
    const beamLight = await themeSnapshotChecks(win, "theme-beam-light");

    await clickTestId(win, "mode-dark");
    const beamDark = await themeSnapshotChecks(win, "theme-beam-dark");

    const primaryBefore: string = await win.webContents.executeJavaScript(`
      getComputedStyle(document.documentElement)
        .getPropertyValue('--bl-primary')
        .trim()
    `);
    await setThemeSelect(win, "ember");
    await setSeedInput(win, "#EA580C");
    await clickTestId(win, "mode-light");
    const emberLight = await themeSnapshotChecks(win, "theme-ember-light");
    const emberPrimaryLight = await win.webContents.executeJavaScript(`
      (() => {
        const token = document
          .querySelector('[data-testid="token-primary"]')
          ?.getAttribute('data-value');
        const css = getComputedStyle(document.documentElement)
          .getPropertyValue('--bl-primary')
          .trim();
        return { token, css, before: ${JSON.stringify(primaryBefore)} };
      })()
    `);
    const emberPrimaryChanged: CheckResult = {
      name: "theme-ember-primary-changed",
      pass:
        !!emberPrimaryLight.token &&
        emberPrimaryLight.token === emberPrimaryLight.css &&
        emberPrimaryLight.css !== emberPrimaryLight.before,
      detail: JSON.stringify(emberPrimaryLight),
    };

    await clickTestId(win, "mode-dark");
    const emberDark = await themeSnapshotChecks(win, "theme-ember-dark");

    const swatchesBefore: string[] = await win.webContents.executeJavaScript(`
      Array.from(document.querySelectorAll('[data-testid^="brand-swatch-"]'))
        .map((el) => el.getAttribute('data-color'))
    `);
    await setSeedInput(win, "#FFFF99");
    const palePayload = await win.webContents.executeJavaScript(`
      (() => {
        const swatches = Array.from(
          document.querySelectorAll('[data-testid^="brand-swatch-"]'),
        ).map((el) => el.getAttribute('data-color'));
        const rows = Array.from(
          document.querySelectorAll('[data-testid^="contrast-row-"]'),
        ).map((row) => ({
          name: row.getAttribute('data-testid'),
          pass: row.getAttribute('data-pass') === 'true',
          text: row.innerText,
        }));
        return { swatches, rows };
      })()
    `);
    const swatchesChanged: CheckResult = {
      name: "theme-pale-swatches-changed",
      pass:
        Array.isArray(palePayload.swatches) &&
        palePayload.swatches.length === 10 &&
        palePayload.swatches.join(",") !== swatchesBefore.join(","),
      detail: `before=${swatchesBefore.join("|")} after=${palePayload.swatches.join("|")}`,
    };
    const paleRowsPass: CheckResult = {
      name: "theme-pale-contrast-all-pass",
      pass:
        Array.isArray(palePayload.rows) &&
        palePayload.rows.length > 0 &&
        palePayload.rows.every((row: { pass: boolean }) => row.pass),
      detail: JSON.stringify(
        (palePayload.rows || []).filter((row: { pass: boolean }) => !row.pass),
      ),
    };

    const timeOriginAfter: number = await win.webContents.executeJavaScript(
      "performance.timeOrigin",
    );
    const noReload: CheckResult = {
      name: "theme-no-reload",
      pass: timeOriginAfter === timeOriginBefore && !mainFrameNav,
      detail: `timeOriginBefore=${timeOriginBefore} after=${timeOriginAfter} mainFrameNav=${mainFrameNav}`,
    };

    win.webContents.removeListener("did-navigate", onNavigate);

    const saved = await win.webContents.executeJavaScript(`
      (() => {
        return {
          scheme: localStorage.getItem('mantine-color-scheme-value'),
          theme: localStorage.getItem('beamlink-theme'),
          bg: localStorage.getItem('beamlink-theme-bg'),
        };
      })()
    `);

    await new Promise<void>((resolve) => {
      win.webContents.once("did-finish-load", () => resolve());
      win.webContents.reload();
    });
    await sleep(1200);
    await win.webContents.executeJavaScript('location.hash = "#/theme"');
    await sleep(800);

    const restored = await win.webContents.executeJavaScript(`
      (() => {
        const scheme = localStorage.getItem('mantine-color-scheme-value');
        const theme = localStorage.getItem('beamlink-theme');
        const bgRaw = localStorage.getItem('beamlink-theme-bg');
        const bg = bgRaw ? JSON.parse(bgRaw) : null;
        const applied = document.documentElement.getAttribute('data-bg-applied');
        const attrScheme = document.documentElement.getAttribute(
          'data-mantine-color-scheme',
        );
        const expected =
          scheme === 'light' || scheme === 'dark'
            ? scheme
            : window.matchMedia('(prefers-color-scheme: dark)').matches
              ? 'dark'
              : 'light';
        const expectedBg = bg ? bg[expected] : null;
        const select = document.querySelector('[data-testid="theme-select"]');
        const seedRoot = document.querySelector('[data-testid="seed-input"]');
        const seedInput = seedRoot && (seedRoot.querySelector('input') || seedRoot);
        return {
          scheme,
          theme,
          applied,
          expectedBg,
          attrScheme,
          selectValue: select ? select.value : null,
          seedValue: seedInput ? seedInput.value : null,
        };
      })()
    `);

    const restoreCheck: CheckResult = {
      name: "theme-restore-after-reload",
      pass:
        restored.scheme === saved.scheme &&
        restored.theme === saved.theme &&
        !!restored.applied &&
        !!restored.expectedBg &&
        String(restored.applied).toLowerCase() ===
          String(restored.expectedBg).toLowerCase() &&
        restored.selectValue === "ember" &&
        String(restored.seedValue).toLowerCase() === "#ffff99",
      detail: JSON.stringify({ saved, restored }),
    };

    const loginChecks: CheckResult[] = [];
    try {
      const combos: Array<{
        themeId: string;
        mode: "light" | "dark";
        seed: string;
        prefix: string;
      }> = [
        {
          themeId: "beam",
          mode: "light",
          seed: "#2563EB",
          prefix: "login-beam-light",
        },
        {
          themeId: "beam",
          mode: "dark",
          seed: "#2563EB",
          prefix: "login-beam-dark",
        },
        {
          themeId: "ember",
          mode: "light",
          seed: "#EA580C",
          prefix: "login-ember-light",
        },
        {
          themeId: "ember",
          mode: "dark",
          seed: "#EA580C",
          prefix: "login-ember-dark",
        },
      ];
      for (const combo of combos) {
        await prepareThemeCombo(win, combo.themeId, combo.mode, combo.seed);
        loginChecks.push(
          ...(await themeBorderAndAccentChecks(win, combo.prefix)),
        );
        await prepareLoginCombo(win, combo.themeId, combo.mode, combo.seed);
        loginChecks.push(...(await loginContrastChecks(win, combo.prefix)));
      }
    } catch (err) {
      loginChecks.push({
        name: "login-contrast",
        pass: false,
        detail: String(err),
      });
    }

    const all = [
      ...styleChecks,
      ...layoutChecks,
      lazyCheck,
      dropCheck,
      ...beamLight,
      ...beamDark,
      emberPrimaryChanged,
      ...emberLight,
      ...emberDark,
      swatchesChanged,
      paleRowsPass,
      noReload,
      restoreCheck,
      ...loginChecks,
    ];
    for (const check of all) {
      lines.push(
        `${check.pass ? "PASS" : "FAIL"} ${check.name} ${check.detail}`,
      );
    }

    fs.writeFileSync(outPath, lines.join("\n") + "\n", "utf-8");
    app.exit(all.every((c) => c.pass) ? 0 : 1);
  } catch (err) {
    lines.push(`FAIL selftest ${String(err)}`);
    fs.writeFileSync(outPath, lines.join("\n") + "\n", "utf-8");
    app.exit(1);
  }
}
