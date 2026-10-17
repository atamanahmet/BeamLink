import {
  Button,
  Group,
  Loader,
  Paper,
  Select,
  Stack,
  Text,
} from "@mantine/core";
import { Shield } from "lucide-react";
import type { ResumePolicyState } from "./types.ts";
import { StatusMessage } from "./StatusMessage.tsx";

type ResumePolicySectionProps = {
  resumePolicy: ResumePolicyState;
  onChangeValue: (value: "OWNER_ONLY" | "ANYONE") => void;
  onSave: () => void;
};

/**
 * Who may resume a paused transfer.
 */
export function ResumePolicySection({
  resumePolicy,
  onChangeValue,
  onSave,
}: ResumePolicySectionProps) {
  return (
    <Paper
      withBorder
      p="lg"
      radius="md"
      style={{
        backgroundColor: "var(--bl-surface)",
        borderColor: "var(--bl-border)",
      }}
    >
      <Stack gap="md">
        <Group gap="xs">
          <Shield size={16} color="var(--bl-primary-text)" />
          <Text
            size="sm"
            fw={600}
            tt="uppercase"
            c="var(--bl-primary-text)"
            style={{ letterSpacing: "0.08em" }}
          >
            Resume Policy
          </Text>
        </Group>

        <Text size="xs" c="var(--bl-text-muted)">
          Owner only lets the side that paused resume. Anyone lets either side
          resume.
        </Text>

        <Group gap="xs">
          <Select
            value={resumePolicy.value}
            onChange={(val) => {
              if (val === "OWNER_ONLY" || val === "ANYONE") onChangeValue(val);
            }}
            data={[
              { value: "OWNER_ONLY", label: "Owner only" },
              { value: "ANYONE", label: "Anyone" },
            ]}
            w={220}
            allowDeselect={false}
          />
          <Button
            onClick={onSave}
            disabled={resumePolicy.save === "saving"}
            leftSection={
              resumePolicy.save === "saving" ? (
                <Loader size={16} color="white" type="dots" />
              ) : undefined
            }
          >
            {resumePolicy.save === "saving" ? "Saving..." : "Save"}
          </Button>
        </Group>

        {resumePolicy.save === "success" ? (
          <StatusMessage type="success" message="Resume policy updated." />
        ) : null}
        {resumePolicy.error ? (
          <StatusMessage type="error" message={resumePolicy.error} />
        ) : null}
      </Stack>
    </Paper>
  );
}
