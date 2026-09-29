package com.ignilumen.uncannyencounters.client.model;

import com.ignilumen.uncannyencounters.client.render.CrystalFrogRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.packs.resources.ResourceManager;

/** Exercise the real mesh loader and vertex emission without a window, world or renderer. */
public final class CrystalFrogMeshChecks {
    public static void main(String[] args) {
        ResourceManager resources = (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("openAsReader")) return Files.newBufferedReader(
                            Path.of("src/main/resources/assets/uncannyencounters/geometry/crystal_frog.json"));
                    throw new UnsupportedOperationException(method.getName());
                });
        CrystalFrogModel model = new CrystalFrogModel(CrystalFrogGeometry.load(resources));
        check(model.allParts().size() == 9, "Missing animation parts");
        for (int pose = 0; pose < 5; pose++) {
            CrystalFrogRenderState state = new CrystalFrogRenderState();
            state.sitting = pose == 1;
            state.isInWater = pose == 2;
            state.hopProgress = pose == 3 ? 0.5F : 0;
            state.attackProgress = pose == 4 ? 0.5F : 0;
            state.ageInTicks = pose * 9;
            model.setupAnim(state);
            int[] count = {0};
            float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
            VertexConsumer output = (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class}, (proxy, method, values) -> {
                        if (method.getName().equals("addVertex") && values.length == 11) {
                            count[0]++;
                            for (int index : new int[]{0, 1, 2, 4, 5, 8, 9, 10})
                                check(Float.isFinite((Float)values[index]), "Non-finite vertex data");
                            float x = (Float)values[0], y = (Float)values[1];
                            bounds[0] = Math.min(bounds[0], x); bounds[1] = Math.max(bounds[1], x);
                            bounds[2] = Math.min(bounds[2], y); bounds[3] = Math.max(bounds[3], y);
                            float nx = (Float)values[8], ny = (Float)values[9], nz = (Float)values[10];
                            check(Math.abs(nx*nx + ny*ny + nz*nz - 1) < 0.001, "Invalid transformed normal");
                        }
                        return method.getReturnType() == VertexConsumer.class ? proxy : null;
                    });
            model.root().render(new PoseStack(), output, 0, 0);
            check(count[0] == 6000 * 4, "Triangles not emitted as complete quads: " + count[0]);
            if (pose == 0) {
                check(Math.abs(bounds[1]-bounds[0]-0.828) < 0.01, "Wrong body width");
                check(Math.abs(bounds[3]-bounds[2]-0.5188) < 0.01, "Wrong body height");
                check(Math.abs(bounds[3]-1.5) < 0.01, "Feet are not at the model ground plane");
            }
        }
        System.out.println("Crystal frog mesh: native load, skeleton, size, ground plane and vertex emission for 5 poses: PASS");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
