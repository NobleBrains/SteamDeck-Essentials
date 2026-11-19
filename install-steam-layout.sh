#!/usr/bin/env bash
# SteamDeck Essentials - Steam Layout Auto-Installer
set -e

STEAM_DIR="$HOME/.local/share/Steam"
if [ ! -d "$STEAM_DIR" ]; then
    STEAM_DIR="$HOME/.steam/steam"
fi

if [ ! -d "$STEAM_DIR" ]; then
    echo "Steam directory not found!"
    exit 1
fi

TEMPLATES_DIR="$STEAM_DIR/controller_base/templates"
mkdir -p "$TEMPLATES_DIR"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_SRC="$SCRIPT_DIR/src/main/resources/assets/steamdeck_essentials/controller_neptune_steamdeck_essentials.vdf"

if [ -f "$TEMPLATES_DIR/controller_neptune_steamdeck_essentials.vdf" ]; then
    echo "Template already installed at $TEMPLATES_DIR"
elif [ -f "$TEMPLATE_SRC" ]; then
    cp "$TEMPLATE_SRC" "$TEMPLATES_DIR/controller_neptune_steamdeck_essentials.vdf"
    echo "Installed template to $TEMPLATES_DIR"
fi

# Also auto-configure for existing shortcuts
for user_cfg in "$STEAM_DIR"/steamapps/common/"Steam Controller Configs"/*/config; do
    if [ -d "$user_cfg" ]; then
        mkdir -p "$user_cfg/prism launcher" "$user_cfg/minecraft"
        if [ -f "$TEMPLATES_DIR/controller_neptune_steamdeck_essentials.vdf" ]; then
            cp "$TEMPLATES_DIR/controller_neptune_steamdeck_essentials.vdf" "$user_cfg/prism launcher/controller_neptune.vdf"
            cp "$TEMPLATES_DIR/controller_neptune_steamdeck_essentials.vdf" "$user_cfg/minecraft/controller_neptune.vdf"
            echo "Configured shortcut layouts in $user_cfg"
        fi
    fi
done

echo "SteamDeck Essentials layout is fully configured and ready!"
