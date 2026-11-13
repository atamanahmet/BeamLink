# =============================================================================
# Beamlink - Build & Dev Orchestration
# =============================================================================
#
# build pipeline:  setup-jre -> Maven -> copy JAR + .env -> npm run dist -> copy installer
# dev pipeline:    setup-jre -> Maven -> copy JAR + .env -> npm run dev
# test pipeline:   frontend (typecheck, build, lint, vitest) -> nexus mvn -> peer mvn -> android unit
# apk pipeline:    check phone -> cap:sync -> gradlew assembleDebug -> adb install
#
# Usage:
#   make help           Show this help
#   make setup-jre      Download JRE for current OS - runs automatically, call manually to refresh
#
#   make build          Production build for both modules
#   make build-nexus    Production build for Beamlink Nexus only
#   make build-peer     Production build for Beamlink Peer only
#
#   make dev-nexus                Run Nexus in dev mode (full Maven recompile)
#   make dev-nexus-skip-maven     Run Nexus in dev mode (skip Maven, reuse existing JAR)
#   make dev-peer                 Run Peer in dev mode (full Maven recompile)
#   make dev-peer-skip-maven      Run Peer in dev mode (skip Maven, reuse existing JAR)
#
#   make build-apk      Build the debug APK (no phone needed)
#   make install-apk    Check the phone, build the debug APK and install it
#
#   make test-all       Run every gate. Use make -k test-all to run all even if one fails
#   make test-frontend  typecheck, build, lint, vitest
#   make test-nexus     Maven clean test for Nexus
#   make test-peer      Maven clean test for Peer
#   make test-android   Android unit tests with a real count from fresh results
#
# Requirements:
#   - mvn, java, node, npm, curl on PATH
#   - tar (Mac/Linux) or unzip (Windows via Git Bash)
#   - APK and Android test targets: pnpm on PATH, adb on PATH or ANDROID_HOME set.
#     They use ANDROID_JAVA_HOME (Android Studio JBR 21) and do not touch JAVA_HOME, so Maven keeps its own JDK.
#     Another JDK 21: make build-apk ANDROID_JAVA_HOME="C:/path/to/jdk21"
# =============================================================================

# -- Paths --------------------------------------------------------------------
ROOT_DIR        := $(shell pwd)

NEXUS_JAVA_DIR  := $(ROOT_DIR)/beamlink-nexus
PEER_JAVA_DIR  := $(ROOT_DIR)/beamlink-peer

NEXUS_UI_DIR    := $(ROOT_DIR)/frontend/apps/nexus
PEER_UI_DIR    := $(ROOT_DIR)/frontend/apps/peer

FRONTEND_DIR   := $(ROOT_DIR)/frontend

# Android web app (cap:sync lives here) and its native Gradle project
ANDROID_UI_DIR     := $(ROOT_DIR)/frontend/apps/android
ANDROID_NATIVE_DIR := $(ANDROID_UI_DIR)/android
APK_PATH           := $(ANDROID_NATIVE_DIR)/app/build/outputs/apk/debug/app-debug.apk

# JDK 21 for Capacitor 7. Used only by the Android targets, Maven still uses JAVA_HOME
ANDROID_JAVA_HOME ?= C:/Program Files/Android/Android Studio/jbr

# Fresh unit test result XML, counted by test-android
ANDROID_TEST_RESULTS := $(ANDROID_NATIVE_DIR)/app/build/test-results/testDebugUnitTest

# adb from PATH first, then ANDROID_HOME/platform-tools. Empty if neither exists
ADB := $(shell command -v adb 2>/dev/null || { [ -n "$$ANDROID_HOME" ] && ls "$$ANDROID_HOME"/platform-tools/adb* 2>/dev/null | head -1; })

