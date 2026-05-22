# =============================================================================
# Beamlink - Build & Dev Orchestration
# =============================================================================
#
# build pipeline:  setup-jre -> Maven -> copy JAR + .env -> npm run dist -> copy installer
# dev pipeline:    setup-jre -> Maven -> copy JAR + .env -> npm run dev
#
# Usage:
#   make help           Show this help
#   make setup-jre      Download JRE for current OS - runs automatically, call manually to refresh
#
#   make build          Production build for both modules
#   make build-nexus    Production build for Beamlink Nexus only
#   make build-agent    Production build for Beamlink Agent only
#
#   make dev-nexus                Run Nexus in dev mode (full Maven recompile)
#   make dev-nexus-skip-maven     Run Nexus in dev mode (skip Maven, reuse existing JAR)
#   make dev-agent                Run Agent in dev mode (full Maven recompile)
#   make dev-agent-skip-maven     Run Agent in dev mode (skip Maven, reuse existing JAR)
#
# Requirements:
#   - mvn, java, node, npm, curl on PATH
#   - tar (Mac/Linux) or unzip (Windows via Git Bash)
# =============================================================================

# -- Paths --------------------------------------------------------------------
ROOT_DIR        := $(shell pwd)

NEXUS_JAVA_DIR  := $(ROOT_DIR)/beamlink-nexus
AGENT_JAVA_DIR  := $(ROOT_DIR)/beamlink-agent

NEXUS_UI_DIR    := $(ROOT_DIR)/frontend/nexus
AGENT_UI_DIR    := $(ROOT_DIR)/frontend/agent

# Shared runtime resources - gitignored, populated by this Makefile
JRE_DIR         := $(ROOT_DIR)/resources/jre
NEXUS_BACKEND   := $(ROOT_DIR)/resources/nexus/backend
NEXUS_CONFIG    := $(ROOT_DIR)/resources/nexus/config
AGENT_BACKEND   := $(ROOT_DIR)/resources/agent/backend
AGENT_CONFIG    := $(ROOT_DIR)/resources/agent/config

# -- JRE ----------------------------------------------------------------------
# JRE 21 over 17: same bytecode compatibility, better GC and startup, longer support window
JRE_VERSION     := 21

# -- JRE Version Check --------------------------------------------------------
# Reads major version from existing JRE binary. Returns 0 if JRE doesn't exist.
INSTALLED_JRE_MAJOR := $(shell { $(JRE_DIR)/bin/java -version 2>&1 | grep -oE '"[0-9]+' | head -1 | tr -d '"'; } 2>/dev/null || echo 0)

# -- OS + Arch Detection ------------------------------------------------------
# Maps current machine to Adoptium API values and electron-builder output format
ifeq ($(OS),Windows_NT)
    DETECTED_OS     := windows
    ADOPTIUM_OS     := windows
    INSTALLER_EXT   := exe
    JRE_ARCHIVE_EXT := zip
    UNAME_ARCH      := x86_64
else
    UNAME_S         := $(shell uname -s)
    UNAME_ARCH      := $(shell uname -m)
    ifeq ($(UNAME_S),Darwin)
        DETECTED_OS     := macos
        ADOPTIUM_OS     := mac
        INSTALLER_EXT   := dmg
        JRE_ARCHIVE_EXT := tar.gz
    else
        DETECTED_OS     := linux
        ADOPTIUM_OS     := linux
        INSTALLER_EXT   := AppImage
        JRE_ARCHIVE_EXT := tar.gz
    endif
endif

# uname outputs x86_64 - Adoptium API expects x64
ifeq ($(UNAME_ARCH),x86_64)
    ADOPTIUM_ARCH := x64
else ifeq ($(UNAME_ARCH),arm64)
    ADOPTIUM_ARCH := aarch64
else ifeq ($(UNAME_ARCH),aarch64)
    ADOPTIUM_ARCH := aarch64
else
    ADOPTIUM_ARCH := x64
endif

# Always pulls latest patch release of JRE_VERSION for the detected platform
ADOPTIUM_URL := https://api.adoptium.net/v3/binary/latest/$(JRE_VERSION)/ga/$(ADOPTIUM_OS)/$(ADOPTIUM_ARCH)/jre/hotspot/normal/eclipse

# -- Phony targets ------------------------------------------------------------
.PHONY: build build-nexus build-agent dev-nexus dev-agent setup-jre \
        dev-nexus-skip-maven dev-agent-skip-maven \
        _maven-nexus _maven-agent \
        _copy-nexus _copy-agent \
        _dist-nexus _dist-agent \
        _installer-nexus _installer-agent \
        help

# =============================================================================
# HELP
# =============================================================================

