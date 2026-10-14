package com.verdant.render;

import java.util.List;

import com.verdant.config.VerdantConfig;
import com.verdant.config.VerdantConfig.Module;
import com.verdant.feature.TargetScanner;
import com.verdant.feature.TargetScanner.Hit;

import org.joml.Matrix4fc;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Draws outlines (thin boxes along each edge) and optional fills for every found block. */
public final class EspRenderer {
    private EspRenderer() {}

    private static final BoxBuffer THROUGH_WALLS = new BoxBuffer("through_walls", true);
    private static final BoxBuffer DEPTH_TESTED = new BoxBuffer("depth_tested", false);

    public static void init() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(EspRenderer::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            THROUGH_WALLS.close();
            DEPTH_TESTED.close();
        });
    }

    private static void render(WorldRenderContext context) {
        List<Hit> hits = TargetScanner.hits();
        if (hits.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 cam = context.worldState().cameraRenderState.pos;
        Matrix4fc pose = context.matrices().last().pose();
        double seconds = System.nanoTime() / 1.0e9;

        int index = 0;
        for (Hit hit : hits) {
            Module module = hit.target().module;
            BoxBuffer out = module.throughWalls ? THROUGH_WALLS : DEPTH_TESTED;

            int rgb;
            if (module.rainbow) {
                float hue = (float) ((seconds * 0.15 * module.rainbowSpeed) + index * 0.013);
                rgb = VerdantConfig.hsbToRgb(hue, 0.85f, 1f);
            } else {
                rgb = hit.target().rgb();
            }
            index++;

            float fade = 1f;
            if (module.distanceFade) {
                fade = (float) Math.max(0.15, 1.0 - hit.distance() / Math.max(1.0, module.range));
            }

            addHit(out, pose, cam, hit, module, rgb, fade);
        }

        THROUGH_WALLS.flush(mc);
        DEPTH_TESTED.flush(mc);
    }

    private static void addHit(BoxBuffer out, Matrix4fc pose, Vec3 cam, Hit hit, Module module, int rgb, float fade) {
        double grow = 0.002; // tiny gap so the box does not flicker against the block faces
        double x0 = hit.minX() - grow;
        double y0 = hit.minY() - grow;
        double z0 = hit.minZ() - grow;
        double x1 = hit.maxX() + grow;
        double y1 = hit.maxY() + grow;
        double z1 = hit.maxZ() + grow;

        if (module.fill && module.fillOpacity > 0.01f) {
            out.addBox(pose, cam.x, cam.y, cam.z, x0, y0, z0, x1, y1, z1, rgb, module.fillOpacity * fade);
        }

        if (module.outlineOpacity > 0.01f) {
            float alpha = module.outlineOpacity * fade;
            // Thickness is in blocks; it grows a little with distance so far-away outlines stay visible.
            double thickness = module.outlineWidth * 0.006 * (1.0 + hit.distance() / 32.0);
            double h = thickness / 2.0;

            // 4 edges along X (full length, including the corners)
            for (int i = 0; i < 4; i++) {
                double y = (i & 1) == 0 ? y0 : y1;
                double z = (i & 2) == 0 ? z0 : z1;
                out.addBox(pose, cam.x, cam.y, cam.z, x0 - h, y - h, z - h, x1 + h, y + h, z + h, rgb, alpha);
            }
            // 4 edges along Y (between the X edges so corners are not drawn twice)
            for (int i = 0; i < 4; i++) {
                double x = (i & 1) == 0 ? x0 : x1;
                double z = (i & 2) == 0 ? z0 : z1;
                out.addBox(pose, cam.x, cam.y, cam.z, x - h, y0 + h, z - h, x + h, y1 - h, z + h, rgb, alpha);
            }
            // 4 edges along Z
            for (int i = 0; i < 4; i++) {
                double x = (i & 1) == 0 ? x0 : x1;
                double y = (i & 2) == 0 ? y0 : y1;
                out.addBox(pose, cam.x, cam.y, cam.z, x - h, y - h, z0 + h, x + h, y + h, z1 - h, rgb, alpha);
            }
        }
    }
}
