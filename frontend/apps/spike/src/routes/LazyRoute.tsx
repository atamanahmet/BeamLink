import { Link } from "react-router";

export function Component() {
  return (
    <>
      <div>lazy route loaded</div>
      <Link to="/">Back home</Link>
    </>
  );
}
