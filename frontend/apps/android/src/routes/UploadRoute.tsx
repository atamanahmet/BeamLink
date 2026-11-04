import { useNavigate } from "react-router";
import { FileUpload } from "../components/FileUpload";

export function Component() {
  const navigate = useNavigate();
  return <FileUpload onTransferStarted={() => navigate("/transfers")} />;
}
