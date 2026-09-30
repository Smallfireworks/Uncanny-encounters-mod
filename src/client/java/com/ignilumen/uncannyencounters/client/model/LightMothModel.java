package com.ignilumen.uncannyencounters.client.model;

import com.ignilumen.uncannyencounters.client.render.LightMothRenderState;
import com.ignilumen.uncannyencounters.entity.LightMoth;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class LightMothModel extends EntityModel<LightMothRenderState> {
    private final ModelPart body, leftWing, rightWing;
    public LightMothModel(ModelPart root) {
        super(root);
        body=root.getChild("body");
        leftWing=body.getChild("left_wing");
        rightWing=body.getChild("right_wing");
    }
    @Override public void setupAnim(LightMothRenderState state) {
        super.setupAnim(state);
        // Entity metadata drives panic speed; world position is not the animation clock.
        float frequency=state.behavior==LightMoth.FLEEING ? 1.8F : state.behavior==LightMoth.FEEDING ? 0.95F : 1.3F;
        float stroke=0.15F+Mth.sin(state.ageInTicks*frequency)*0.5F;
        leftWing.zRot=stroke;
        rightWing.zRot=-stroke;
        body.xRot=Math.clamp(state.xRot,-15,15)*Mth.DEG_TO_RAD;
        body.y-=Mth.sin(state.ageInTicks*0.16F)*0.12F;
    }
}
