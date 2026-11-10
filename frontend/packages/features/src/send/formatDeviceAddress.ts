/**
 * Formats a device LAN address as ip with tls port, else http port.
 */
export function formatDeviceAddress(
  ip: string,
  port: number,
  tlsPort?: number | null,
): string {
  return `${ip}:${tlsPort ?? port}`;
}
