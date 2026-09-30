package com.ignilumen.uncannyencounters.client.model;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.packs.resources.ResourceManager;

/** Preserve the frog's existing size and geometry using the shared native mesh loader. */
public final class CrystalFrogGeometry {
    public static ModelPart load(ResourceManager resources) {
        return TriangleMeshGeometry.load(resources, UncannyEncounters.id("geometry/crystal_frog.json"), CrystalFrog.SIZE_SCALE);
    }
    private CrystalFrogGeometry() {}
}