# Shared runtime resources - gitignored, populated by this Makefile
JRE_DIR         := $(ROOT_DIR)/resources/jre
NEXUS_BACKEND   := $(ROOT_DIR)/resources/nexus/backend
NEXUS_CONFIG    := $(ROOT_DIR)/resources/nexus/config
PEER_BACKEND   := $(ROOT_DIR)/resources/peer/backend
PEER_CONFIG    := $(ROOT_DIR)/resources/peer/config

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
.PHONY: build build-nexus build-peer dev-nexus dev-peer setup-jre \
        dev-nexus-skip-maven dev-peer-skip-maven \
        build-apk install-apk \
        test-all test-frontend test-nexus test-peer test-android \
        _maven-nexus _maven-peer \
        _copy-nexus _copy-peer \
        _dist-nexus _dist-peer \
        _installer-nexus _installer-peer \
        _check-java _check-device _sync-apk _gradle-apk _install-apk \
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
	@echo "  build-peer              Production build for Peer only"
	@echo ""
	@echo "  dev-nexus               Run Nexus in dev mode (full Maven recompile)"
	@echo "  dev-nexus-skip-maven    Run Nexus in dev mode (skip Maven)"
	@echo "  dev-peer                Run Peer in dev mode (full Maven recompile)"
	@echo "  dev-peer-skip-maven     Run Peer in dev mode (skip Maven)"
	@echo ""
	@echo "  test-all                Run every gate (frontend, nexus, peer, android)"
	@echo "  test-frontend           typecheck, build, lint, vitest"
	@echo "  test-nexus              Maven clean test for Nexus"
	@echo "  test-peer               Maven clean test for Peer"
	@echo "  test-android            Android unit tests with a real count"
	@echo "  wipe-nexus-dev          Remove the dev nexus.db, wal, and shm files"
	@echo ""
	@echo "  build-apk               Build the debug APK (no phone needed)"
	@echo "  install-apk             Check the phone, build the debug APK and install it"
	@echo ""
	@echo "  Nexus UI:  http://localhost:5173"
	@echo "  Peer UI:   http://localhost:5174"
	@echo ""
	@echo "  Backend logs (dev):"
	@echo "    tail -f resources/nexus/logs/nexus.log"
	@echo "    tail -f resources/peer/logs/peer.log"
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

build: build-nexus build-peer
	@echo ""
	@echo "✓ All builds complete."

build-nexus: setup-jre _maven-nexus _copy-nexus _dist-nexus _installer-nexus
	@echo ""
	@echo "✓ Beamlink Nexus build complete."

build-peer: setup-jre _maven-peer _copy-peer _dist-peer _installer-peer
	@echo ""
	@echo "✓ Beamlink Peer build complete."

dev-nexus: setup-jre _maven-nexus _copy-nexus
	@echo ""
	@echo "▸ Starting Nexus in dev mode..."
	@echo "  Backend logs: tail -f resources/nexus/logs/nexus.log"
	cd $(NEXUS_UI_DIR) && npm run dev

dev-peer: setup-jre _maven-peer _copy-peer
	@echo ""
	@echo "▸ Starting Peer in dev mode..."
	@echo "  Backend logs: tail -f resources/peer/logs/peer.log"
	cd $(PEER_UI_DIR) && npm run dev

dev-nexus-skip-maven:
	@echo ""
	@echo "▸ Starting Nexus in dev mode (skipping Maven)..."
	@echo "  Backend logs: tail -f resources/nexus/logs/nexus.log"
	cd $(NEXUS_UI_DIR) && npm run dev

dev-peer-skip-maven:
	@echo ""
	@echo "▸ Starting Peer in dev mode (skipping Maven)..."
	@echo "  Backend logs: tail -f resources/peer/logs/peer.log"
	cd $(PEER_UI_DIR) && npm run dev

# Build only, the phone does not need to be connected
build-apk: _check-java _sync-apk _gradle-apk
	@echo ""
	@echo "✓ APK ready: $(APK_PATH)"

# Phone is checked first so a missing cable fails before the long build
install-apk: _check-device build-apk _install-apk
	@echo ""
	@echo "✓ APK installed on the phone."

# Dev sqlite only. Leaves device.p8 and nexus-tls.p12.
wipe-nexus-dev:
	rm -f $(ROOT_DIR)/resources/nexus/data/database/nexus.db
	rm -f $(ROOT_DIR)/resources/nexus/data/database/nexus.db-wal
	rm -f $(ROOT_DIR)/resources/nexus/data/database/nexus.db-shm

# Gates. Each target stops on the first failure, use make -k test-all to see all of them
test-all: test-frontend test-nexus test-peer test-android
	@echo ""
	@echo "✓ All gates passed."

test-frontend:
	@echo ""
	@echo "▸ [frontend] typecheck"
	cd $(FRONTEND_DIR) && pnpm run typecheck
	@echo ""
	@echo "▸ [frontend] build"
	cd $(FRONTEND_DIR) && pnpm run build
	@echo ""
	@echo "▸ [frontend] lint"
	cd $(FRONTEND_DIR) && pnpm run lint
	@echo ""
	@echo "▸ [frontend] vitest"
	cd $(FRONTEND_DIR) && pnpm run test

