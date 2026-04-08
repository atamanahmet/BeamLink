# Beamlink - Local File Sharing with Server-Managed Peers

Beamlink is an agent-based network for sending files locally. Agents communicate directly and Nexus coordinates the network.

## Prerequisites

- **Java 17+** - [Eclipse Temurin](https://adoptium.net/)

## How it works

**Agent**

- Runs with launcher.bat

- Gets two UUIDs on registration, one for internal routing and one public-facing to keep internal structure hidden

- Stores auth token locally (file-based, no db)

- Transfers files directly to other agents and nexus over HTTP

- Keeps logs locally and syncs to Nexus when online

- Works offline using last known peer list

- Agent UI uses HTTP-only JWT cookie

**Nexus**

- Runs with launcher.bat

- Tracks agents, peer lists, logs, and push updates

- Receives files like agents

- Admin UI uses HTTP-only JWT cookie

## Installation

**Agent**

- Install [Java 17+](https://adoptium.net/)

- Download and extract **beamlink-agent-0.0.1-pre1.zip**

- Run **launcher.bat**

- Open Agent UI in browser

**Nexus**

- Install [Java 17+](https://adoptium.net/)

- Download and extract **beamlink-nexus-0.0.1-pre1.zip**

- Run **launcher.bat**

- Open Nexus UI in browser

## Configuration

**Nexus**

- Copy `.env.example` to `.env` and fill in your values before running.
- Additional config in `config/application.yaml`
- `NEXUS_IP` - leave `auto` to detect local IP on first launch
- `NEXUS_PORT` - default is `7472`, changing it is recommended
- `NEXUS_JWT_SECRET` - leave `auto` to generate on first launch
- `NEXUS_ADMIN_USERNAME` / `NEXUS_ADMIN_PASSWORD` - Admin UI credentials
- `DB_USERNAME` / `DB_PASSWORD` - PostgreSQL credentials
- `MULTIPART_MAX_FILE_SIZE` - max upload size in MB, `-1` for unlimited

**Agent**

- Configured via `config/application.yaml`
- `server.port` - port that Agent runs on
- `agent.nexus.url` - set to your Nexus IP and port
- `agent.name` - display name on the network
- `agent.ui.username` / `password` - Agent UI credentials
- `agent.ui.jwt-secret` - leave `auto` to generate

## Notes

- Early version (v0.0.1) - expect bugs.

- Windows-only for now

- Built for local networks

- File transfers go directly between agents over HTTP. Backend-proxied transfers not yet implemented

- HTTPS recommended if exposed to outside of LAN
