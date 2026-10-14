package com.verdant.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.verdant.config.VerdantConfig;
import com.verdant.config.VerdantConfig.Module;
import com.verdant.config.VerdantConfig.Target;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Verdant click GUI. Two panels in the style of the Figma reference:
 * "Visuals" (the two modules and their settings) and "Colors" (per-block color editor).
 * Everything is drawn in "immediate mode": each frame the layout is rebuilt and the
 * clickable areas are collected, so there are no widget objects to keep in sync.
 */
public final class VerdantScreen extends Screen {
    // ---- theme (ARGB) ----
    private static final int OVERLAY = 0xE0050E0B;
    private static final int PANEL_BORDER = 0xFF244636;
    private static final int PANEL_BODY = 0xFF0C2019;
    private static final int PANEL_HEAD = 0xFF1A3928;
    private static final int SECTION = 0xFF0A1B14;
    private static final int SECTION_SEL = 0xFF173A2A;
    private static final int DIVIDER = 0xFF1E382B;
    private static final int HOVER = 0x2A5CE0A8;
    private static final int ACCENT = 0xFF5CE0A8;
    private static final int ACCENT_DEEP = 0xFF24543E;
    private static final int TEXT = 0xFFE8F3EC;
    private static final int TEXT_DIM = 0xFF9DB5A8;
    private static final int TEXT_FAINT = 0xFF62786C;
    private static final int TOGGLE_OFF = 0xFF3A4A42;
    private static final int KNOB = 0xFFF0F8F3;
    private static final int TRACK = 0xFF264235;

    // ---- layout ----
    private static final int GAP = 14;
    private static final int PANEL_HEAD_H = 38;
    private static final int CONTENT_TOP = 52;
    private static final int FOOTER_H = 28;

    private static final float[][] PRESETS = {
            {0.43f, 0.70f, 0.90f}, // mint
            {0.33f, 0.80f, 0.85f}, // green
            {0.20f, 0.90f, 1.00f}, // lime
            {0.12f, 0.90f, 1.00f}, // gold
            {0.07f, 0.90f, 1.00f}, // orange
            {0.00f, 0.85f, 1.00f}, // red
            {0.92f, 0.60f, 1.00f}, // pink
            {0.75f, 0.70f, 1.00f}, // purple
            {0.60f, 0.80f, 1.00f}, // blue
            {0.50f, 0.80f, 1.00f}, // cyan
            {0.00f, 0.00f, 1.00f}, // white
    };

    // ---- click handling ----
    @FunctionalInterface
    private interface Action {
        void run(double px, int button);
    }

    private record Region(int x, int y, int w, int h, boolean scrolled, Action action) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private static final class Slider {
        final int x;
        final int w;
        final float min;
        final float max;
        final float step;
        final Consumer<Float> setter;

        Slider(int x, int w, float min, float max, float step, Consumer<Float> setter) {
            this.x = x;
            this.w = w;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
        }

        void update(double px) {
            float frac = (float) Math.max(0.0, Math.min(1.0, (px - x) / (double) w));
            float v = min + frac * (max - min);
            if (step > 0f) v = Math.round(v / step) * step;
            v = Math.max(min, Math.min(max, v));
            setter.accept(v);
        }
    }

    private final List<Region> regions = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private Target selected = VerdantConfig.CHEST;
    private Slider dragging;
    private boolean dirty;
    private float scroll;

    // per-frame drawing state
    private GuiGraphics g;
    private Font ui;
    private int mx;
    private int my;
    private boolean measure;      // true = only compute sizes, draw nothing
    private boolean scrolledMode; // true = inside the scrolling area

    public VerdantScreen() {
        super(Component.literal("Verdant"));
        expanded.add("storage");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        VerdantConfig.save();
        super.onClose();
    }

    // =====================================================================
    // Frame
    // =====================================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.g = graphics;
        this.ui = Minecraft.getInstance().font;
        this.mx = mouseX;
        this.my = mouseY;
        regions.clear();
        measure = false;
        scrolledMode = false;

        graphics.fill(0, 0, width, height, OVERLAY);

        int pw = Math.max(170, Math.min(236, (width - 24 - GAP) / 2));
        int totalW = pw * 2 + GAP;
        int left = (width - totalW) / 2;
        int right = left + totalW;

        drawHeader(left, right);
        drawFooter(left, right);

