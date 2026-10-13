import {
  ActionIcon,
  Badge,
  Button,
  ColorInput,
  Group,
  Menu,
  Modal,
  NativeSelect,
  Paper,
  SegmentedControl,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
  useComputedColorScheme,
  useMantineColorScheme,
} from "@mantine/core";
import { Star } from "lucide-react";
import { useLayoutEffect, useMemo, useState } from "react";
import {
  checkTokens,
  CONTRAST_RULES,
  generateScale,
  generateTokens,
  loadThemes,
  resolveThemeSeeds,
  useThemeSettings,
  type ThemeTokens,
} from "@beamlink/ui";
import {
  measureRendered,
  measureRenderedBorder,
  type RenderedContrast,
} from "@beamlink/ui/testing";

const TOKEN_TO_BL = {
  background: "--bl-background",
  surface: "--bl-surface",
  "text-primary": "--bl-text-primary",
  "text-secondary": "--bl-text-secondary",
  "border-strong": "--bl-border-strong",
  primary: "--bl-primary",
  "primary-foreground": "--bl-primary-foreground",
  secondary: "--bl-secondary",
  "secondary-foreground": "--bl-secondary-foreground",
  tertiary: "--bl-tertiary",
  "tertiary-foreground": "--bl-tertiary-foreground",
} as Record<keyof ThemeTokens, string>;

const ROLE_PREVIEWS = [
  { role: "primary", color: undefined },
  { role: "secondary", color: "secondary" },
  { role: "tertiary", color: "tertiary" },
  { role: "success", color: "success" },
  { role: "warning", color: "warning" },
  { role: "danger", color: "danger" },
  { role: "info", color: "info" },
] as const;

const VARIANTS = ["filled", "light", "outline"] as const;

/**
 * Reads a CSS variable from the document element.
 */
function readCssVar(name: string): string {
  return getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
}

/**
 * Theme lab page for mode, theme, seed, preview and contrast checks.
 */
