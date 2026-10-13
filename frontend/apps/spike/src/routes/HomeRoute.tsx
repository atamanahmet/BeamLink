import { Button } from "@mantine/core";
import { Link } from "react-router";

/**
 * Home page with Mantine/Tailwind coexistence probes.
 */
export function HomeRoute() {
  return (
    <>
      <div>home route</div>
      <Link to="/lazy">Go to lazy</Link>
      <div>
        <Button data-testid="mantine-probe">Mantine</Button>
      </div>
      <div data-testid="tailwind-probe" className="p-8">
        Tailwind padding probe
      </div>
      <div>
        <Button data-testid="override-probe" className="rounded-none">
          Override
        </Button>
      </div>
    </>
  );
}
