package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Turns a player's skin into its zombie version once per skin: skin tones become rotting green,
 * clothes and hair lose colour and darken, eye whites sink into dark sockets, and scattered rot
 * spots and torn holes in the outer layer are added. The result is registered as its own texture.
 */
final class ZombieSkins {
    private static final Map<Identifier, Identifier> CACHE = new HashMap<>();
    private static final Map<Identifier, Long> RETRY_AT = new HashMap<>();

    private ZombieSkins() {}

    /** Renderer instances are rebuilt on resource reload; release derived textures at the same time. */
    static void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (Identifier converted : CACHE.values()) manager.release(converted);
        CACHE.clear();
        RETRY_AT.clear();
    }

    /** The zombified texture for {@code skin}; falls back to the original if it cannot be read. Render thread only. */
    static Identifier get(Identifier skin) {
        Identifier cached = CACHE.get(skin);
        if (cached != null) return cached;
        long now = System.nanoTime();
        if (now < RETRY_AT.getOrDefault(skin, Long.MIN_VALUE)) return skin;
        try (NativeImage source = read(skin)) {
            int size = source.getWidth();
            if (size < 64 || size % 64 != 0 || source.getHeight() != size) {
                throw new IllegalArgumentException("Unsupported skin dimensions: " + size + "x" + source.getHeight());
            }
            NativeImage zombie = new NativeImage(size, size, true);
            boolean transferred = false;
            try {
                int changed = zombify(source, zombie, skin.hashCode());
                if (changed == 0) throw new IllegalStateException("Skin conversion changed no pixels");
                Identifier result = UncannyEncounters.id("zombie_skin/" + skin.getNamespace() + "/" + skin.getPath());
                Minecraft.getInstance().getTextureManager().register(result, new DynamicTexture(result::toString, zombie));
                transferred = true;
                CACHE.put(skin, result);
                RETRY_AT.remove(skin);
                UncannyEncounters.LOGGER.info("Zombie skin converted: {}x{}, {} changed pixels", size, size, changed);
                return result;
            } finally {
                if (!transferred) zombie.close();
            }
        } catch (Exception e) {
            UncannyEncounters.LOGGER.warn("Could not zombify skin {}", skin, e);
            RETRY_AT.put(skin, now + 10_000_000_000L);
            return skin;
        }
    }

    /** A copy of the skin's pixels: downloaded skins keep theirs in memory, built-in ones are read from resources. */
    private static NativeImage read(Identifier skin) throws Exception {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skin);
        if (texture instanceof DynamicTexture dynamic) {
            NativeImage copy = new NativeImage(dynamic.getPixels().getWidth(), dynamic.getPixels().getHeight(), false);
            copy.copyFrom(dynamic.getPixels());
            return copy;
        }
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(skin)) {
            return NativeImage.read(stream);
        }
    }

    private static int zombify(NativeImage from, NativeImage to, int seed) {
        int size = from.getWidth(), scale = size / 64, changed = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int pixel = from.getPixel(x, y);
                int alpha = ARGB.alpha(pixel);
                if (alpha == 0) continue;
                int u = x / scale, vPos = y / scale;
                boolean overlay = overlay(u, vPos);
                int noise = Mth.murmurHash3Mixer(seed ^ (u * 73 + vPos * 9173)) & 0xFF;
                // Torn clothes: holes in the outer layer show the body underneath.
                if (overlay && !face(u, vPos) && noise < 16) { changed++; continue; }
                toHsv(ARGB.red(pixel), ARGB.green(pixel), ARGB.blue(pixel), hsv);
                float h = hsv[0], s = hsv[1], v = hsv[2];
                if (face(u, vPos) && s < 0.1F && v > 0.8F) {
                    // Eye whites sink into dark sockets (pale skin is more saturated than this).
                    h = 0.28F; s = 0.35F; v = 0.12F;
                } else if (skinTone(h, s, v)) {
                    h = 0.27F + (noise & 7) * 0.004F;
                    s = Mth.clamp(0.38F + s * 0.25F, 0.35F, 0.6F);
                    v = v * 0.78F;
                } else {
                    // Clothes and hair: faded, darker and a little sickly.
                    s *= 0.55F;
                    v *= 0.72F;
                    h = s < 0.08F ? 0.25F : h;
                }
                if (!overlay && !face(u, vPos) && noise > 246) {
                    // Rot spots on the body.
                    h = 0.2F; s = 0.55F; v *= 0.55F;
                }
                int converted = ARGB.color(alpha, fromHsv(h, s, v));
                to.setPixel(x, y, converted);
                if (converted != pixel) changed++;
            }
        }
        return changed;
    }

    /** Orange-brown hues across light and dark complexions; very dark or grey pixels are hair or cloth. */
    private static boolean skinTone(float h, float s, float v) {
        return (h <= 0.13F || h >= 0.97F) && s >= 0.12F && s <= 0.72F && v >= 0.28F;
    }

    /** Front of the head, on either layer. */
    private static boolean face(int x, int y) {
        return y >= 8 && y < 16 && (x >= 8 && x < 16 || x >= 40 && x < 48);
    }

    /** Hat, jacket, sleeves and trouser layers of a 64x64 skin. */
    private static boolean overlay(int x, int y) {
        return y < 16 && x >= 32 || y >= 32 && y < 48 && x < 56 || y >= 48 && (x < 16 || x >= 48);
    }

    private static void toHsv(int r, int g, int b, float[] out) {
        float rf = r / 255F, gf = g / 255F, bf = b / 255F;
        float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf)), delta = max - min;
        float h;
        if (delta == 0) h = 0;
        else if (max == rf) h = ((gf - bf) / delta % 6 + 6) % 6 / 6;
        else if (max == gf) h = ((bf - rf) / delta + 2) / 6;
        else h = ((rf - gf) / delta + 4) / 6;
        out[0] = h;
        out[1] = max == 0 ? 0 : delta / max;
        out[2] = max;
    }

    /** RGB (no alpha) from hue, saturation and value in [0, 1]. */
    private static int fromHsv(float h, float s, float v) {
        float c = v * s, hp = (h % 1 + 1) % 1 * 6, x = c * (1 - Math.abs(hp % 2 - 1)), m = v - c;
        float r, g, b;
        if (hp < 1) { r = c; g = x; b = 0; }
        else if (hp < 2) { r = x; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = x; }
        else if (hp < 4) { r = 0; g = x; b = c; }
        else if (hp < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return ARGB.color(0, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255)) & 0xFFFFFF;
    }
}
