package com.strife.client_fx;

import com.strife.quest.StrifeNpcEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * NPC 渲染：标准人形模型 + 按 {@code npcId} 的贴图（{@code assets/strife/textures/entity/npc/<id>.png}， 64×64
 * 标准皮肤布局）。占位贴图是程序化生成的纯色皮肤（AGENTS 开区"占位资产"清单内），新 NPC 的贴图由内容 管线投放，渲染器零改动；未投放贴图的 NPC
 * 回落序章长老占位贴图，而不是紫黑格。
 *
 * <p>贴图存在性只探测一次（{@code ConcurrentHashMap} 缓存探测结果）——render 循环每帧都会要贴图，逐帧查 ResourceManager 是可感知的浪费。
 */
@OnlyIn(Dist.CLIENT)
public final class StrifeNpcRenderer
        extends HumanoidMobRenderer<StrifeNpcEntity, HumanoidModel<StrifeNpcEntity>> {

    private static final ResourceLocation FALLBACK =
            ResourceLocation.fromNamespaceAndPath(
                    "strife", "textures/entity/npc/npc_qingshi_zhizhi.png");

    private static final Map<String, ResourceLocation> RESOLVED = new ConcurrentHashMap<>();

    public StrifeNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(StrifeNpcEntity entity) {
        String npcId = entity.npcId();
        return RESOLVED.computeIfAbsent(npcId, StrifeNpcRenderer::resolve);
    }

    private static ResourceLocation resolve(String npcId) {
        ResourceLocation candidate =
                ResourceLocation.fromNamespaceAndPath(
                        "strife", "textures/entity/npc/" + npcId + ".png");
        boolean present =
                Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent();
        return present ? candidate : FALLBACK;
    }
}
