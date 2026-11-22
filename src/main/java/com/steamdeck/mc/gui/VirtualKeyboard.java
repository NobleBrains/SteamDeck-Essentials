package com.steamdeck.mc.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.steamdeck.mc.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * On-Screen Keyboard tailored for the Steam Deck 16:10 touchscreen.
 * Features Split Mode, Full Mode, dynamic scaling, and an integrated Quick Action Bar.
 */
public class VirtualKeyboard {
    private static final VirtualKeyboard INSTANCE = new VirtualKeyboard();
    private static final float[] SCALE_PRESETS = {0.75F, 1.0F, 1.25F, 1.5F};

    private final List<List<Key>> leftRows = new ArrayList<>();
    private final List<List<Key>> rightRows = new ArrayList<>();
    private final List<List<Key>> fullRows = new ArrayList<>();

    private boolean open = false;
    private boolean shifted = false;
    private boolean capsLock = false;
    private boolean symbols = false;
    private boolean splitMode = true;
    private float scale = 1.0F;

    private int leftX;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int fullX;
    private int fullWidth;
    private int kbY;
    private int kbHeight;

    private int toggleX;
    private int toggleY;
    private int toggleW;
    private int toggleH;

    // SteamOS Dual-Cursor Trackpad Typing State
    private float leftCursorX = -1;
    private float leftCursorY = -1;
    private float rightCursorX = -1;
    private float rightCursorY = -1;
    private boolean dualCursorActive = false;
    private boolean joySelectorActive = false;
    private Key joyLeftKey = null;
    private Key joyRightKey = null;

    // Absolute trackpad typing (SteamOS style): each pad covers its half of the keyboard
    private final boolean[] padTouching = {false, false};
    private final Key[] padHoverKey = {null, null};
    private static final float KEY_HYSTERESIS = 0.0f; // SteamOS: key stays active while the point is inside its rect

    public static VirtualKeyboard getInstance() {
        return INSTANCE;
    }

    private VirtualKeyboard() {
        ModConfig config = ModConfig.getInstance();
        this.scale = config.oskScale;
        this.splitMode = config.oskSplitMode;
        this.buildLayouts();
    }

