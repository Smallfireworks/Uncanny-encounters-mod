package com.ignilumen.uncannyencounters.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;

/** Native ModelPart mesh: keeps the reference's sloped faces and original UVs. */
public final class TriangleMeshGeometry {
    public static ModelPart load(ResourceManager resources, Identifier location, float sizeScale) {
        try (var reader = resources.openAsReader(location)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            if (data.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unknown entity mesh format");
            JsonArray definitions = data.getAsJsonArray("parts");
            Map<String, ModelPart> parts = new LinkedHashMap<>();
            Map<String, Map<String, ModelPart>> children = new LinkedHashMap<>();
            Map<String, float[]> origins = new LinkedHashMap<>();
            children.put("root", new LinkedHashMap<>());
            origins.put("root", new float[3]);
            parts.put("root", new ModelPart(List.of(), children.get("root")));

            for (var element : definitions) {
                JsonObject part = element.getAsJsonObject();
                String name = part.get("name").getAsString();
                float[] origin = floats(part.getAsJsonArray("origin"));
                if (origin.length != 3 || parts.containsKey(name)) throw new IllegalArgumentException("Invalid entity part " + name);
                JsonArray triangles = part.getAsJsonArray("triangles");
                float[] vertices = new float[triangles.size() * 18];
                for (int i = 0; i < triangles.size(); i++) {
                    float[] triangle = floats(triangles.get(i).getAsJsonArray());
                    if (triangle.length != 18) throw new IllegalArgumentException("Invalid triangle in " + name);
                    System.arraycopy(triangle, 0, vertices, i * 18, 18);
                }
                children.put(name, new LinkedHashMap<>());
                origins.put(name, origin);
                List<ModelPart.Cube> geometry = vertices.length == 0 ? List.of() : List.of(new Mesh(vertices));
                parts.put(name, new ModelPart(geometry, children.get(name)));
            }
            for (var element : definitions) {
                JsonObject part = element.getAsJsonObject();
                String name = part.get("name").getAsString(), parent = part.get("parent").getAsString();
                float[] origin = origins.get(name), parentOrigin = origins.get(parent);
                if (parentOrigin == null || parent.equals(name)) throw new IllegalArgumentException("Invalid parent for " + name);
                ModelPart model = parts.get(name);
                PartPose pose = PartPose.offset(origin[0]-parentOrigin[0], origin[1]-parentOrigin[1], origin[2]-parentOrigin[2]);
                model.setInitialPose(pose);
                model.loadPose(pose);
                children.get(parent).put(name, model);
            }
            ModelPart root = parts.get("root");
            // Scale around the feet at model Y=24, preserving the existing ground plane.
            PartPose size = PartPose.offset(0, 24 * (1 - sizeScale), 0).withScale(sizeScale);
            root.setInitialPose(size);
            root.loadPose(size);
            return root;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load " + location, exception);
        }
    }

    private static float[] floats(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsFloat();
            if (!Float.isFinite(values[i])) throw new IllegalArgumentException("Non-finite entity mesh coordinate");
        }
        return values;
    }

    /**
     * One mesh batch per animated part. The superclass box supplies GUI extents;
     * the render path emits the actual triangles as quads with a repeated last vertex.
     */
    private static final class Mesh extends ModelPart.Cube {
        private final float[] vertices;
        Mesh(float[] vertices) { this(vertices, bounds(vertices)); }
        private Mesh(float[] vertices, float[] bounds) {
            super(0, 0, bounds[0], bounds[1], bounds[2],
                    bounds[3]-bounds[0], bounds[4]-bounds[1], bounds[5]-bounds[2],
                    0, 0, 0, false, 1, 1, EnumSet.allOf(Direction.class));
            this.vertices = vertices;
        }
        @Override public void compile(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color) {
            Vector3f normal = new Vector3f(), point = new Vector3f();
            for (int i = 0; i < vertices.length; i += 18) {
                normal.set(vertices[i], vertices[i+1], vertices[i+2]);
                pose.transformNormal(normal, normal);
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = i + 3 + Math.min(vertex, 2) * 5;
                    pose.pose().transformPosition(vertices[offset]/16, vertices[offset+1]/16, vertices[offset+2]/16, point);
                    buffer.addVertex(point.x, point.y, point.z, color, vertices[offset+3], vertices[offset+4],
                            overlay, light, normal.x, normal.y, normal.z);
                }
            }
        }
        private static float[] bounds(float[] vertices) {
            float[] bounds = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (int i = 0; i < vertices.length; i += 18) {
                for (int vertex = 0; vertex < 3; vertex++) for (int axis = 0; axis < 3; axis++) {
                    float value = vertices[i + 3 + vertex*5 + axis];
                    bounds[axis] = Math.min(bounds[axis], value);
                    bounds[axis+3] = Math.max(bounds[axis+3], value);
                }
            }
            return bounds;
        }
    }

    private TriangleMeshGeometry() {}
}