help:
	@echo ""
	@echo "Beamlink - available targets:"
	@echo ""
	@echo "  setup-jre               Download JRE $(JRE_VERSION) for current OS ($(DETECTED_OS)/$(ADOPTIUM_ARCH))"
	@echo ""
	@echo "  build                   Production build for both modules"
	@echo "  build-nexus             Production build for Nexus only"
	@echo "  build-agent             Production build for Agent only"
	@echo ""
	@echo "  dev-nexus               Run Nexus in dev mode (full Maven recompile)"
	@echo "  dev-nexus-skip-maven    Run Nexus in dev mode (skip Maven)"
	@echo "  dev-agent               Run Agent in dev mode (full Maven recompile)"
	@echo "  dev-agent-skip-maven    Run Agent in dev mode (skip Maven)"
	@echo ""
	@echo "  Nexus UI:  http://localhost:5173"
	@echo "  Agent UI:  http://localhost:5174"
	@echo ""
	@echo "  Backend logs (dev):"
	@echo "    tail -f resources/nexus/logs/nexus.log"
	@echo "    tail -f resources/agent/logs/agent.log"
	@echo ""

# =============================================================================
# PUBLIC TARGETS
# =============================================================================

setup-jre:
	@echo ""
	@echo "▸ JRE setup (Temurin $(JRE_VERSION) - OS: $(DETECTED_OS) / Arch: $(ADOPTIUM_ARCH))"
	@if [ "$(INSTALLED_JRE_MAJOR)" -ge "$(JRE_VERSION)" ] 2>/dev/null; then \
		echo "  ✓ JRE $(INSTALLED_JRE_MAJOR) found (>= required $(JRE_VERSION)) - skipping"; \
	else \
		echo "  Downloading from Adoptium API..."; \
		mkdir -p /tmp/beamlink-jre/extracted; \
		if curl -L --progress-bar -o /tmp/beamlink-jre/jre.$(JRE_ARCHIVE_EXT) "$(ADOPTIUM_URL)"; then \
			echo "  Extracting..."; \
			if [ "$(JRE_ARCHIVE_EXT)" = "zip" ]; then \
				unzip -q /tmp/beamlink-jre/jre.zip -d /tmp/beamlink-jre/extracted; \
				rm -rf $(JRE_DIR); \
				mkdir -p $(JRE_DIR); \
				cp -r /tmp/beamlink-jre/extracted/*/* $(JRE_DIR)/; \
				if [ -z "$$(ls -A $(JRE_DIR))" ]; then \
					echo "  ✗ ERROR: JRE extraction produced empty directory"; \
					rm -rf /tmp/beamlink-jre; \
					exit 1; \
				fi; \
			else \
				rm -rf $(JRE_DIR); \
				mkdir -p $(JRE_DIR); \
				tar -xzf /tmp/beamlink-jre/jre.tar.gz -C $(JRE_DIR) --strip-components=1; \
			fi; \
			rm -rf /tmp/beamlink-jre; \
			echo "  ✓ JRE extracted -> resources/jre/"; \
		else \
			echo "  ✗ ERROR: Download failed - existing JRE left untouched"; \
			rm -rf /tmp/beamlink-jre; \
			exit 1; \
		fi; \
	fi

build: build-nexus build-agent
	@echo ""
	@echo "✓ All builds complete."

build-nexus: setup-jre _maven-nexus _copy-nexus _dist-nexus _installer-nexus
	@echo ""
	@echo "✓ Beamlink Nexus build complete."

build-agent: setup-jre _maven-agent _copy-agent _dist-agent _installer-agent
	@echo ""
	@echo "✓ Beamlink Agent build complete."

dev-nexus: setup-jre _maven-nexus _copy-nexus
	@echo ""
	@echo "▸ Starting Nexus in dev mode..."
	@echo "  Backend logs: tail -f resources/nexus/logs/nexus.log"
	cd $(NEXUS_UI_DIR) && npm run dev

dev-agent: setup-jre _maven-agent _copy-agent
	@echo ""
	@echo "▸ Starting Agent in dev mode..."
	@echo "  Backend logs: tail -f resources/agent/logs/agent.log"
	cd $(AGENT_UI_DIR) && npm run dev

dev-nexus-skip-maven:
	@echo ""
	@echo "▸ Starting Nexus in dev mode (skipping Maven)..."
	@echo "  Backend logs: tail -f resources/nexus/logs/nexus.log"
	cd $(NEXUS_UI_DIR) && npm run dev

dev-agent-skip-maven:
	@echo ""
	@echo "▸ Starting Agent in dev mode (skipping Maven)..."
	@echo "  Backend logs: tail -f resources/agent/logs/agent.log"
	cd $(AGENT_UI_DIR) && npm run dev

# =============================================================================
# INTERNAL STEPS
# Prefixed with _ - not meant to be called directly
# =============================================================================

_maven-nexus:
	@echo ""
	@echo "▸ [nexus] Step 1 - Maven package"
	cd $(NEXUS_JAVA_DIR) && mvn clean package -DskipTests

_maven-agent:
	@echo ""
	@echo "▸ [agent] Step 1 - Maven package"
	cd $(AGENT_JAVA_DIR) && mvn clean package -DskipTests

# Globs target/ so the version number in the JAR filename doesn't matter
_copy-nexus:
	@echo ""
	@echo "▸ [nexus] Step 2 - Copy JAR and .env to resources"
	@mkdir -p $(NEXUS_BACKEND) $(NEXUS_CONFIG)
	$(eval NEXUS_JAR := $(shell ls $(NEXUS_JAVA_DIR)/target/beamlink-nexus-*.jar 2>/dev/null | head -1))
	@if [ -z "$(NEXUS_JAR)" ]; then \
		echo "✗ ERROR: No JAR found in $(NEXUS_JAVA_DIR)/target/ - did Maven build succeed?"; \
		exit 1; \
	fi
	@echo "  Found: $$(basename $(NEXUS_JAR))"
	cp $(NEXUS_JAR) $(NEXUS_BACKEND)/beamlink-nexus.jar
	@echo "  ✓ JAR -> resources/nexus/backend/beamlink-nexus.jar"
	@if [ ! -f "$(NEXUS_JAVA_DIR)/.env" ]; then \
		echo "✗ ERROR: .env not found in $(NEXUS_JAVA_DIR)"; \
		exit 1; \
	fi
	cp $(NEXUS_JAVA_DIR)/.env $(NEXUS_CONFIG)/.env
	@echo "  ✓ .env -> resources/nexus/config/.env"

_copy-agent:
	@echo ""
	@echo "▸ [agent] Step 2 - Copy JAR and .env to resources"
	@mkdir -p $(AGENT_BACKEND) $(AGENT_CONFIG)
	$(eval AGENT_JAR := $(shell ls $(AGENT_JAVA_DIR)/target/beamlink-agent-*.jar 2>/dev/null | head -1))
	@if [ -z "$(AGENT_JAR)" ]; then \
		echo "✗ ERROR: No JAR found in $(AGENT_JAVA_DIR)/target/ - did Maven build succeed?"; \
		exit 1; \
	fi
	@echo "  Found: $$(basename $(AGENT_JAR))"
	cp $(AGENT_JAR) $(AGENT_BACKEND)/beamlink-agent.jar
	@echo "  ✓ JAR -> resources/agent/backend/beamlink-agent.jar"
	@if [ ! -f "$(AGENT_JAVA_DIR)/.env" ]; then \
		echo "✗ ERROR: .env not found in $(AGENT_JAVA_DIR)"; \
		exit 1; \
	fi
	cp $(AGENT_JAVA_DIR)/.env $(AGENT_CONFIG)/.env
	@echo "  ✓ .env -> resources/agent/config/.env"

_dist-nexus:
	@echo ""
	@echo "▸ [nexus] Step 3 - Electron dist (npm run dist)"
	cd $(NEXUS_UI_DIR) && npm run dist

_dist-agent:
	@echo ""
	@echo "▸ [agent] Step 3 - Electron dist (npm run dist)"
	cd $(AGENT_UI_DIR) && npm run dist

_installer-nexus:
	@echo ""
	@echo "▸ [nexus] Step 4 - Copy installer to root (OS: $(DETECTED_OS))"
	$(eval NEXUS_INSTALLER := $(shell ls $(NEXUS_UI_DIR)/dist/*.$(INSTALLER_EXT) 2>/dev/null | head -1))
	@if [ -z "$(NEXUS_INSTALLER)" ]; then \
		echo "✗ ERROR: No .$(INSTALLER_EXT) installer found in $(NEXUS_UI_DIR)/dist/"; \
		exit 1; \
	fi
	cp "$(NEXUS_INSTALLER)" "$(ROOT_DIR)/"
	@echo "  ✓ Installer -> $$(basename $(NEXUS_INSTALLER))"

_installer-agent:
	@echo ""
	@echo "▸ [agent] Step 4 - Copy installer to root (OS: $(DETECTED_OS))"
	$(eval AGENT_INSTALLER := $(shell ls $(AGENT_UI_DIR)/dist/*.$(INSTALLER_EXT) 2>/dev/null | head -1))
	@if [ -z "$(AGENT_INSTALLER)" ]; then \
		echo "✗ ERROR: No .$(INSTALLER_EXT) installer found in $(AGENT_UI_DIR)/dist/"; \
		exit 1; \
	fi
	cp "$(AGENT_INSTALLER)" "$(ROOT_DIR)/"
	@echo "  ✓ Installer -> $$(basename $(AGENT_INSTALLER))"