        // measure both panels so they end up the same height, like the reference
        measure = true;
        scrolledMode = true;
        int h1 = visualsContent(0, 0, pw);
        int h2 = colorsContent(0, 0, pw);
        measure = false;
        int panelH = PANEL_HEAD_H + Math.max(h1, h2) + 8;

        int viewTop = CONTENT_TOP;
        int viewBottom = height - FOOTER_H;
        int maxScroll = Math.max(0, panelH + 10 - (viewBottom - viewTop));
        scroll = Math.max(0f, Math.min((float) maxScroll, scroll));
        int top = viewTop - (int) scroll;

        graphics.enableScissor(0, viewTop, width, viewBottom);
        scrolledMode = true;
        panel(left, top, pw, panelH, "Visuals", "Block highlights", "o");
        visualsContent(left, top + PANEL_HEAD_H, pw);
        int rx = left + pw + GAP;
        panel(rx, top, pw, panelH, "Colors", "Per-block customization", "#");
        colorsContent(rx, top + PANEL_HEAD_H, pw);
        scrolledMode = false;
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackH = viewBottom - viewTop;
            int barH = Math.max(20, trackH * trackH / (trackH + maxScroll));
            int barY = viewTop + (int) ((trackH - barH) * (scroll / maxScroll));
            graphics.fill(right + 6, barY, right + 8, barY + barH, ACCENT_DEEP);
        }
    }

    private void drawHeader(int left, int right) {
        int y = 12;
        rrect(left, y, 26, 26, 6, PANEL_BORDER);
        rrect(left + 1, y + 1, 24, 24, 5, PANEL_HEAD);
        text("V", left + 13 - tw("V") / 2, y + 9, ACCENT);
        text("VERDANT", left + 34, y + 4, TEXT);
        text("Client utilities", left + 34, y + 16, TEXT_FAINT);

        int closeX = right - 24;
        boolean overClose = hover(closeX, y + 1, 24, 24);
        rrect(closeX, y + 1, 24, 24, 5, overClose ? PANEL_BORDER : PANEL_HEAD);
        text("x", closeX + 12 - tw("x") / 2, y + 9, TEXT_DIM);
        addRegion(closeX, y + 1, 24, 24, (px, b) -> onClose());

        int resetW = 54;
        int resetX = closeX - 8 - resetW;
        button("Reset", resetX, y + 1, resetW, 24, () -> {
            VerdantConfig.resetAll();
            selected = VerdantConfig.CHEST;
        });
    }

    private void drawFooter(int left, int right) {
        int y = height - FOOTER_H + 8;
        rrect(left, y, 44, 14, 4, PANEL_HEAD);
        text("RSHIFT", left + 6, y + 3, TEXT_DIM);
        text("opens this menu   |   ESC closes   |   mouse wheel scrolls", left + 52, y + 3, TEXT_FAINT);
        String saved = "Changes save automatically";
        text(saved, right - tw(saved), y + 3, TEXT_FAINT);
    }

    private void panel(int x, int y, int w, int h, String title, String sub, String glyph) {
        rrect(x, y, w, h, 5, PANEL_BORDER);
        rrect(x + 1, y + 1, w - 2, h - 2, 4, PANEL_BODY);
        rrect(x + 1, y + 1, w - 2, PANEL_HEAD_H + 6, 4, PANEL_HEAD);
        fill(x + 1, y + PANEL_HEAD_H, w - 2, 6, PANEL_BODY);
        fill(x + 1, y + PANEL_HEAD_H, w - 2, 1, DIVIDER);
        rrect(x + 10, y + 10, 18, 18, 4, ACCENT_DEEP);
        text(glyph, x + 19 - tw(glyph) / 2, y + 15, ACCENT);
        text(title, x + 36, y + 9, TEXT);
        text(sub, x + 36, y + 21, TEXT_FAINT);
    }

    // =====================================================================
    // "Visuals" panel
    // =====================================================================

    private int visualsContent(int x, int y, int w) {
        int cy = y;
        cy += moduleBlock("storage", "Storage ESP", "Chests, hoppers, shulkers", VerdantConfig.STORAGE, x, cy, w);
        cy += moduleBlock("spawner", "Spawn Finder", "Mob spawners", VerdantConfig.SPAWNER, x, cy, w);
        return cy - y;
    }

    private int moduleBlock(String id, String name, String sub, Module m, int x, int y, int w) {
        final int rowH = 30;
        int start = y;
        boolean open = expanded.contains(id);

        if (!measure) {
            if (hover(x, y, w, rowH)) fill(x + 1, y, w - 2, rowH, HOVER);
            fill(x + 1, y, w - 2, 1, DIVIDER);
            text(name, x + 12, y + 6, m.enabled ? TEXT : TEXT_DIM);
            text(sub, x + 12, y + 17, TEXT_FAINT);
            toggle(x + w - 52, y + 9, m.enabled);
            chevron(x + w - 18, y + 13, open, TEXT_DIM);
            addRegion(x, y, w, rowH, (px, b) -> {
                if (!expanded.remove(id)) expanded.add(id);
            });
            addRegion(x + w - 58, y, 36, rowH, (px, b) -> m.enabled = !m.enabled);
        }
        y += rowH;

        if (open) {
            int ix = x + 8;
            int iw = w - 16;
            boolean prev = measure;
            measure = true;
            int sh = moduleSettings(m, ix, y + 2, iw);
            measure = prev;
            if (!measure) rrect(ix, y + 2, iw, sh, 5, SECTION);
            moduleSettings(m, ix, y + 2, iw);
            y += sh + 2 + 8;
        }
        return y - start;
    }

    private int moduleSettings(Module m, int x, int y, int w) {
        int cy = y + 4;
        cy += sliderRow("Range", Math.round(m.range) + " m", m.range, 8f, 128f, 1f, v -> m.range = v, x, cy, w, false);
        cy += toggleRow("Through walls", m.throughWalls, v -> m.throughWalls = v, x, cy, w);
        cy += sliderRow("Outline width", String.format("%.1f", m.outlineWidth), m.outlineWidth, 1f, 10f, 0.5f,
                v -> m.outlineWidth = v, x, cy, w, false);
        cy += sliderRow("Outline opacity", pct(m.outlineOpacity), m.outlineOpacity, 0f, 1f, 0.05f,
                v -> m.outlineOpacity = v, x, cy, w, false);
        cy += toggleRow("Fill boxes", m.fill, v -> m.fill = v, x, cy, w);
        if (m.fill) {
            cy += sliderRow("Fill opacity", pct(m.fillOpacity), m.fillOpacity, 0.05f, 1f, 0.05f,
                    v -> m.fillOpacity = v, x, cy, w, false);
        }
        cy += toggleRow("Fade with distance", m.distanceFade, v -> m.distanceFade = v, x, cy, w);
        cy += toggleRow("Rainbow colors", m.rainbow, v -> m.rainbow = v, x, cy, w);
        if (m.rainbow) {
            cy += sliderRow("Rainbow speed", String.format("%.1fx", m.rainbowSpeed), m.rainbowSpeed, 0.2f, 4f, 0.1f,
                    v -> m.rainbowSpeed = v, x, cy, w, false);
        }
        return cy + 4 - y;
    }

    // =====================================================================
    // "Colors" panel
    // =====================================================================

    private int colorsContent(int x, int y, int w) {
        int ix = x + 8;
        int iw = w - 16;
        int cy = y + 4;

        cy += sectionLabel("STORAGE ESP", x, cy);
        for (Target t : VerdantConfig.TARGETS) {
            if (t.module == VerdantConfig.STORAGE) cy += targetRow(t, x, cy, w);
        }
        cy += sectionLabel("SPAWN FINDER", x, cy);
        for (Target t : VerdantConfig.TARGETS) {
            if (t.module == VerdantConfig.SPAWNER) cy += targetRow(t, x, cy, w);
        }

        cy += 4;
        fill(x + 1, cy, w - 2, 1, DIVIDER);
        cy += 8;

        final Target t = selected;
        cy += sectionLabel("EDITING: " + t.label.toUpperCase(), x, cy);

        if (!measure) {
            int rgb = t.rgb();
            rrect(ix, cy, iw, 20, 5, PANEL_BORDER);
            rrect(ix + 1, cy + 1, iw - 2, 18, 4, 0xFF000000 | rgb);
            String hex = String.format("#%06X", rgb);
            int r = (rgb >> 16) & 255;
            int gr = (rgb >> 8) & 255;
            int b = rgb & 255;
            int luminance = (r * 299 + gr * 587 + b * 114) / 1000;
            text(hex, ix + iw / 2 - tw(hex) / 2, cy + 6, luminance > 140 ? 0xFF0B1410 : 0xFFFFFFFF);
        }
        cy += 28;

        cy += sliderRow("Hue", Math.round(t.hue * 360f) + " deg", t.hue, 0f, 1f, 0.0025f, v -> t.hue = v, x, cy, w, true);
        cy += sliderRow("Saturation", pct(t.sat), t.sat, 0f, 1f, 0.01f, v -> t.sat = v, x, cy, w, false);
        cy += sliderRow("Brightness", pct(t.bri), t.bri, 0f, 1f, 0.01f, v -> t.bri = v, x, cy, w, false);

        // preset swatches
        if (!measure) {
            int n = PRESETS.length;
            int gap = 3;
            int sw = (iw - gap * (n - 1)) / n;
            for (int i = 0; i < n; i++) {
                final float[] p = PRESETS[i];
                int sx = ix + i * (sw + gap);
                int c = 0xFF000000 | VerdantConfig.hsbToRgb(p[0], p[1], p[2]);
                boolean current = Math.abs(t.hue - p[0]) < 0.004f && Math.abs(t.sat - p[1]) < 0.01f && Math.abs(t.bri - p[2]) < 0.01f;
                rrect(sx - 1, cy - 1, sw + 2, 16, 4, current ? ACCENT : PANEL_BORDER);
                rrect(sx, cy, sw, 14, 3, c);
                addRegion(sx, cy, sw, 14, (px, bt) -> {
                    t.hue = p[0];
                    t.sat = p[1];
                    t.bri = p[2];
                    dirty = true;
                });
            }
        }
        cy += 24;

        String applyLabel = t.module == VerdantConfig.STORAGE ? "Apply to all storage blocks" : "Apply to all spawner blocks";
        button(applyLabel, ix, cy, iw, 20, () -> {
            for (Target other : VerdantConfig.TARGETS) {
                if (other.module == t.module) {
                    other.hue = t.hue;
                    other.sat = t.sat;
                    other.bri = t.bri;
                }
            }
            dirty = true;
        });
        cy += 28;

        return cy - y;
    }

    private int sectionLabel(String label, int x, int y) {
        text(label, x + 14, y + 4, TEXT_FAINT);
        return 16;
    }

    private int targetRow(Target t, int x, int y, int w) {
        final int rowH = 22;
        if (!measure) {
            boolean isSelected = t == selected;
            if (isSelected) {
                rrect(x + 6, y, w - 12, rowH, 4, SECTION_SEL);
            } else if (hover(x + 6, y, w - 12, rowH)) {
                rrect(x + 6, y, w - 12, rowH, 4, HOVER);
            }
            rrect(x + 14, y + 6, 10, 10, 3, 0xFF000000 | t.rgb());
            text(t.label, x + 30, y + 7, t.enabled ? TEXT : TEXT_DIM);
            toggle(x + w - 38, y + 5, t.enabled);
            addRegion(x + 6, y, w - 12 - 36, rowH, (px, b) -> selected = t);
            addRegion(x + w - 42, y, 36, rowH, (px, b) -> t.enabled = !t.enabled);
        }
        return rowH;
    }

    // =====================================================================
    // Reusable rows and widgets
    // =====================================================================

    private int toggleRow(String label, boolean value, Consumer<Boolean> setter, int x, int y, int w) {
        final int rowH = 22;
        if (!measure) {
            int ix = x + 8;
            int iw = w - 16;
            if (hover(ix - 2, y, iw + 4, rowH)) rrect(ix - 2, y, iw + 4, rowH, 4, HOVER);
            text(label, ix + 2, y + 7, TEXT_DIM);
            toggle(ix + iw - 26, y + 5, value);
            addRegion(ix - 2, y, iw + 4, rowH, (px, b) -> setter.accept(!value));
        }
        return rowH;
    }

    private int sliderRow(String label, String valueText, float value, float min, float max, float step,
                          Consumer<Float> setter, int x, int y, int w, boolean hueTrack) {
        final int rowH = 30;
        if (!measure) {
            int ix = x + 8;
            int iw = w - 16;
            text(label, ix, y + 2, TEXT_DIM);
            text(valueText, ix + iw - tw(valueText), y + 2, ACCENT);

            float frac = (value - min) / (max - min);
            frac = Math.max(0f, Math.min(1f, frac));
            int trackY = y + 17;
            if (hueTrack) {
                int segments = 36;
                for (int i = 0; i < segments; i++) {
                    int sx = ix + iw * i / segments;
                    int ex = ix + iw * (i + 1) / segments;
                    fill(sx, trackY - 1, ex - sx, 4, 0xFF000000 | VerdantConfig.hsbToRgb(i / (float) segments, 0.85f, 1f));
                }
            } else {
                fill(ix, trackY, iw, 2, TRACK);
                fill(ix, trackY, (int) (iw * frac), 2, ACCENT);
            }
            int knobX = ix + (int) (iw * frac);
            rrect(knobX - 3, trackY - 3, 6, 8, 3, KNOB);

            addRegion(ix - 3, y + 8, iw + 6, 20, (px, b) -> {
                dragging = new Slider(ix, iw, min, max, step, setter);
                dragging.update(px);
                dirty = true;
            });
        }
        return rowH;
    }

    private void button(String label, int x, int y, int w, int h, Runnable onClick) {
        if (measure) return;
        boolean over = hover(x, y, w, h);
        rrect(x, y, w, h, 5, PANEL_BORDER);
        rrect(x + 1, y + 1, w - 2, h - 2, 4, over ? ACCENT_DEEP : PANEL_HEAD);
        text(label, x + w / 2 - tw(label) / 2, y + h / 2 - 4, over ? TEXT : TEXT_DIM);
        addRegion(x, y, w, h, (px, b) -> {
            onClick.run();
            dirty = true;
        });
    }

    private void toggle(int x, int y, boolean on) {
        if (measure) return;
        rrect(x, y, 24, 12, 6, on ? ACCENT : TOGGLE_OFF);
        rrect(on ? x + 13 : x + 1, y + 1, 10, 10, 5, KNOB);
    }

    private void chevron(int cx, int cy, boolean up, int color) {
        if (measure) return;
        for (int i = 0; i < 3; i++) {
            if (up) {
                g.fill(cx - 2 + i, cy + 2 - i, cx + 3 - i, cy + 3 - i, color);
            } else {
                g.fill(cx - 2 + i, cy + i, cx + 3 - i, cy + i + 1, color);
            }
        }
    }

    // =====================================================================
    // Drawing helpers
    // =====================================================================

    private void fill(int x, int y, int w, int h, int color) {
        if (!measure) g.fill(x, y, x + w, y + h, color);
    }

    private void text(String s, int x, int y, int color) {
        if (!measure) g.drawString(ui, s, x, y, color, false);
    }

    private int tw(String s) {
        return ui.width(s);
    }

    /** Rectangle with rounded corners (radius r, in GUI pixels). */
    private void rrect(int x, int y, int w, int h, int r, int color) {
        if (measure) return;
        if (r <= 0 || w < 2 * r || h < 2 * r) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int dx = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + dx, y + i, x + r, y + i + 1, color);
            g.fill(x + w - r, y + i, x + w - dx, y + i + 1, color);
            g.fill(x + dx, y + h - i - 1, x + r, y + h - i, color);
            g.fill(x + w - r, y + h - i - 1, x + w - dx, y + h - i, color);
        }
    }

    private boolean hover(int x, int y, int w, int h) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        if (scrolledMode && (my < CONTENT_TOP || my >= height - FOOTER_H)) return false;
        return true;
    }

    private void addRegion(int x, int y, int w, int h, Action action) {
        if (!measure) regions.add(new Region(x, y, w, h, scrolledMode, action));
    }

    private static String pct(float v) {
        return Math.round(v * 100f) + "%";
    }

    // =====================================================================
    // Input (1.21.11 uses MouseButtonEvent)
    // =====================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double px = event.x();
            double py = event.y();
            for (int i = regions.size() - 1; i >= 0; i--) {
                Region r = regions.get(i);
                if (!r.contains(px, py)) continue;
                if (r.scrolled() && (py < CONTENT_TOP || py >= height - FOOTER_H)) continue;
                r.action().run(px, event.button());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging != null) {
            dragging.update(event.x());
            dirty = true;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = dragging != null;
        dragging = null;
        if (dirty) {
            VerdantConfig.save();
            dirty = false;
        }
        return wasDragging || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (float) (scrollY * 20.0);
        return true;
    }
}
