package com.ignilumen.uncannyencounters.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/** AvatarRenderState is reserved for the dispatcher's vanilla player renderer. */
public final class ZombiePlayerRenderState extends HumanoidRenderState {
    public PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
    public boolean showHat = true;
    public boolean showJacket = true;
    public boolean showLeftPants = true;
    public boolean showRightPants = true;
    public boolean showLeftSleeve = true;
    public boolean showRightSleeve = true;
}
