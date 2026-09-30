package com.ignilumen.uncannyencounters.client.model;

import com.ignilumen.uncannyencounters.client.render.CrystalFrogRenderState;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogHopAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.packs.resources.ResourceManager;

/** Exercise the real mesh loader and vertex emission without a window, world or renderer. */
public final class CrystalFrogMeshChecks {
    public static void main(String[] args) {
        hopTransitions();
        ResourceManager resources = (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("openAsReader")) return Files.newBufferedReader(
                            Path.of("src/main/resources/assets/uncannyencounters/geometry/crystal_frog.json"));
                    throw new UnsupportedOperationException(method.getName());
                });
        CrystalFrogModel model = new CrystalFrogModel(CrystalFrogGeometry.load(resources));
        check(model.allParts().size() == 9, "Missing animation parts");
        for (int pose = 0; pose < 9; pose++) {
            CrystalFrogRenderState state = new CrystalFrogRenderState();
            state.sitting = pose == 1;
            state.isInWater = pose == 2;
            state.hindLegRotation = pose == 3 ? 0.55F : pose == 4 ? -0.55F : pose == 5 ? 0.18F : 0;
            state.frontLegRotation = pose == 3 ? -0.3F : pose == 4 ? -0.2F : pose == 5 ? 0.4F : 0;
            state.hopPitch = pose == 3 ? -0.07F : pose == 5 ? 0.06F : 0;
            state.landingCompression = pose == 6 ? 1 : 0;
            state.attackProgress = pose == 7 ? 0.5F : 0;
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
            if (pose == 8) {
                check(model.root().xRot == 0, "Hop tilt remained after returning to idle");
                check(Math.abs(model.root().xScale-model.root().yScale) < 0.0001, "Landing squash accumulated across frames");
            }
        }
        limbTravel(model);
        System.out.println("Crystal frog mesh: 9 poses, pose reset, replicated phase transitions and short-hop independent limb travel: PASS");
    }

    private static void hopTransitions() {
        CrystalFrogHopAnimation animation = new CrystalFrogHopAnimation();
        animation.tick(CrystalFrogHopAnimation.GROUNDED);
        check(animation.hindLeg(1) == 0 && animation.landing(1) == 0, "Idle frog entered hop pose");
        animation.tick(CrystalFrogHopAnimation.phase(false, false, 0.28));
        check(animation.hindLeg(0) == 0 && animation.hindLeg(0.5F) > 0, "Takeoff was not interpolated");
        check(animation.hindLeg(1) > 0.3F, "Takeoff pose was attenuated");
        check(CrystalFrogHopAnimation.phase(false, false, 0.04) == CrystalFrogHopAnimation.TUCKED, "Missing apex phase");
        for (int tick = 0; tick < 20; tick++) animation.tick(CrystalFrogHopAnimation.phase(false, false, -0.2));
        check(animation.frontLeg(1) > 0.39F, "Long fall reset to standing before landing");
        animation.tick(CrystalFrogHopAnimation.phase(true, false, -0.08));
        check(animation.landing(0) == 0 && animation.landing(1) == 1, "Actual landing did not trigger compression");
        for (int tick = 0; tick < 8; tick++) animation.tick(CrystalFrogHopAnimation.GROUNDED);
        check(Math.abs(animation.hindLeg(1)) < 0.001 && animation.landing(1) == 0, "Landing did not settle back to idle");
        animation.tick(CrystalFrogHopAnimation.RISING);
        animation.tick(CrystalFrogHopAnimation.GROUNDED);
        check(animation.landing(1) == 1, "Short uphill hop landing was ignored");
        animation.tick(CrystalFrogHopAnimation.phase(false, true, 0.3));
        check(animation.hindLeg(0) == 0 && animation.frontLeg(1) == 0 && animation.landing(1) == 0, "Swimming/sitting retained a land-hop pose");
    }

    /** Drive the actual limb meshes through a short hop, without position data on the client. */
    private static void limbTravel(CrystalFrogModel model) {
        for (String name : new String[]{"left_arm", "right_arm", "left_leg", "right_leg"}) {
            CrystalFrogHopAnimation animation = new CrystalFrogHopAnimation();
            List<List<float[]>> frames = new ArrayList<>();
            double height = 0, velocity = 0.36;
            boolean airborne = true;
            for (int tick = 0; tick < 18; tick++) {
                if (airborne) {
                    height += velocity;
                    velocity = (velocity - 0.08) * 0.98;
                    if (height <= 0) airborne = false;
                }
                // Only the replicated byte reaches the client pose controller.
                byte phase = CrystalFrogHopAnimation.phase(!airborne, false, velocity);
                animation.tick(phase);
                CrystalFrogRenderState state = new CrystalFrogRenderState();
                state.hindLegRotation = animation.hindLeg(1);
                state.frontLegRotation = animation.frontLeg(1);
                state.hopPitch = animation.pitch(1);
                state.landingCompression = animation.landing(1);
                model.setupAnim(state);
                frames.add(vertices(model.root().getChild(name)));
            }
            int moving = 0;
            for (int i = 0; i < frames.getFirst().size(); i++) {
                double maxTravel = 0;
                for (var frameA : frames) for (var frameB : frames) {
                    float[] a = frameA.get(i), b = frameB.get(i);
                    double distance = Math.sqrt(Math.pow(a[0]-b[0], 2)+Math.pow(a[1]-b[1], 2)+Math.pow(a[2]-b[2], 2));
                    maxTravel = Math.max(maxTravel, distance);
                }
                if (maxTravel > 0.08) moving++;
            }
            check(moving > frames.getFirst().size()/4, "Short-hop limb travel was suppressed: " + name);
        }
    }

    private static List<float[]> vertices(ModelPart part) {
        List<float[]> positions = new ArrayList<>();
        VertexConsumer output = (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, values) -> {
                    if (method.getName().equals("addVertex") && values.length == 11)
                        positions.add(new float[]{(Float)values[0], (Float)values[1], (Float)values[2]});
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
        part.render(new PoseStack(), output, 0, 0);
        return positions;
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
