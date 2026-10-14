package com.verdant.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** All Verdant settings. Saved to .minecraft/config/verdant.properties */
public final class VerdantConfig {
    private VerdantConfig() {}

    /** Settings shared by one module (Storage ESP or Spawn Finder). */
    public static final class Module {
        public final String id;
        public boolean enabled;
        public boolean throughWalls;
        public boolean fill;
        public boolean distanceFade;
        public boolean rainbow;
        public float range;
        public float outlineWidth;
        public float outlineOpacity;
        public float fillOpacity;
        public float rainbowSpeed;
        private final float defaultFillOpacity;

        Module(String id, float defaultFillOpacity) {
            this.id = id;
            this.defaultFillOpacity = defaultFillOpacity;
            reset();
        }

        void reset() {
            enabled = true;
            throughWalls = true;
            fill = true;
            distanceFade = false;
            rainbow = false;
            range = 64f;
            outlineWidth = 3f;
            outlineOpacity = 1f;
            fillOpacity = defaultFillOpacity;
            rainbowSpeed = 1f;
        }

        void load(Properties p) {
            enabled = bool(p, id + ".enabled", enabled);
            throughWalls = bool(p, id + ".throughWalls", throughWalls);
            fill = bool(p, id + ".fill", fill);
            distanceFade = bool(p, id + ".distanceFade", distanceFade);
            rainbow = bool(p, id + ".rainbow", rainbow);
            range = flt(p, id + ".range", range, 8f, 128f);
            outlineWidth = flt(p, id + ".outlineWidth", outlineWidth, 1f, 10f);
            outlineOpacity = flt(p, id + ".outlineOpacity", outlineOpacity, 0f, 1f);
            fillOpacity = flt(p, id + ".fillOpacity", fillOpacity, 0f, 1f);
            rainbowSpeed = flt(p, id + ".rainbowSpeed", rainbowSpeed, 0.2f, 4f);
        }

        void save(Properties p) {
            p.setProperty(id + ".enabled", String.valueOf(enabled));
            p.setProperty(id + ".throughWalls", String.valueOf(throughWalls));
            p.setProperty(id + ".fill", String.valueOf(fill));
            p.setProperty(id + ".distanceFade", String.valueOf(distanceFade));
            p.setProperty(id + ".rainbow", String.valueOf(rainbow));
            p.setProperty(id + ".range", String.valueOf(range));
            p.setProperty(id + ".outlineWidth", String.valueOf(outlineWidth));
            p.setProperty(id + ".outlineOpacity", String.valueOf(outlineOpacity));
            p.setProperty(id + ".fillOpacity", String.valueOf(fillOpacity));
            p.setProperty(id + ".rainbowSpeed", String.valueOf(rainbowSpeed));
        }
    }

    /** One kind of block that can be highlighted, with its own color. */
    public static final class Target {
        public final String id;
        public final String label;
        public final Module module;
        public boolean enabled;
        public float hue;
        public float sat;
        public float bri;
        private final boolean defEnabled;
        private final float defHue;
        private final float defSat;
        private final float defBri;

        Target(String id, String label, Module module, boolean enabled, float hue, float sat, float bri) {
            this.id = id;
            this.label = label;
            this.module = module;
            this.defEnabled = enabled;
            this.defHue = hue;
            this.defSat = sat;
            this.defBri = bri;
            reset();
        }

        void reset() {
            enabled = defEnabled;
            hue = defHue;
            sat = defSat;
            bri = defBri;
        }

        public int rgb() {
            return hsbToRgb(hue, sat, bri);
        }

        void load(Properties p) {
            enabled = bool(p, "color." + id + ".enabled", enabled);
            hue = flt(p, "color." + id + ".hue", hue, 0f, 1f);
            sat = flt(p, "color." + id + ".sat", sat, 0f, 1f);
            bri = flt(p, "color." + id + ".bri", bri, 0f, 1f);
        }

        void save(Properties p) {
            p.setProperty("color." + id + ".enabled", String.valueOf(enabled));
            p.setProperty("color." + id + ".hue", String.valueOf(hue));
            p.setProperty("color." + id + ".sat", String.valueOf(sat));
            p.setProperty("color." + id + ".bri", String.valueOf(bri));
        }
    }

    public static final Module STORAGE = new Module("storage", 0.18f);
    public static final Module SPAWNER = new Module("spawner", 0.25f);

    public static final Target CHEST = new Target("chest", "Chest", STORAGE, true, 0.11f, 0.85f, 1.00f);
    public static final Target TRAPPED_CHEST = new Target("trapped_chest", "Trapped chest", STORAGE, true, 0.00f, 0.85f, 1.00f);
    public static final Target ENDER_CHEST = new Target("ender_chest", "Ender chest", STORAGE, true, 0.77f, 0.70f, 1.00f);
    public static final Target HOPPER = new Target("hopper", "Hopper", STORAGE, true, 0.55f, 0.35f, 0.95f);
    public static final Target SHULKER = new Target("shulker", "Shulker box", STORAGE, true, 0.88f, 0.60f, 1.00f);
    public static final Target BARREL = new Target("barrel", "Barrel", STORAGE, false, 0.07f, 0.70f, 0.85f);
    public static final Target SPAWNER_BLOCK = new Target("spawner", "Mob spawner", SPAWNER, true, 0.74f, 0.75f, 1.00f);

    public static final List<Target> TARGETS =
            List.of(CHEST, TRAPPED_CHEST, ENDER_CHEST, HOPPER, SHULKER, BARREL, SPAWNER_BLOCK);

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("verdant.properties");
    }

    public static void load() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file())) {
            p.load(in);
        } catch (IOException e) {
            return; // no file yet, keep defaults
        }
        STORAGE.load(p);
        SPAWNER.load(p);
        for (Target t : TARGETS) t.load(p);
    }

    public static void save() {
        Properties p = new Properties();
        STORAGE.save(p);
        SPAWNER.save(p);
        for (Target t : TARGETS) t.save(p);
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "Verdant settings");
            }
        } catch (IOException ignored) {
            // not fatal
        }
    }

    public static void resetAll() {
        STORAGE.reset();
        SPAWNER.reset();
        for (Target t : TARGETS) t.reset();
        save();
    }

    /** Hue/saturation/brightness (each 0..1) to 0xRRGGBB. */
    public static int hsbToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = Math.max(0f, Math.min(1f, s));
        v = Math.max(0f, Math.min(1f, v));
        float scaled = h * 6f;
        int i = (int) scaled;
        float f = scaled - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255f + 0.5f) << 16) | ((int) (g * 255f + 0.5f) << 8) | (int) (b * 255f + 0.5f);
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static float flt(Properties p, String key, float def, float min, float max) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            return Math.max(min, Math.min(max, Float.parseFloat(v.trim())));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
