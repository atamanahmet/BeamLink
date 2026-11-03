import { Button, Stack } from "@mantine/core";
import { connectBanner } from "@beamlink/core";
import { UploadBanner } from "../send/UploadBanner.tsx";

export type ConnectBannerProps = {
  state: string | undefined;
  onOpen: () => void;
};

/**
 * Main page banner for Connect. Hidden when the device is approved.
 */
export function ConnectBanner({ state, onOpen }: ConnectBannerProps) {
  const banner = connectBanner(state);
  if (!banner) {
    return null;
  }
  return (
    <Stack gap="sm">
      <UploadBanner message={banner.text} kind="notice" />
      <Button onClick={onOpen} w="fit-content">
        {banner.button}
      </Button>
    </Stack>
  );
}