# clean first so old surefire reports cannot add to the count
test-nexus:
	@echo ""
	@echo "▸ [nexus] Maven clean test"
	cd $(NEXUS_JAVA_DIR) && mvn clean test

test-peer:
	@echo ""
	@echo "▸ [peer] Maven clean test"
	cd $(PEER_JAVA_DIR) && mvn clean test

# cleanTestDebugUnitTest removes old result XML. The count prints even when tests fail, then make exits with the Gradle status
test-android: _check-java
	@echo ""
	@echo "▸ [android] Unit tests"
	@if [ ! -f "$(ANDROID_NATIVE_DIR)/gradlew" ]; then \
		echo "✗ ERROR: gradlew not found in $(ANDROID_NATIVE_DIR)"; \
		exit 1; \
	fi
	@cd $(ANDROID_NATIVE_DIR) && JAVA_HOME="$(ANDROID_JAVA_HOME)" ./gradlew cleanTestDebugUnitTest testDebugUnitTest; STATUS=$$?; \
	"$(MAKE)" -C "$(ROOT_DIR)" --no-print-directory _count-android; \
	exit $$STATUS

# =============================================================================
# INTERNAL STEPS
# Prefixed with _ - not meant to be called directly
# =============================================================================

_maven-nexus:
	@echo ""
	@echo "▸ [nexus] Step 1 - Maven package"
	cd $(NEXUS_JAVA_DIR) && mvn clean package -DskipTests

_maven-peer:
	@echo ""
	@echo "▸ [peer] Step 1 - Maven package"
	cd $(PEER_JAVA_DIR) && mvn clean package -DskipTests

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

_copy-peer:
	@echo ""
	@echo "▸ [peer] Step 2 - Copy JAR and .env to resources"
	@mkdir -p $(PEER_BACKEND) $(PEER_CONFIG)
	$(eval PEER_JAR := $(shell ls $(PEER_JAVA_DIR)/target/beamlink-peer-*.jar 2>/dev/null | head -1))
	@if [ -z "$(PEER_JAR)" ]; then \
		echo "✗ ERROR: No JAR found in $(PEER_JAVA_DIR)/target/ - did Maven build succeed?"; \
		exit 1; \
	fi
	@echo "  Found: $$(basename $(PEER_JAR))"
	cp $(PEER_JAR) $(PEER_BACKEND)/beamlink-peer.jar
	@echo "  ✓ JAR -> resources/peer/backend/beamlink-peer.jar"
	@if [ ! -f "$(PEER_JAVA_DIR)/.env" ]; then \
		echo "✗ ERROR: .env not found in $(PEER_JAVA_DIR)"; \
		exit 1; \
	fi
	cp $(PEER_JAVA_DIR)/.env $(PEER_CONFIG)/.env
	@echo "  ✓ .env -> resources/peer/config/.env"

_dist-nexus:
	@echo ""
	@echo "▸ [nexus] Step 3 - Electron dist (npm run dist)"
	cd $(NEXUS_UI_DIR) && npm run dist

_dist-peer:
	@echo ""
	@echo "▸ [peer] Step 3 - Electron dist (npm run dist)"
	cd $(PEER_UI_DIR) && npm run dist

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

