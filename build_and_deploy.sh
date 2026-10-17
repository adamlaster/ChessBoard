#!/usr/bin/env bash
# ==============================================================================
# Chess 4 Board - Build, Export & Deployment Pipeline
# ==============================================================================
# Usage:
#   ./build_and_deploy.sh             # Complete build (AAR + APK) & deploy to Board
#   ./build_and_deploy.sh --skip-aar  # Skip AAR compilation (Godot/assets only)
#   ./build_and_deploy.sh --no-deploy # Build APK without installing to device
#   ./build_and_deploy.sh --doctor    # Check environment dependencies & configuration
#   ./build_and_deploy.sh -d <target> # Deploy to a specific Board IP or host
# ==============================================================================

set -euo pipefail

# ANSI Color formatting
BOLD='\033[1m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# ------------------------------------------------------------------------------
# 1. Base Project Directories (resolved relative to this script)
# ------------------------------------------------------------------------------
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHESS_DIR="$ROOT_DIR/chess-4-board"
PLUGIN_DIR="$ROOT_DIR/godot-webview/GodotWebView"
AAR_OUT_DIR="$PLUGIN_DIR/GodotWebView/build/outputs/aar"
TARGET_ADDONS_DIR="$CHESS_DIR/addons/webview"

# ------------------------------------------------------------------------------
# 2. Source Local Environment Overrides (.env / .env.local) if Present
# ------------------------------------------------------------------------------
if [[ -f "$ROOT_DIR/.env" ]]; then
    # shellcheck source=/dev/null
    source "$ROOT_DIR/.env"
elif [[ -f "$ROOT_DIR/.env.local" ]]; then
    # shellcheck source=/dev/null
    source "$ROOT_DIR/.env.local"
fi

# ------------------------------------------------------------------------------
# 3. Output Directory & APK Path Configuration
# ------------------------------------------------------------------------------
# By default, writes to sibling '../output' directory to keep 90+ MB APKs out of git.
OUTPUT_DIR="${OUTPUT_DIR:-$(cd "$ROOT_DIR/.." && pwd)/output}"
APK_NAME="${APK_NAME:-Chess4Board.apk}"
APK_PATH="$OUTPUT_DIR/$APK_NAME"

# ------------------------------------------------------------------------------
# 4. Tool Auto-Detection & Fallbacks
# ------------------------------------------------------------------------------

# (A) Java 17+ JDK
if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -x "/usr/libexec/java_home" ]] && /usr/libexec/java_home -v 17 &>/dev/null; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    elif [[ -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]]; then
        export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    elif [[ -d "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]]; then
        export JAVA_HOME="/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    elif [[ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
        export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    elif [[ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]]; then
        export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    fi
fi

# (B) Android SDK
if [[ -z "${ANDROID_HOME:-}" ]]; then
    if [[ -d "$HOME/Library/Android/sdk" ]]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [[ -d "$HOME/Android/Sdk" ]]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT" ]]; then
        export ANDROID_HOME="$ANDROID_SDK_ROOT"
    fi
fi

# Update PATH with detected tools
if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ -n "${ANDROID_HOME:-}" ]]; then
    export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi
export PATH="$HOME/.local/bin:$PATH"

# (C) Godot Engine (4.2+)
if [[ -z "${GODOT_BIN:-}" ]]; then
    if [[ -x "/Applications/Godot.app/Contents/MacOS/Godot" ]]; then
        GODOT_BIN="/Applications/Godot.app/Contents/MacOS/Godot"
    elif command -v godot &>/dev/null; then
        GODOT_BIN="$(command -v godot)"
    elif command -v godot4 &>/dev/null; then
        GODOT_BIN="$(command -v godot4)"
    elif [[ -x "/opt/homebrew/bin/godot" ]]; then
        GODOT_BIN="/opt/homebrew/bin/godot"
    else
        GODOT_BIN="godot"
    fi
fi

# (D) Board Connect CLI
if [[ -z "${BOARD_CONNECT_BIN:-}" ]]; then
    if command -v board-connect &>/dev/null; then
        BOARD_CONNECT_BIN="$(command -v board-connect)"
    elif [[ -x "$HOME/.local/bin/board-connect" ]]; then
        BOARD_CONNECT_BIN="$HOME/.local/bin/board-connect"
    else
        BOARD_CONNECT_BIN="board-connect"
    fi
fi

BOARD_CONNECT_ARGS="${BOARD_CONNECT_ARGS:-}"

