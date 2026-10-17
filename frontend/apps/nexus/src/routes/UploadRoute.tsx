import { useNavigate } from "react-router";
import { FileUpload } from "../components/FileUpload";

/**
 * Upload tab with navigate to transfers after start.
 */
export function Component() {
  const navigate = useNavigate();
  return (
    <FileUpload onTransferStarted={() => navigate("/transfers")} />
  );
}