_installer-peer:
	@echo ""
	@echo "▸ [peer] Step 4 - Copy installer to root (OS: $(DETECTED_OS))"
	$(eval PEER_INSTALLER := $(shell ls $(PEER_UI_DIR)/dist/*.$(INSTALLER_EXT) 2>/dev/null | head -1))
	@if [ -z "$(PEER_INSTALLER)" ]; then \
		echo "✗ ERROR: No .$(INSTALLER_EXT) installer found in $(PEER_UI_DIR)/dist/"; \
		exit 1; \
	fi
	cp "$(PEER_INSTALLER)" "$(ROOT_DIR)/"
	@echo "  ✓ Installer -> $$(basename $(PEER_INSTALLER))"

# -- APK steps ----------------------------------------------------------------

# Gradle prints no total, so the numbers are summed from the fresh XML files
_count-android:
	@if ! ls $(ANDROID_TEST_RESULTS)/*.xml >/dev/null 2>&1; then \
		echo "✗ ERROR: no test result XML in $(ANDROID_TEST_RESULTS)"; \
		exit 1; \
	fi
	@cat $(ANDROID_TEST_RESULTS)/*.xml | grep -o '<testsuite [^>]*' | awk '{ for (i = 1; i <= NF; i++) { split($$i, a, "="); gsub(/"/, "", a[2]); if (a[1] == "tests") t += a[2]; if (a[1] == "failures") f += a[2]; if (a[1] == "errors") e += a[2]; if (a[1] == "skipped") k += a[2] } } END { printf "  android unit: tests %d, failures %d, errors %d, skipped %d\n", t, f, e, k }'

# Capacitor 7 needs Java 21. The Android targets use ANDROID_JAVA_HOME, so its major version is checked here
_check-java:
	@if [ ! -x "$(ANDROID_JAVA_HOME)/bin/java" ] && [ ! -x "$(ANDROID_JAVA_HOME)/bin/java.exe" ]; then \
		echo "✗ ERROR: no java found in $(ANDROID_JAVA_HOME)/bin."; \
		echo "  Set it with: make build-apk ANDROID_JAVA_HOME=\"C:/path/to/jdk21\""; \
		exit 1; \
	fi
	@MAJOR=$$("$(ANDROID_JAVA_HOME)/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/'); \
	if ! [ "$$MAJOR" -ge 21 ] 2>/dev/null; then \
		echo "✗ ERROR: $(ANDROID_JAVA_HOME) runs Java $$MAJOR. The Android build needs Java 21 or newer."; \
		echo "  Set it with: make build-apk ANDROID_JAVA_HOME=\"C:/path/to/jdk21\""; \
		exit 1; \
	fi

_check-device:
	@echo ""
	@echo "▸ [apk] Step 1 - Check phone connection"
	@if [ -z "$(ADB)" ]; then \
		echo "✗ ERROR: adb not found. Add Android SDK platform-tools to PATH or set ANDROID_HOME."; \
		exit 1; \
	fi
	@DEVICES=$$("$(ADB)" devices 2>/dev/null | tr -d '\r' | awk 'NF==2 && $$1 !~ /^\*/'); \
	COUNT=$$(printf '%s\n' "$$DEVICES" | grep -c .); \
	if [ "$$COUNT" -eq 0 ]; then \
		echo "✗ No phone found. Cable not connected, or USB debugging is off."; \
		echo "  Connect the cable and turn on USB debugging (Settings > Developer options)."; \
		exit 1; \
	fi; \
	if [ "$$COUNT" -gt 1 ]; then \
		echo "✗ More than one device connected. Unplug the others or close the emulator."; \
		exit 1; \
	fi; \
	STATE=$$(printf '%s\n' "$$DEVICES" | awk '{print $$2}'); \
	case "$$STATE" in \
		device) echo "  ✓ Phone connected" ;; \
		unauthorized) echo "✗ Phone is not authorized. Unlock it and accept the Allow USB debugging prompt."; exit 1 ;; \
		offline) echo "✗ Phone is offline. Reconnect the cable or toggle USB debugging off and on."; exit 1 ;; \
		*) echo "✗ Unexpected phone state: $$STATE. Reconnect the cable."; exit 1 ;; \
	esac

_sync-apk:
	@echo ""
	@echo "▸ [apk] Step 2 - Build web app and cap:sync"
	cd $(ANDROID_UI_DIR) && pnpm run cap:sync

_gradle-apk:
	@echo ""
	@echo "▸ [apk] Step 3 - Gradle assembleDebug"
	@if [ ! -f "$(ANDROID_NATIVE_DIR)/gradlew" ]; then \
		echo "✗ ERROR: gradlew not found in $(ANDROID_NATIVE_DIR)"; \
		exit 1; \
	fi
	cd $(ANDROID_NATIVE_DIR) && JAVA_HOME="$(ANDROID_JAVA_HOME)" ./gradlew assembleDebug
	@if [ ! -f "$(APK_PATH)" ]; then \
		echo "✗ ERROR: APK not found at $(APK_PATH) after the build"; \
		exit 1; \
	fi

_install-apk:
	@echo ""
	@echo "▸ [apk] Step 4 - adb install"
	@if "$(ADB)" install -r "$(APK_PATH)"; then \
		echo "  ✓ Installed"; \
	else \
		echo "✗ Install failed. Read the adb message above."; \
		echo "  INSTALL_FAILED_UPDATE_INCOMPATIBLE: uninstall the app from the phone first (this wipes its data)."; \
		exit 1; \
	fi