export type ThemeSeeds = {
  primary: string;
  secondary: string;
  tertiary: string;
  neutral: string;
};

export type ThemeFile = {
  schemaVersion: 1;
  id: string;
  name: string;
  seeds: ThemeSeeds;
};

export type ThemeMode = "light" | "dark";

export type ThemeTokens = {
  background: string;
  surface: string;
  glass: string;
  "surface-raised": string;
  "text-primary": string;
  "text-secondary": string;
  "text-muted": string;
  border: string;
  "border-strong": string;
  ring: string;
  primary: string;
  "primary-foreground": string;
  "primary-subtle": string;
  "primary-text": string;
  secondary: string;
  "secondary-foreground": string;
  "secondary-subtle": string;
  "secondary-text": string;
  tertiary: string;
  "tertiary-foreground": string;
  "tertiary-subtle": string;
  "tertiary-text": string;
  success: string;
  "success-foreground": string;
  "success-subtle": string;
  "success-text": string;
  warning: string;
  "warning-foreground": string;
  "warning-subtle": string;
  "warning-text": string;
  danger: string;
  "danger-foreground": string;
  "danger-subtle": string;
  "danger-text": string;
  info: string;
  "info-foreground": string;
  "info-subtle": string;
  "info-text": string;
};
