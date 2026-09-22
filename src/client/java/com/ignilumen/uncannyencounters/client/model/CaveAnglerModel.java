package com.ignilumen.uncannyencounters.client.model;

import com.ignilumen.uncannyencounters.client.render.CaveAnglerRenderState;
import com.ignilumen.uncannyencounters.entity.CaveAngler;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

public final class CaveAnglerModel extends EntityModel<CaveAnglerRenderState> {
    public CaveAnglerModel(ModelPart root) { super(root); }

    @Override public void setupAnim(CaveAnglerRenderState state) {
        super.setupAnim(state);
        ModelPart tongue = root.getChild("tongue");
        tongue.visible = state.phase == CaveAngler.IDLE;
        if (tongue.visible) tongue.z = (float)Math.sin(state.ageInTicks * 0.08) * 0.35F;
    }
}