# ------------------------------------------------------------------------------
# 5. Environment Doctor / Sanity Check Function
# ------------------------------------------------------------------------------
run_doctor() {
    echo -e "\n${BOLD}${CYAN}=== Chess 4 Board Environment Doctor ===${NC}\n"
    local all_ok=true

    # Check Java
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
        local java_ver
        java_ver="$("$JAVA_HOME/bin/javac" -version 2>&1 | head -n 1)"
        echo -e "  [${GREEN}✓${NC}] Java Development Kit (JDK): ${BOLD}$JAVA_HOME${NC} ($java_ver)"
    else
        echo -e "  [${RED}✗${NC}] Java Development Kit (JDK 17+): ${RED}NOT FOUND${NC}"
        echo -e "      Install via: ${YELLOW}brew install openjdk@17${NC} or set JAVA_HOME in .env"
        all_ok=false
    fi

    # Check Android SDK
    if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
        echo -e "  [${GREEN}✓${NC}] Android SDK: ${BOLD}$ANDROID_HOME${NC}"
        if [[ -x "$ANDROID_HOME/platform-tools/adb" ]]; then
            echo -e "  [${GREEN}✓${NC}] Android Platform Tools (adb): Installed"
        else
            echo -e "  [${YELLOW}!${NC}] Android Platform Tools (adb): Not found in \$ANDROID_HOME/platform-tools"
        fi
    else
        echo -e "  [${RED}✗${NC}] Android SDK: ${RED}NOT FOUND${NC}"
        echo -e "      Install via Android Studio or set ANDROID_HOME in .env"
        all_ok=false
    fi

    # Check Godot
    if command -v "$GODOT_BIN" &>/dev/null || [[ -x "$GODOT_BIN" ]]; then
        local godot_ver
        godot_ver="$("$GODOT_BIN" --version 2>&1 | head -n 1 || echo "unknown")"
        echo -e "  [${GREEN}✓${NC}] Godot Engine: ${BOLD}$GODOT_BIN${NC} ($godot_ver)"
    else
        echo -e "  [${RED}✗${NC}] Godot Engine: ${RED}NOT FOUND${NC} at '$GODOT_BIN'"
        echo -e "      Install via: ${YELLOW}brew install --cask godot${NC} or set GODOT_BIN in .env"
        all_ok=false
    fi

    # Check board-connect
    if command -v "$BOARD_CONNECT_BIN" &>/dev/null || [[ -x "$BOARD_CONNECT_BIN" ]]; then
        echo -e "  [${GREEN}✓${NC}] Board Connect CLI: ${BOLD}$BOARD_CONNECT_BIN${NC}"
    else
        echo -e "  [${YELLOW}!${NC}] Board Connect CLI: ${YELLOW}Not found${NC} (Required only for device deployment)"
        echo -e "      Ensure board-connect is in ~/.local/bin or on PATH"
    fi

    # Check Output Path
    echo -e "  [${GREEN}✓${NC}] Output APK Target: ${BOLD}$APK_PATH${NC}"
    if [[ -d "$OUTPUT_DIR" ]]; then
        echo -e "      Output directory exists and is writable."
    else
        echo -e "      Output directory will be created automatically upon export."
    fi

    echo ""
    if [[ "$all_ok" = true ]]; then
        echo -e "${BOLD}${GREEN}All essential build prerequisites are satisfied!${NC}\n"
        exit 0
    else
        echo -e "${BOLD}${RED}Some required tools are missing. Review instructions above or configure '.env'.${NC}\n"
        exit 1
    fi
}

# ------------------------------------------------------------------------------
# 6. Parse CLI Options
# ------------------------------------------------------------------------------
BUILD_AAR=true
DEPLOY=true

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-aar)
            BUILD_AAR=false
            shift
            ;;
        --no-deploy|--skip-deploy|-b)
            DEPLOY=false
            shift
            ;;
        --doctor|--check)
            run_doctor
            ;;
        -d|--device|--board)
            if [[ -n "${2:-}" ]]; then
                BOARD_CONNECT_ARGS="--board $2"
                shift 2
            else
                echo -e "${RED}Error: $1 requires a target board/IP argument${NC}"
                exit 1
            fi
            ;;
        -h|--help)
            echo -e "${BOLD}Chess 4 Board - Build & Deploy Script${NC}"
            echo "Usage: ./build_and_deploy.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --skip-aar            Skip compiling GodotWebView Android library (AAR)"
            echo "  --no-deploy, -b       Build APK without deploying to Board device"
            echo "  -d, --device <name>   Target a specific Board device/IP for deployment"
            echo "  --doctor, --check     Verify local environment tools, SDKs, and paths"
            echo "  -h, --help            Show this help message"
            echo ""
            echo "Environment Configuration:"
            echo "  Copy .env.example to .env or .env.local to customize paths"
            echo "  (ANDROID_HOME, JAVA_HOME, GODOT_BIN, OUTPUT_DIR, BOARD_CONNECT_BIN)."
            exit 0
            ;;
        *)
            echo -e "${YELLOW}Warning: Unknown option '$1'${NC}"
            shift
            ;;
    esac
