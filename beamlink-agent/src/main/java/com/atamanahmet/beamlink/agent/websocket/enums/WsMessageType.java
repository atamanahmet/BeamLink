package com.atamanahmet.beamlink.agent.websocket.enums;

public enum WsMessageType {

    /**
     * Nexus to Agent
     */
    APPROVAL_PUSH,
    PEER_LIST_UPDATE,
    AGENT_JOINED,
    AGENT_OFFLINE,
    RENAME_PUSH,

    /**
     * Agent to Nexus
     */
    HEARTBEAT,
    LOG_SYNC,
    STATUS_UPDATE,

    /**
     * Agent to Agent
     */
    PING,
    PONG;

}