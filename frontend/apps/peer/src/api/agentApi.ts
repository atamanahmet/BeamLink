import { apiClient } from "./client";

export type AgentStatus = {
  agentId: string | null;
  name: string;
  nexusUrl: string;
  status: string;
  uploadDirectory: string;
  port: number;
  fileCount: number;
};

/**
 * Loads agent status for the info screen.
 */
export async function getAgentStatus(): Promise<AgentStatus> {
  return (await apiClient.get<AgentStatus>("/status")).data;
}

/**
 * Asks nexus to rename this agent.
 */
export async function requestRename(
  agentId: string,
  newName: string,
): Promise<void> {
  await apiClient.post(`/${agentId}/rename`, { name: newName });
}