done

echo -e "\n${BOLD}${CYAN}=== Starting Chess 4 Board Pipeline ===${NC}\n"

# Verify Essential Tool Prerequisites
if [[ "$BUILD_AAR" = true ]]; then
    if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/javac" ]]; then
        echo -e "${RED}Error: Java 17+ JDK not found. Run './build_and_deploy.sh --doctor' for setup help.${NC}"
        exit 1
    fi
fi

if ! command -v "$GODOT_BIN" &>/dev/null && [[ ! -x "$GODOT_BIN" ]]; then
    echo -e "${RED}Error: Godot binary not found at '$GODOT_BIN'. Run './build_and_deploy.sh --doctor' for setup help.${NC}"
    exit 1
fi

# ------------------------------------------------------------------------------
# STEP 1: Build Android WebView Plugin (AAR)
# ------------------------------------------------------------------------------
if [[ "$BUILD_AAR" = true ]]; then
    echo -e "${BOLD}${GREEN}[Step 1/4] Building Android WebView Plugin (AAR)...${NC}"
    cd "$PLUGIN_DIR"
    ./gradlew --no-build-cache clean assemble
    echo -e "${GREEN}✓ AAR build complete.${NC}\n"
else
    echo -e "${YELLOW}[Step 1/4] Skipping AAR build (--skip-aar selected).${NC}\n"
fi

# ------------------------------------------------------------------------------
# STEP 2: Copy AARs to chess-4-board
# ------------------------------------------------------------------------------
if [[ "$BUILD_AAR" = true ]]; then
    echo -e "${BOLD}${GREEN}[Step 2/4] Copying AARs to Chess 4 Board project...${NC}"
    mkdir -p "$TARGET_ADDONS_DIR"
    cp -v "$AAR_OUT_DIR/GodotWebView-debug.aar" "$TARGET_ADDONS_DIR/"
    cp -v "$AAR_OUT_DIR/GodotWebView-release.aar" "$TARGET_ADDONS_DIR/"
    echo -e "${GREEN}✓ Plugin AARs copied.${NC}\n"
else
    echo -e "${YELLOW}[Step 2/4] Skipping AAR copy (--skip-aar selected).${NC}\n"
fi

# ------------------------------------------------------------------------------
# STEP 3: Export Android APK via Godot CLI
# ------------------------------------------------------------------------------
echo -e "${BOLD}${GREEN}[Step 3/4] Exporting Chess4Board.apk with Godot...${NC}"
mkdir -p "$OUTPUT_DIR"
cd "$CHESS_DIR"
"$GODOT_BIN" --headless --path "$CHESS_DIR" --export-debug "Android" "$APK_PATH"
echo -e "${GREEN}✓ APK exported: $APK_PATH ($(du -h "$APK_PATH" | cut -f1))${NC}\n"

# ------------------------------------------------------------------------------
# STEP 4: Install and Launch APK on Board Device
# ------------------------------------------------------------------------------
if [[ "$DEPLOY" = true ]]; then
    echo -e "${BOLD}${GREEN}[Step 4/4] Deploying to Board device...${NC}"
    if ! command -v "$BOARD_CONNECT_BIN" &>/dev/null && [[ ! -x "$BOARD_CONNECT_BIN" ]]; then
        echo -e "${RED}Error: board-connect CLI not found at '$BOARD_CONNECT_BIN'.${NC}"
        echo -e "${YELLOW}Install board-connect or run with '--no-deploy' to export APK only.${NC}"
        exit 1
    fi
    cd "$OUTPUT_DIR"
    if [[ -n "$BOARD_CONNECT_ARGS" ]]; then
        "$BOARD_CONNECT_BIN" $BOARD_CONNECT_ARGS install "$APK_PATH" --launch
    else
        "$BOARD_CONNECT_BIN" install "$APK_PATH" --launch
    fi
    echo -e "\n${BOLD}${GREEN}=== Deployment Complete & Launched! ===${NC}\n"
else
    echo -e "${YELLOW}[Step 4/4] Skipping deployment (--no-deploy selected).${NC}\n"
    echo -e "${BOLD}${GREEN}=== Build Successful! ===${NC}\n"
fi
