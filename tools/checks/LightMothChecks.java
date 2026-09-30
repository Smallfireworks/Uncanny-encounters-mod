package com.ignilumen.uncannyencounters.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.client.render.LightMothRenderState;
import com.ignilumen.uncannyencounters.entity.LightMoth;
import com.ignilumen.uncannyencounters.entity.lightmoth.MothLights;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.packs.resources.ResourceManager;

/** No game, world, window or image preview: actual mesh emission and saved-claim round trip. */
public final class LightMothChecks {
    public static void main(String[] args) throws Exception {
        claimPersistence();
        ResourceManager resources=(ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class},(proxy,method,values) -> {
                    if (method.getName().equals("openAsReader")) return Files.newBufferedReader(
                            Path.of("src/main/resources/assets/uncannyencounters/geometry/light_moth.json"));
                    throw new UnsupportedOperationException(method.getName());
                });
        LightMothModel model=new LightMothModel(TriangleMeshGeometry.load(resources,
                UncannyEncounters.id("geometry/light_moth.json"),1));
        check(model.allParts().size()==4,"Missing wing parts");
        ModelPart left=model.root().getChild("body").getChild("left_wing");
        ModelPart right=model.root().getChild("body").getChild("right_wing");
        List<float[]> before=null,after=null;
        for (int tick=0;tick<12;tick++) {
            LightMothRenderState state=new LightMothRenderState();state.ageInTicks=tick;
            state.behavior=tick<6 ? LightMoth.WANDER : LightMoth.FLEEING;
            model.setupAnim(state);
            check(Math.abs(left.zRot+right.zRot)<0.0001,"Wings lost symmetry");
            check(vertices(model.root()).size()==16000*4,"Missing triangles in full mesh");
            if (tick==1) before=vertices(left);
            if (tick==3) after=vertices(left);
        }
        int moving=0;
        for (int i=0;i<before.size();i++) {
            float[] a=before.get(i),b=after.get(i);
            if (Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2))>0.08) moving++;
        }
        check(moving>before.size()/3,"Wing stroke did not move the wing mesh independently");
        LightMothRenderState idle=new LightMothRenderState();model.setupAnim(idle);
        float angle=left.zRot;model.setupAnim(idle);
        check(left.zRot==angle,"Wing rotation accumulated across frames");
        System.out.println("Light moth: 16000-triangle real mesh, symmetric independent wing travel, pose reset and persisted exclusive light ownership: PASS");
    }
    @SuppressWarnings("unchecked")
    private static void claimPersistence() throws Exception {
        var field=MothLights.class.getDeclaredField("CODEC");field.setAccessible(true);
        Codec<MothLights> codec=(Codec<MothLights>)field.get(null);
        UUID owner=UUID.randomUUID();BlockPos pos=new BlockPos(5,-20,9);
        JsonObject record=new JsonObject();
        record.add("pos",BlockPos.CODEC.encodeStart(JsonOps.INSTANCE,pos).getOrThrow());
        record.add("owner",UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE,owner).getOrThrow());
        record.addProperty("expires",123456789L);
        JsonArray list=new JsonArray();list.add(record);
        MothLights loaded=codec.parse(JsonOps.INSTANCE,list).getOrThrow();
        check(loaded.owns(pos,owner) && loaded.available(pos,owner),"Owner lost after loading");
        check(!loaded.available(pos,UUID.randomUUID()),"Another moth can take an occupied light");
        check(loaded.available(pos.above(),UUID.randomUUID()),"Claim incorrectly occupies another block");
        var saved=codec.encodeStart(JsonOps.INSTANCE,loaded).getOrThrow();
        check(saved.getAsJsonArray().get(0).getAsJsonObject().get("expires").getAsLong()==123456789L,"Restore deadline lost after saving");
        check(codec.parse(JsonOps.INSTANCE,saved).getOrThrow().owns(pos,owner),"Ownership failed save/reload round trip");
    }
    private static List<float[]> vertices(ModelPart part) {
        List<float[]> result=new ArrayList<>();
        VertexConsumer output=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class},(proxy,method,values) -> {
                    if (method.getName().equals("addVertex") && values.length==11) {
                        for (int i : new int[]{0,1,2,4,5,8,9,10}) check(Float.isFinite((Float)values[i]),"Non-finite mesh coordinate");
                        result.add(new float[]{(Float)values[0],(Float)values[1],(Float)values[2]});
                    }
                    return method.getReturnType()==VertexConsumer.class ? proxy : null;
                });
        part.render(new PoseStack(),output,0,0);return result;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