    public static void playClickSound() {
        float pitch = 1.35F + (float) (Math.random() * 0.15F);
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, pitch, 0.35F)
        );
    }

    private void buildLayouts() {
        this.buildSplitLayout();
        this.buildFullLayout();
    }

    private void buildSplitLayout() {
        this.leftRows.clear();
        this.rightRows.clear();

        List<Key> l1 = new ArrayList<>();
        l1.add(Key.character("1", "!", "~"));
        l1.add(Key.character("2", "@", "`"));
        l1.add(Key.character("3", "#", "|"));
        l1.add(Key.character("4", "$", "["));
        l1.add(Key.character("5", "%", "]"));
        this.leftRows.add(l1);

        List<Key> l2 = new ArrayList<>();
        l2.add(Key.character("q", "Q", "+"));
        l2.add(Key.character("w", "W", "="));
        l2.add(Key.character("e", "E", "/"));
        l2.add(Key.character("r", "R", "\\"));
        l2.add(Key.character("t", "T", "|"));
        this.leftRows.add(l2);

        List<Key> l3 = new ArrayList<>();
        l3.add(Key.action("Caps", KeyType.CAPS, 1.25F));
        l3.add(Key.character("a", "A", "ä"));
        l3.add(Key.character("s", "S", "ß"));
        l3.add(Key.character("d", "D", "#"));
        l3.add(Key.character("f", "F", "$"));
        l3.add(Key.character("g", "G", "&"));
        this.leftRows.add(l3);

        List<Key> l4 = new ArrayList<>();
        l4.add(Key.action("Shift", KeyType.SHIFT, 1.35F));
        l4.add(Key.character("z", "Z", "_"));
        l4.add(Key.character("x", "X", "-"));
        l4.add(Key.character("c", "C", ":"));
        l4.add(Key.character("v", "V", ";"));
        this.leftRows.add(l4);

        List<Key> l5 = new ArrayList<>();
        l5.add(Key.action("?123", KeyType.SYMBOLS, 1.15F));
        l5.add(Key.action("TAB", KeyType.TAB, 1.0F));
        l5.add(Key.character("/", "/", "/", 0.85F));
        l5.add(Key.action("SPACE", KeyType.SPACE, 1.7F));
        l5.add(Key.action("<", KeyType.LEFT, 0.85F));
        this.leftRows.add(l5);

        List<Key> r1 = new ArrayList<>();
        r1.add(Key.character("6", "^", "{"));
        r1.add(Key.character("7", "&", "}"));
        r1.add(Key.character("8", "*", "+"));
        r1.add(Key.character("9", "(", "="));
        r1.add(Key.character("0", ")", "%"));
        r1.add(Key.action("←", KeyType.BACKSPACE, 1.4F));
        this.rightRows.add(r1);

        List<Key> r2 = new ArrayList<>();
        r2.add(Key.character("y", "Y", "~"));
        r2.add(Key.character("u", "U", "ü"));
        r2.add(Key.character("i", "I", ">"));
        r2.add(Key.character("o", "O", "ö"));
        r2.add(Key.character("p", "P", "]"));
        this.rightRows.add(r2);

        List<Key> r3 = new ArrayList<>();
        r3.add(Key.character("h", "H", "*"));
        r3.add(Key.character("j", "J", "("));
        r3.add(Key.character("k", "K", ")"));
        r3.add(Key.character("l", "L", "'"));
        r3.add(Key.action("Enter", KeyType.ENTER, 1.5F));
        this.rightRows.add(r3);

        List<Key> r4 = new ArrayList<>();
        r4.add(Key.character("b", "B", "\""));
        r4.add(Key.character("n", "N", "?"));
        r4.add(Key.character("m", "M", "!"));
        r4.add(Key.character(",", "<", ","));
        r4.add(Key.character(".", ">", "."));
        r4.add(Key.action("Shift", KeyType.SHIFT, 1.35F));
        this.rightRows.add(r4);

        List<Key> r5 = new ArrayList<>();
        r5.add(Key.action(">", KeyType.RIGHT, 0.85F));
        r5.add(Key.action("SPACE", KeyType.SPACE, 1.6F));
        r5.add(Key.action("100%", KeyType.SIZE, 0.95F));
        r5.add(Key.action("Full", KeyType.LAYOUT, 0.95F));
        r5.add(Key.action("✕", KeyType.CLOSE, 0.85F));
        this.rightRows.add(r5);
    }

    private void buildFullLayout() {
        this.fullRows.clear();

        List<Key> r1 = new ArrayList<>();
        r1.add(Key.character("1", "!", "~"));
        r1.add(Key.character("2", "@", "`"));
        r1.add(Key.character("3", "#", "|"));
        r1.add(Key.character("4", "$", "["));
        r1.add(Key.character("5", "%", "]"));
        r1.add(Key.character("6", "^", "{"));
        r1.add(Key.character("7", "&", "}"));
        r1.add(Key.character("8", "*", "+"));
        r1.add(Key.character("9", "(", "="));
        r1.add(Key.character("0", ")", "%"));
        r1.add(Key.action("←", KeyType.BACKSPACE, 1.5F));
        this.fullRows.add(r1);

        List<Key> r2 = new ArrayList<>();
        r2.add(Key.character("q", "Q", "+"));
        r2.add(Key.character("w", "W", "="));
        r2.add(Key.character("e", "E", "/"));
        r2.add(Key.character("r", "R", "\\"));
        r2.add(Key.character("t", "T", "|"));
        r2.add(Key.character("y", "Y", "~"));
        r2.add(Key.character("u", "U", "ü"));
        r2.add(Key.character("i", "I", ">"));
        r2.add(Key.character("o", "O", "ö"));
        r2.add(Key.character("p", "P", "]"));
        this.fullRows.add(r2);

        List<Key> r3 = new ArrayList<>();
        r3.add(Key.action("Caps", KeyType.CAPS, 1.25F));
        r3.add(Key.character("a", "A", "ä"));
        r3.add(Key.character("s", "S", "ß"));
        r3.add(Key.character("d", "D", "#"));
        r3.add(Key.character("f", "F", "$"));
        r3.add(Key.character("g", "G", "&"));
        r3.add(Key.character("h", "H", "*"));
        r3.add(Key.character("j", "J", "("));
        r3.add(Key.character("k", "K", ")"));
        r3.add(Key.character("l", "L", "'"));
        r3.add(Key.action("Enter", KeyType.ENTER, 1.6F));
        this.fullRows.add(r3);

        List<Key> r4 = new ArrayList<>();
        r4.add(Key.action("Shift", KeyType.SHIFT, 1.4F));
        r4.add(Key.character("z", "Z", "_"));
        r4.add(Key.character("x", "X", "-"));
        r4.add(Key.character("c", "C", ":"));
        r4.add(Key.character("v", "V", ";"));
        r4.add(Key.character("b", "B", "\""));
        r4.add(Key.character("n", "N", "?"));
        r4.add(Key.character("m", "M", "!"));
        r4.add(Key.character(",", "<", ","));
        r4.add(Key.character(".", ">", "."));
        r4.add(Key.action("Shift", KeyType.SHIFT, 1.4F));
        this.fullRows.add(r4);

        List<Key> r5 = new ArrayList<>();
        r5.add(Key.action("?123", KeyType.SYMBOLS, 1.1F));
        r5.add(Key.action("TAB", KeyType.TAB, 0.95F));
        r5.add(Key.character("/", "/", "/", 0.8F));
        r5.add(Key.action("SPACE", KeyType.SPACE, 2.3F));
        r5.add(Key.character("-", "_", "-", 0.8F));
        r5.add(Key.action("<", KeyType.LEFT, 0.8F));
        r5.add(Key.action(">", KeyType.RIGHT, 0.8F));
        r5.add(Key.action("100%", KeyType.SIZE, 0.9F));
        r5.add(Key.action("Split", KeyType.LAYOUT, 0.9F));
        r5.add(Key.action("✕", KeyType.CLOSE, 0.8F));
        this.fullRows.add(r5);
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        if (!open) {
            padTouching[0] = padTouching[1] = false;
            padHoverKey[0] = padHoverKey[1] = null;
        }
    }

    public void toggle() {
        this.open = !this.open;
    }

    public float getScale() {
        return this.scale;
    }

    public void setScale(float newScale) {
        this.scale = Math.max(0.5F, Math.min(2.0F, newScale));
        ModConfig.getInstance().oskScale = this.scale;
        ModConfig.getInstance().save();
    }

    public void cycleScale() {
        int currentIndex = -1;
        for (int i = 0; i < SCALE_PRESETS.length; i++) {
            if (Math.abs(this.scale - SCALE_PRESETS[i]) < 0.05F) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % SCALE_PRESETS.length;
        this.setScale(SCALE_PRESETS[nextIndex]);
    }

    public void cycleScaleBackwards() {
        int currentIndex = -1;
        for (int i = 0; i < SCALE_PRESETS.length; i++) {
            if (Math.abs(this.scale - SCALE_PRESETS[i]) < 0.05F) {
                currentIndex = i;
                break;
            }
        }
        int prevIndex = currentIndex <= 0 ? SCALE_PRESETS.length - 1 : currentIndex - 1;
        this.setScale(SCALE_PRESETS[prevIndex]);
    }

    public String getSizeLabel() {
        return Math.round(this.scale * 100.0F) + "%";
    }

    public boolean hasTextInput(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen) {
            return true;
        }
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof EditBox || listener instanceof MultiLineEditBox) {
                return true;
            }
        }
        return false;
    }

    public EditBox getActiveEditBox(Screen screen) {
        if (screen == null) return null;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof EditBox editBox && editBox.isFocused()) {
                return editBox;
            }
        }
        EditBox singleBox = null;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof EditBox editBox) {
                if (singleBox != null) return null;
                singleBox = editBox;
            }
        }
        return singleBox;
    }

    private void layoutRows(List<List<Key>> rows, int startX, int startY, int totalWidth, int rowHeight, int keyGap, int padding) {
        for (int i = 0; i < rows.size(); ++i) {
            List<Key> row = rows.get(i);
            float totalWeight = 0.0F;
            for (Key key : row) {
                totalWeight += key.weight;
            }

            int usableWidth = totalWidth - padding * 2 - (row.size() - 1) * keyGap;
            float unitWidth = (float) usableWidth / totalWeight;
            int currentX = startX + padding;
            int currentY = startY + padding + i * (rowHeight + keyGap);

            for (Key key : row) {
                int keyWidth = Math.round(key.weight * unitWidth);
                key.x = currentX;
                key.y = currentY;
                key.width = keyWidth;
                key.height = rowHeight;
                currentX += keyWidth + keyGap;
            }
        }
    }

    public void updateLayout(int screenWidth, int screenHeight) {
        int numRows = 5;
        int baseRowHeight = Math.max(14, Math.min(18, screenHeight / 6));
        int rowHeight = Math.round(baseRowHeight * this.scale);
        int keyGap = this.scale >= 1.25F ? 3 : 2;
        int padding = this.scale >= 1.25F ? 5 : 4;

        int maxKbHeight = (int) (screenHeight * 0.65F);
        int maxRowHeight = Math.max(12, (maxKbHeight - padding * 2 - (numRows - 1) * keyGap) / numRows);
        rowHeight = Math.max(12, Math.min(rowHeight, maxRowHeight));

        this.kbHeight = numRows * rowHeight + (numRows - 1) * keyGap + padding * 2;
        this.kbY = screenHeight - this.kbHeight - 2;

        if (this.splitMode) {
            int maxHalfWidth = (screenWidth - 24) / 2;
            int targetHalfWidth = Math.round(195 * this.scale);
            this.leftWidth = Math.min(maxHalfWidth, targetHalfWidth);
            this.leftX = 4;
            this.rightWidth = this.leftWidth;
            this.rightX = screenWidth - this.rightWidth - 4;

            this.layoutRows(this.leftRows, this.leftX, this.kbY, this.leftWidth, rowHeight, keyGap, padding);
            this.layoutRows(this.rightRows, this.rightX, this.kbY, this.rightWidth, rowHeight, keyGap, padding);

            if (this.leftCursorX < 0 || this.leftCursorY < 0) {
                this.leftCursorX = this.leftX + this.leftWidth / 2.0f;
                this.leftCursorY = this.kbY + this.kbHeight / 2.0f;
            }
            if (this.rightCursorX < 0 || this.rightCursorY < 0) {
                this.rightCursorX = this.rightX + this.rightWidth / 2.0f;
                this.rightCursorY = this.kbY + this.kbHeight / 2.0f;
            }
        } else {
            int maxFullWidth = screenWidth - 8;
            int targetFullWidth = Math.round(430 * this.scale);
            this.fullWidth = Math.min(maxFullWidth, targetFullWidth);
            this.fullX = (screenWidth - this.fullWidth) / 2;

            this.layoutRows(this.fullRows, this.fullX, this.kbY, this.fullWidth, rowHeight, keyGap, padding);
        }

        this.toggleW = Math.round(24 * Math.min(this.scale, 1.25F));
        this.toggleH = Math.round(18 * Math.min(this.scale, 1.25F));
        this.toggleX = screenWidth - this.toggleW - 4;
        this.toggleY = screenHeight - this.toggleH - 4;
    }

    private void renderKey(GuiGraphicsExtractor graphics, Font font, Key key, int mouseX, int mouseY) {
        boolean isLeftHover = (padTouching[0] && key == padHoverKey[0])
                || (this.joySelectorActive && key == this.joyLeftKey)
                || (this.dualCursorActive && !padTouching[0] && !this.joySelectorActive && this.splitMode && key.contains(this.leftCursorX, this.leftCursorY));
        boolean isRightHover = (padTouching[1] && key == padHoverKey[1])
                || (this.joySelectorActive && key == this.joyRightKey)
                || (this.dualCursorActive && !padTouching[1] && !this.joySelectorActive && this.splitMode && key.contains(this.rightCursorX, this.rightCursorY));
        boolean hovered = key.contains((double) mouseX, (double) mouseY) || isLeftHover || isRightHover;
        boolean active = (key.type == KeyType.SHIFT && this.shifted)
                || (key.type == KeyType.CAPS && this.capsLock)
                || (key.type == KeyType.SYMBOLS && this.symbols);

        int bgColor;
        if (!hovered && !active) {
            if (key.type == KeyType.ENTER) {
                bgColor = 0xFF104A7B;
            } else if (key.type == KeyType.BACKSPACE) {
                bgColor = 0xFF7B2020;
            } else if (key.type == KeyType.CLOSE) {
                bgColor = 0xFF442222;
            } else if (key.type == KeyType.LAYOUT || key.type == KeyType.SIZE) {
                bgColor = 0xFF1E3A4A;
            } else if (key.type == KeyType.QUICK_COMMAND || key.type == KeyType.TAB) {
                bgColor = 0xFF162E3B;
            } else {
                bgColor = 0xFF1D2128;
            }
        } else {
            bgColor = isLeftHover ? 0xFF1A3B45 : (isRightHover ? 0xFF3D3216 : 0xFF2A3D52);
        }

        graphics.fill(key.x, key.y, key.x + key.width, key.y + key.height, bgColor);

        int borderColor;
        if (isLeftHover) {
            borderColor = 0xFF00BFFF; // Blue/Cyan outline for left joystick / pad selector
        } else if (isRightHover) {
            borderColor = 0xFFFF9500; // Orange outline for right joystick / pad selector
        } else if (!hovered && !active) {
            borderColor = 0x3300FFFF;
        } else {
            borderColor = 0xFF00E5FF;
        }
        graphics.fill(key.x, key.y, key.x + key.width, key.y + 1, borderColor);
        graphics.fill(key.x, key.y + key.height - 1, key.x + key.width, key.y + key.height, borderColor);
        if (isLeftHover || isRightHover) {
            graphics.fill(key.x, key.y, key.x + 1, key.y + key.height, borderColor);
            graphics.fill(key.x + key.width - 1, key.y, key.x + key.width, key.y + key.height, borderColor);
        }

        String label;
        if (key.type == KeyType.SYMBOLS) {
            label = this.symbols ? "ABC" : "?123";
        } else if (key.type == KeyType.LAYOUT) {
            label = this.splitMode ? "Full" : "Split";
        } else if (key.type == KeyType.SIZE) {
            label = this.getSizeLabel();
        } else {
            label = key.getDisplayLabel(this.shifted || this.capsLock, this.symbols);
        }

        int textY = key.y + (key.height - 7) / 2;
        int textColor = (key.type == KeyType.QUICK_COMMAND) ? 0xFF00FFCC : -1;
        graphics.centeredText(font, label, key.x + key.width / 2, textY, textColor);
    }

    private void renderPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xDD0D1117);
        graphics.fill(x, y, x + width, y + 1, 0xFF00E5FF);
        graphics.fill(x, y + 1, x + width, y + 2, 0x4400E5FF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x4400E5FF);
        graphics.fill(x, y, x + 1, y + height, 0x4400E5FF);
        graphics.fill(x + width - 1, y, x + width, y + height, 0x4400E5FF);
    }

    public void render(GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY) {
        if (!this.open && !this.hasTextInput(screen)) {
            return;
        }

        this.updateLayout(screen.width, screen.height);
        Font font = Minecraft.getInstance().font;

        if (!this.open) {
            // Render closed mini KB button
            boolean hovered = mouseX >= this.toggleX && mouseX <= this.toggleX + this.toggleW
                    && mouseY >= this.toggleY && mouseY <= this.toggleY + this.toggleH;
            int bgColor = hovered ? 0xDD1E3A4A : 0xAA0D1117;
            int textColor = hovered ? 0xFF00FFCC : 0xFF00E5FF;
            graphics.fill(this.toggleX, this.toggleY, this.toggleX + this.toggleW, this.toggleY + this.toggleH, bgColor);
            graphics.fill(this.toggleX, this.toggleY, this.toggleX + this.toggleW, this.toggleY + 1, 0xFF00E5FF);
            graphics.fill(this.toggleX, this.toggleY + this.toggleH - 1, this.toggleX + this.toggleW, this.toggleY + this.toggleH, 0xFF00E5FF);
            graphics.fill(this.toggleX, this.toggleY, this.toggleX + 1, this.toggleY + this.toggleH, 0xFF00E5FF);
            graphics.fill(this.toggleX + this.toggleW - 1, this.toggleY, this.toggleX + this.toggleW, this.toggleY + this.toggleH, 0xFF00E5FF);
            graphics.centeredText(font, "KB", this.toggleX + this.toggleW / 2, this.toggleY + (this.toggleH - 7) / 2, textColor);
        } else {
            // Controller legend above the keyboard (only while a controller is the input device)
            if (com.steamdeck.mc.input.SteamDeckInputManager.getInstance().isControllerContext()) {
                String legend = font.plainSubstrByWidth(
                        "A Taste  •  X Löschen  •  Y Leer  •  Start Senden  •  B Aus  •  L3/R3/L/R tippen", screen.width - 12);
                int lw = font.width(legend);
                int lx = (screen.width - lw) / 2;
                int ly = this.kbY - 11;
                graphics.fill(lx - 4, ly - 2, lx + lw + 4, ly + 9, 0xAA0D1117);
                graphics.text(font, legend, lx, ly, 0xFF88CCFF, false);
            }
            // Render Keyboard Panels
            if (this.splitMode) {
                this.renderPanel(graphics, this.leftX, this.kbY, this.leftWidth, this.kbHeight);
                for (List<Key> row : this.leftRows) {
                    for (Key key : row) {
                        this.renderKey(graphics, font, key, mouseX, mouseY);
                    }
                }

                this.renderPanel(graphics, this.rightX, this.kbY, this.rightWidth, this.kbHeight);
                for (List<Key> row : this.rightRows) {
                    for (Key key : row) {
                        this.renderKey(graphics, font, key, mouseX, mouseY);
                    }
                }

                // Render Dual SteamOS Trackpad Cursors in Split Mode (only when trackpads are touched)
                if (padTouching[0]) {
                    renderReticle(graphics, this.leftCursorX, this.leftCursorY, 0xFF00FFCC, 0x8800E5FF);
                }
                if (padTouching[1]) {
                    renderReticle(graphics, this.rightCursorX, this.rightCursorY, 0xFFFFB300, 0x88FFAA00);
                }
            } else {
                this.renderPanel(graphics, this.fullX, this.kbY, this.fullWidth, this.kbHeight);
                for (List<Key> row : this.fullRows) {
                    for (Key key : row) {
                        this.renderKey(graphics, font, key, mouseX, mouseY);
                    }
                }
                // Full layout: show reticle only when a trackpad is touched
                if (padTouching[0]) renderReticle(graphics, this.leftCursorX, this.leftCursorY, 0xFF00FFCC, 0x8800E5FF);
                if (padTouching[1]) renderReticle(graphics, this.rightCursorX, this.rightCursorY, 0xFFFFB300, 0x88FFAA00);
            }
        }
    }

    /** Which pointer moved last (A types with that one). */
    private boolean lastCursorRight = true;

    /**
     * Discrete key-to-key navigation for Left Stick (blue/cyan) and Right Stick (orange).
     */
    public void navigateStick(boolean right, int dx, int dy) {
        if (!this.open || (dx == 0 && dy == 0)) return;
        List<List<Key>> rows = this.splitMode ? (right ? this.rightRows : this.leftRows) : this.fullRows;
        if (rows.isEmpty()) return;

        // Clear trackpad mode if switching back to sticks
        this.joySelectorActive = true;
        this.lastCursorRight = right;

        Key current = right ? this.joyRightKey : this.joyLeftKey;
        int curRow = -1;
        int curCol = -1;
        if (current != null) {
            for (int r = 0; r < rows.size(); r++) {
                int c = rows.get(r).indexOf(current);
                if (c >= 0) {
                    curRow = r;
                    curCol = c;
                    break;
                }
            }
        }
        if (curRow < 0) {
            curRow = Math.min(1, rows.size() - 1);
            List<Key> row = rows.get(curRow);
            curCol = right ? Math.min(row.size() - 1, Math.max(0, row.size() * 3 / 4)) : Math.min(row.size() - 1, row.size() / 4);
            current = row.get(curCol);
        } else {
            if (dy != 0) {
                float targetCenterX = current.x + current.width * 0.5f;
                curRow = (curRow + dy + rows.size()) % rows.size();
                List<Key> nextRow = rows.get(curRow);
                int bestCol = 0;
                float bestDist = Float.MAX_VALUE;
                for (int c = 0; c < nextRow.size(); c++) {
                    Key k = nextRow.get(c);
                    float dist = Math.abs((k.x + k.width * 0.5f) - targetCenterX);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestCol = c;
                    }
                }
                current = nextRow.get(bestCol);
            } else {
                List<Key> row = rows.get(curRow);
                curCol = (curCol + dx + row.size()) % row.size();
                current = row.get(curCol);
            }
        }

        if (right) {
            this.joyRightKey = current;
            this.rightCursorX = current.x + current.width * 0.5f;
            this.rightCursorY = current.y + current.height * 0.5f;
        } else {
            this.joyLeftKey = current;
            this.leftCursorX = current.x + current.width * 0.5f;
            this.leftCursorY = current.y + current.height * 0.5f;
        }
    }

    public void moveLeftCursor(float dx, float dy) {
        this.dualCursorActive = true;
        this.lastCursorRight = false;
        float minX = this.splitMode ? this.leftX + 4 : this.fullX + 4;
        float maxX = this.splitMode ? this.leftX + this.leftWidth - 4 : this.fullX + this.fullWidth - 4;
        this.leftCursorX = Math.max(minX, Math.min(maxX, this.leftCursorX + dx));
        this.leftCursorY = Math.max(kbY + 4, Math.min(kbY + kbHeight - 4, this.leftCursorY + dy));
    }

    public void moveRightCursor(float dx, float dy) {
        this.dualCursorActive = true;
        this.lastCursorRight = true;
        float minX = this.splitMode ? this.rightX + 4 : this.fullX + 4;
        float maxX = this.splitMode ? this.rightX + this.rightWidth - 4 : this.fullX + this.fullWidth - 4;
        this.rightCursorX = Math.max(minX, Math.min(maxX, this.rightCursorX + dx));
        this.rightCursorY = Math.max(kbY + 4, Math.min(kbY + kbHeight - 4, this.rightCursorY + dy));
    }

    public void updateLeftCursorFromMouse(double mouseX, double mouseY) {
        if (mouseX >= leftX && mouseX <= leftX + leftWidth && mouseY >= kbY && mouseY <= kbY + kbHeight) {
            this.leftCursorX = (float) mouseX;
            this.leftCursorY = (float) mouseY;
            this.dualCursorActive = true;
        }
    }

    public void updateRightCursorFromMouse(double mouseX, double mouseY) {
        if (mouseX >= rightX && mouseX <= rightX + rightWidth && mouseY >= kbY && mouseY <= kbY + kbHeight) {
            this.rightCursorX = (float) mouseX;
            this.rightCursorY = (float) mouseY;
            this.dualCursorActive = true;
        }
    }

    /**
     * Absolute trackpad position (0..1 on the pad) for the left ({@code right = false}) or right pad.
     * The pad covers its half of the keyboard (split: its own panel; full layout: left/right half),
     * inset slightly so the outer keys are easy to reach at the pad's edge.
     * Pass {@code touching = false} when the finger lifts.
     */
    public void setPadCursor(boolean right, float nx, float ny, boolean touching) {
        int side = right ? 1 : 0;
        if (!this.open || !touching) {
            padTouching[side] = false;
            padHoverKey[side] = null;
            return;
        }
        // SteamOS layout (virtualkeyboard.css): each pad drives a pointer area of 55 % keyboard width,
        // left 0-55 %, right 45-100 % (10 % overlap in the middle). In the split layout each pad simply
        // covers its own panel.
        float areaX, areaW;
        if (this.splitMode) {
            areaX = right ? this.rightX : this.leftX;
            areaW = right ? this.rightWidth : this.leftWidth;
        } else if (!com.steamdeck.mc.input.SteamDeckInputManager.getInstance().getActiveKind().hasDualTouchpads()) {
            // Single center touchpad (e.g. DualSense): cover the full keyboard width
            areaX = this.fullX;
            areaW = this.fullWidth;
        } else {
            areaW = this.fullWidth * 0.55f;
            areaX = right ? this.fullX + this.fullWidth - areaW : this.fullX;
        }
        // Linear mapping like SteamOS: x in [-1, 1], scaled by TrackPadTypingInputScale and clamped
        float s = ModConfig.getInstance().oskTrackpadScale;
        float ux = Math.max(-1.0f, Math.min(1.0f, (nx * 2.0f - 1.0f) * s));
        float uy = Math.max(-1.0f, Math.min(1.0f, (ny * 2.0f - 1.0f) * s));
        float px = areaX + areaW * 0.5f * (1.0f + ux);
        float py = this.kbY + this.kbHeight * 0.5f * (1.0f + uy);
        if (right) {
            this.rightCursorX = px;
            this.rightCursorY = py;
        } else {
            this.leftCursorX = px;
            this.leftCursorY = py;
        }
        this.dualCursorActive = true;
        this.joySelectorActive = false;
        this.lastCursorRight = right;
        padTouching[side] = true;

        // Key under the finger with hysteresis, haptic tick when it changes (like SteamOS)
        Key current = padHoverKey[side];
        boolean keep = current != null && px >= current.x - KEY_HYSTERESIS && px < current.x + current.width + KEY_HYSTERESIS
                && py >= current.y - KEY_HYSTERESIS && py < current.y + current.height + KEY_HYSTERESIS;
        if (!keep) {
            Key next = this.splitMode ? getKeyAt(px, py, !right) : getKeyAt(px, py, true);
            if (next == null) next = nearestKey(px, py, right);
            if (next != current && next != null && current != null) {
                com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerPadHaptic(right, false, (short) 16000, 12);
            }
            padHoverKey[side] = next;
        }
    }

    public boolean isPadTouching(boolean right) {
        return padTouching[right ? 1 : 0];
    }

    /** Closest key to a point in the pad's area (for the gaps between keys). */
    private Key nearestKey(float x, float y, boolean right) {
        List<List<Key>> rows = this.splitMode ? (right ? this.rightRows : this.leftRows) : this.fullRows;
        Key best = null;
        double bestD = Double.MAX_VALUE;
        for (List<Key> row : rows) {
            for (Key key : row) {
                double cx = Math.max(key.x, Math.min(key.x + key.width, x));
                double cy = Math.max(key.y, Math.min(key.y + key.height, y));
                double d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
                if (d < bestD) {
                    bestD = d;
                    best = key;
                }
            }
        }
        return best;
    }

    /** A button: types the key under the pointer that was moved last. */
    public boolean triggerLastCursorClick(Screen screen) {
        return this.lastCursorRight ? triggerRightCursorClick(screen) : triggerLeftCursorClick(screen);
    }

    /** Presses a special key (backspace, space, enter, ...) without aiming at it. */
    public void tapAction(KeyType type, Screen screen) {
        if (!this.open || screen == null) return;
        this.handleKeyTap(Key.action("", type, 1.0f), screen, 0);
        playClickSound();
    }

    public boolean triggerLeftCursorClick(Screen screen) {
        if (!this.open) return false;
        Key key = padTouching[0] && padHoverKey[0] != null ? padHoverKey[0]
                : (this.joySelectorActive && this.joyLeftKey != null ? this.joyLeftKey
                : getKeyAt(this.leftCursorX, this.leftCursorY, true));
        if (key != null) {
            this.handleKeyTap(key, screen, 0);
            playClickSound();
            com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(-1);
            return true;
        }
        return false;
    }

    public boolean triggerRightCursorClick(Screen screen) {
        if (!this.open) return false;
        Key key = padTouching[1] && padHoverKey[1] != null ? padHoverKey[1]
                : (this.joySelectorActive && this.joyRightKey != null ? this.joyRightKey
                : getKeyAt(this.rightCursorX, this.rightCursorY, !this.splitMode));
        if (key != null) {
            this.handleKeyTap(key, screen, 0);
            playClickSound();
            com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(1);
            return true;
        }
        return false;
    }

    private Key getKeyAt(float x, float y, boolean isLeftPanel) {
        List<List<Key>> rows = this.splitMode ? (isLeftPanel ? this.leftRows : this.rightRows) : this.fullRows;
        for (List<Key> row : rows) {
            for (Key key : row) {
                if (key.contains((double) x, (double) y)) {
                    return key;
                }
            }
        }
        return null;
    }

    private void renderReticle(GuiGraphicsExtractor graphics, float cx, float cy, int borderColor, int fillColor) {
        int x = Math.round(cx);
        int y = Math.round(cy);
        int r = 5;
        // Reticle ring
        graphics.fill(x - r, y - 1, x + r + 1, y + 2, fillColor);
        graphics.fill(x - 1, y - r, x + 2, y + r + 1, fillColor);
        graphics.fill(x - r - 1, y - 1, x - r, y + 2, borderColor);
        graphics.fill(x + r + 1, y - 1, x + r + 2, y + 2, borderColor);
        graphics.fill(x - 1, y - r - 1, x + 2, y - r, borderColor);
        graphics.fill(x - 1, y + r + 1, x + 2, y + r + 2, borderColor);
        // Center cross dot
        graphics.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
    }

    public boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
        if (!this.open && !this.hasTextInput(screen)) {
            return false;
        }

        if (!this.open) {
            if (mouseX >= this.toggleX && mouseX <= (this.toggleX + this.toggleW)
                    && mouseY >= this.toggleY && mouseY <= (this.toggleY + this.toggleH)) {
                this.open = true;
                playClickSound();
                return true;
            }
            return false;
        }

        // Keyboard Panels
        if (this.splitMode) {
            if (mouseX >= this.leftX && mouseX <= (this.leftX + this.leftWidth)
                    && mouseY >= this.kbY && mouseY <= (this.kbY + this.kbHeight)) {
                for (List<Key> row : this.leftRows) {
                    for (Key key : row) {
                        if (key.contains(mouseX, mouseY)) {
                            this.handleKeyTap(key, screen, button);
                            playClickSound();
                            return true;
                        }
                    }
                }
                return true;
            }

            if (mouseX >= this.rightX && mouseX <= (this.rightX + this.rightWidth)
                    && mouseY >= this.kbY && mouseY <= (this.kbY + this.kbHeight)) {
                for (List<Key> row : this.rightRows) {
                    for (Key key : row) {
                        if (key.contains(mouseX, mouseY)) {
                            this.handleKeyTap(key, screen, button);
                            playClickSound();
                            return true;
                        }
                    }
                }
                return true;
            }
        } else {
            if (mouseX >= this.fullX && mouseX <= (this.fullX + this.fullWidth)
                    && mouseY >= this.kbY && mouseY <= (this.kbY + this.kbHeight)) {
                for (List<Key> row : this.fullRows) {
                    for (Key key : row) {
                        if (key.contains(mouseX, mouseY)) {
                            this.handleKeyTap(key, screen, button);
                            playClickSound();
                            return true;
                        }
                    }
                }
                return true;
            }
        }

        return false;
    }

    private void handleKeyTap(Key key, Screen screen, int button) {
        EditBox editBox = this.getActiveEditBox(screen);
        switch (key.type) {
            case CHAR -> {
                String label = key.getDisplayLabel(this.shifted || this.capsLock, this.symbols);
                if (editBox != null) {
                    editBox.insertText(label);
                } else {
                    for (char c : label.toCharArray()) {
                        screen.charTyped(new CharacterEvent(c));
                    }
                }
                if (this.shifted && !this.capsLock) {
                    this.shifted = false;
                }
            }
            case QUICK_COMMAND -> {
                if (editBox != null) {
                    editBox.setValue(key.normal);
                } else {
                    for (char c : key.normal.toCharArray()) {
                        screen.charTyped(new CharacterEvent(c));
                    }
                }
            }
            case TAB -> {
                screen.keyPressed(new KeyEvent(InputConstants.KEY_TAB, InputConstants.KEYCODE_TAB, 0));
            }
            case BACKSPACE -> {
                if (editBox != null) {
                    editBox.deleteChars(-1);
                } else {
                    screen.keyPressed(new KeyEvent(InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE, 0));
                }
            }
            case ENTER -> {
                screen.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
            }
            case SPACE -> {
                if (editBox != null) {
                    editBox.insertText(" ");
                } else {
                    screen.charTyped(new CharacterEvent(' '));
                }
            }
            case SHIFT -> this.shifted = !this.shifted;
            case CAPS -> {
                this.capsLock = !this.capsLock;
                this.shifted = this.capsLock;
            }
            case SYMBOLS -> this.symbols = !this.symbols;
            case LEFT -> {
                if (editBox != null) {
                    editBox.moveCursor(-1, false);
                } else {
                    screen.keyPressed(new KeyEvent(InputConstants.KEY_LEFT, InputConstants.KEYCODE_LEFT, 0));
                }
            }
            case RIGHT -> {
                if (editBox != null) {
                    editBox.moveCursor(1, false);
                } else {
                    screen.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, InputConstants.KEYCODE_RIGHT, 0));
                }
            }
            case CLOSE -> this.open = false;
            case LAYOUT -> {
                this.splitMode = !this.splitMode;
                ModConfig.getInstance().oskSplitMode = this.splitMode;
                ModConfig.getInstance().save();
            }
            case SIZE -> {
                if (button == 3) {
                    this.cycleScaleBackwards();
                } else {
                    this.cycleScale();
                }
            }
        }
    }

    public enum KeyType {
        CHAR,
        BACKSPACE,
        ENTER,
        SPACE,
        SHIFT,
        CAPS,
        SYMBOLS,
        LEFT,
        RIGHT,
        CLOSE,
        LAYOUT,
        SIZE,
        QUICK_COMMAND,
        TAB
    }

    public static class Key {
        public final String normal;
        public final String shifted;
        public final String symbol;
        public final KeyType type;
        public final float weight;
        public int x;
        public int y;
        public int width;
        public int height;

        public Key(String normal, String shifted, String symbol, KeyType type, float weight) {
            this.normal = normal;
            this.shifted = shifted;
            this.symbol = symbol;
            this.type = type;
            this.weight = weight;
        }

        public static Key character(String normal, String shifted, String symbol) {
            return new Key(normal, shifted, symbol, KeyType.CHAR, 1.0F);
        }

        public static Key character(String normal, String shifted, String symbol, float weight) {
            return new Key(normal, shifted, symbol, KeyType.CHAR, weight);
        }

        public static Key action(String normal, KeyType type, float weight) {
            return new Key(normal, normal, normal, type, weight);
        }

        public String getDisplayLabel(boolean isShifted, boolean isSymbol) {
            if (isSymbol && this.symbol != null && !this.symbol.isEmpty()) {
                return isShifted ? this.symbol.toUpperCase() : this.symbol;
            } else {
                return isShifted && this.shifted != null && !this.shifted.isEmpty() ? this.shifted : this.normal;
            }
        }

        public boolean contains(double px, double py) {
            return px >= (double) this.x && px <= (double) (this.x + this.width)
                    && py >= (double) this.y && py <= (double) (this.y + this.height);
        }
    }
}
