import { create } from "zustand";
import type { AgentStatus } from "../api/agentApi";

type AgentStatusState = {
  agentStatus: AgentStatus | null;
  setAgentStatus: (status: AgentStatus | null) => void;
};

/**
 * Live agent status from /status and SSE.
 */
export const useAgentStatusStore = create<AgentStatusState>((set) => ({
  agentStatus: null,
  setAgentStatus: (agentStatus) => set({ agentStatus }),
}));
