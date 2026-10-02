package com.strife.client_fx;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 妖兽渲染：僵尸人形模型 + 通用妖兽皮肤（monster.png，64×64 皮肤布局；04 §7 占位资产——程序化生成， 妖形专用模型/贴图随美术管线迭代）。泛型用 {@link
 * Monster} 而非 combat 的实体类：client_fx 只允许依赖 core（03 §2），实体类型按注册表资源 ID 查（见 {@code
 * StrifeClientFx#onRegisterRenderers}）。
 */
@OnlyIn(Dist.CLIENT)
public final class StrifeMonsterRenderer
        extends HumanoidMobRenderer<Monster, HumanoidModel<Monster>> {

    private static final ResourceLocation SKIN =
            ResourceLocation.fromNamespaceAndPath("strife", "textures/entity/npc/monster.png");

    public StrifeMonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(Monster entity) {
        return SKIN;
    }
}