export function ThemeRoute() {
  const themes = useMemo(() => loadThemes(), []);
  const { themeId, setThemeId, primarySeed, setPrimarySeed } =
    useThemeSettings();
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const computedScheme = useComputedColorScheme("light");
  const seeds = useMemo(
    () => resolveThemeSeeds(themes, { themeId, primarySeed }),
    [themes, themeId, primarySeed],
  );
  const tokens = useMemo(
    () => generateTokens(seeds, computedScheme),
    [seeds, computedScheme],
  );
  const brandScale = useMemo(
    () => generateScale(seeds.primary),
    [seeds.primary],
  );
  const tokenChecks = useMemo(() => checkTokens(tokens), [tokens]);
  type RenderedRow = RenderedContrast & {
    role?: string;
    variant?: string;
  };
  const [renderedRows, setRenderedRows] = useState<RenderedRow[]>([]);
  const [modalOpen, setModalOpen] = useState(true);

  useLayoutEffect(() => {
    const handle = window.setTimeout(() => {
      const rows: RenderedRow[] = [
        measureRendered(
          "preview-filled-button",
          "rendered filled Button text",
        ),
        measureRendered(
          "preview-filled-badge",
          "rendered filled Badge text",
        ),
        measureRendered(
          "preview-filled-action-icon",
          "rendered filled ActionIcon text",
        ),
      ].filter((row): row is RenderedContrast => row !== null);

      for (const { role } of ROLE_PREVIEWS) {
        for (const variant of VARIANTS) {
          const testId = `preview-role-${role}-${variant}`;
          const text = measureRendered(
            testId,
            `rendered ${role} ${variant} text`,
          );
          if (text) {
            rows.push({ ...text, role, variant });
          }
          if (variant === "outline") {
            const border = measureRenderedBorder(
              testId,
              `rendered ${role} outline border`,
              3,
            );
            if (border) {
              rows.push({ ...border, role, variant: "outline-border" });
            }
          }
        }
      }

      setRenderedRows(rows);
    }, 0);
    return () => window.clearTimeout(handle);
  }, [tokens, computedScheme, themeId, primarySeed, colorScheme, modalOpen]);

  return (
    <Stack gap="md">
      <Title order={2}>Theme</Title>

      <SegmentedControl
        value={colorScheme}
        onChange={(value) =>
          setColorScheme(value as "light" | "dark" | "auto")
        }
        data={[
          {
            value: "light",
            label: <span data-testid="mode-light">Light</span>,
          },
          {
            value: "dark",
            label: <span data-testid="mode-dark">Dark</span>,
          },
          {
            value: "auto",
            label: <span data-testid="mode-system">System</span>,
          },
        ]}
      />

      <NativeSelect
        data-testid="theme-select"
        label="Theme"
        value={themeId}
        onChange={(event) => setThemeId(event.currentTarget.value)}
        data={themes.map((theme) => ({
          value: theme.id,
          label: theme.name,
        }))}
      />

      <ColorInput
        data-testid="seed-input"
        label="Primary seed"
        value={primarySeed ?? seeds.primary}
        onChange={setPrimarySeed}
        format="hex"
      />

      <Group gap="sm" align="center">
        <Button data-testid="preview-filled-button" variant="filled">
          Filled
        </Button>
        <Button
          data-testid="preview-filled-secondary"
          color="secondary"
          variant="filled"
        >
          Secondary
        </Button>
        <Button
          data-testid="preview-filled-tertiary"
          color="tertiary"
          variant="filled"
        >
          Tertiary
        </Button>
        <Badge data-testid="preview-filled-badge" variant="filled">
          Badge
        </Badge>
        <ActionIcon
          data-testid="preview-filled-action-icon"
          variant="filled"
          aria-label="Filled action"
        >
          <Star size={16} />
        </ActionIcon>
        <Button variant="light">Light</Button>
        <Button variant="outline">Outline</Button>
        <TextInput placeholder="Text input" />
        <Paper withBorder p="sm">
          Paper
        </Paper>
        <Menu opened onChange={() => undefined} shadow="md" width={160}>
          <Menu.Target>
            <Button data-testid="preview-menu-target">Menu</Button>
          </Menu.Target>
          <Menu.Dropdown data-testid="preview-menu-dropdown">
            <Menu.Item data-testid="preview-menu-item">Raised item</Menu.Item>
          </Menu.Dropdown>
        </Menu>
        <Select
          data-testid="preview-select"
          label="Raised select"
          data={["Alpha", "Beta"]}
          defaultValue="Alpha"
          dropdownOpened
          onDropdownClose={() => undefined}
          comboboxProps={{ withinPortal: false }}
          classNames={{ dropdown: "preview-select-dropdown" }}
        />
      </Group>

      <Stack gap="xs" data-testid="role-variant-previews">
        {ROLE_PREVIEWS.map(({ role, color }) => (
          <Group key={role} gap="sm">
            {VARIANTS.map((variant) => (
              <Button
                key={variant}
                data-testid={`preview-role-${role}-${variant}`}
                data-role={role}
                data-variant={variant}
                color={color}
                variant={variant}
              >
                {role} {variant}
              </Button>
            ))}
          </Group>
        ))}
      </Stack>

      <Modal
        opened={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Raised modal"
        data-testid="preview-modal"
      >
        <Text data-testid="preview-modal-body">Modal on surface-raised</Text>
      </Modal>

      <Group gap={4} data-testid="brand-swatches">
        {brandScale.map((hex, index) => (
          <div
            key={`${hex}-${index}`}
            data-testid={`brand-swatch-${index}`}
            data-color={hex}
            style={{
              width: 28,
              height: 28,
              backgroundColor: hex,
              borderRadius: 4,
            }}
          />
        ))}
      </Group>

      <Text
        size="sm"
        data-testid="token-background"
        data-value={tokens.background}
      >
        background {tokens.background}
      </Text>
      <Text size="sm" data-testid="token-primary" data-value={tokens.primary}>
        primary {tokens.primary}
      </Text>
      <Text
        size="sm"
        data-testid="token-surface-raised"
        data-value={tokens["surface-raised"]}
      >
        surface-raised {tokens["surface-raised"]}
      </Text>

      <Table data-testid="contrast-table" withTableBorder>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Rule</Table.Th>
            <Table.Th>Ratio</Table.Th>
            <Table.Th>Minimum</Table.Th>
            <Table.Th>Result</Table.Th>
            <Table.Th>DOM</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {CONTRAST_RULES.map((rule) => {
            const check = tokenChecks.find((item) => item.name === rule.name);
            if (!check) {
              return null;
            }
            const fgVar = readCssVar(TOKEN_TO_BL[rule.foreground]);
            const bgVar = readCssVar(TOKEN_TO_BL[rule.background]);
            const mismatch =
              fgVar.toLowerCase() !== tokens[rule.foreground].toLowerCase() ||
              bgVar.toLowerCase() !== tokens[rule.background].toLowerCase();
            return (
              <Table.Tr
                key={rule.name}
                data-testid={`contrast-row-${rule.name}`}
                data-pass={check.pass ? "true" : "false"}
              >
                <Table.Td>{rule.name}</Table.Td>
                <Table.Td>{check.ratio.toFixed(2)}</Table.Td>
                <Table.Td>{check.minimum}</Table.Td>
                <Table.Td>{check.pass ? "PASS" : "FAIL"}</Table.Td>
                <Table.Td>{mismatch ? "MISMATCH" : "ok"}</Table.Td>
              </Table.Tr>
            );
          })}
          {renderedRows.map((row) => (
            <Table.Tr
              key={row.name}
              data-testid={`contrast-row-${row.name}`}
              data-pass={row.pass ? "true" : "false"}
              data-fg={row.foreground}
              data-bg={row.background}
              data-ratio={row.ratio.toFixed(3)}
              data-role={row.role ?? ""}
              data-variant={row.variant ?? ""}
            >
              <Table.Td>{row.name}</Table.Td>
              <Table.Td>{row.ratio.toFixed(2)}</Table.Td>
              <Table.Td>{row.minimum}</Table.Td>
              <Table.Td>{row.pass ? "PASS" : "FAIL"}</Table.Td>
              <Table.Td>rendered</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Stack>
  );
}
