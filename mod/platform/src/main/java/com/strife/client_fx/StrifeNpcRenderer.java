package com.strife.client_fx;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * NPC 渲染：标准人形模型 + 通用 strife 皮肤（04 §7 `[锚]`："NPC 模型 MVP 用村民/盔甲架换皮 + 命名区分"——身份由头顶自定义名牌承担（{@code
 * StrifeNpcEntity#setCustomName}），皮肤不按 NPC 区分； per-NPC 换皮属美术资产管线（迭代 8），届时按 npcId 扩展贴图选择即可，渲染器骨架不变）。
 *
 * <p>泛型用 {@link Mob} 而不是 quest 的实体类：client_fx 只允许依赖 core（03 §2 依赖方向）， 实体类型在注册表按资源 ID 查（见 {@code
 * StrifeClientFx#onRegisterRenderers}），渲染器对具体实体类无感。
 */
@OnlyIn(Dist.CLIENT)
public final class StrifeNpcRenderer extends HumanoidMobRenderer<Mob, HumanoidModel<Mob>> {

    private static final ResourceLocation SKIN =
            ResourceLocation.fromNamespaceAndPath("strife", "textures/entity/npc/npc.png");

    public StrifeNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(Mob entity) {
        return SKIN;
    }
}
