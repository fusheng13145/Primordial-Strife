package com.strife.clientfx;

import com.mojang.blaze3d.platform.InputConstants;
import com.strife.core.StrifeMod;
import com.strife.core.net.DialogClientMirror;
import com.strife.core.net.DialogPayloads;
import com.strife.core.net.IntentRateLimiter;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * clientfx 分侧入口（docs/03 §7）。
 *
 * <p>{@code dist = CLIENT} 让专用服务端根本不构造本类；serverJar 又整体剔除 {@code com/strife/clientfx/**}（platform
 * build.gradle 的 exclude 规则先于本包落地）——两道隔离由 CI 的 headless 开服冒烟验证：若 client 类被服务端加载，冒烟当场红。
 *
 * <p>本类只做客户端注册编排：HUD 层进 {@link RegisterGuiLayersEvent}，按键进 {@link RegisterKeyMappingsEvent}， NPC
 * 渲染器进 {@link EntityRenderersEvent.RegisterRenderers}（实体类型按注册表资源 ID 查——clientfx 只依赖 core， 不 import
 * quest 的实体注册类，03 §2），动作挂在客户端 tick。
 */
@Mod(value = StrifeMod.MOD_ID, dist = Dist.CLIENT)
public final class StrifeClientFx {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/clientfx");

    /** 详情面板开关键（docs/07 §7 B1-1）；键位可在游戏内控制设置里改。 */
    private static final KeyMapping OPEN_PANEL =
            new KeyMapping(
                    "key.strife.panel",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_K,
                    "key.categories.strife");

    /** 打坐 / 出定（03 §4 sit 意图）。 */
    private static final KeyMapping TOGGLE_SIT =
            new KeyMapping(
                    "key.strife.sit",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_V,
                    "key.categories.strife");

    /** 主动押注突破（03 §4 breakthrough 意图，NUMBERS §10 限速 6 次/分钟）。 */
    private static final KeyMapping BREAKTHROUGH =
            new KeyMapping(
                    "key.strife.breakthrough",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_B,
                    "key.categories.strife");

    /** 施法轮盘（03 §4 cast 意图；选择结果由服务端再判定一遍）。 */
    private static final KeyMapping SPELL_WHEEL =
            new KeyMapping(
                    "key.strife.spell_wheel",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_R,
                    "key.categories.strife");

    /** 法宝面板：独立 GUI 槽位展示副手法宝。 */
    private static final KeyMapping ARTIFACT_PANEL =
            new KeyMapping(
                    "key.strife.artifact",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G,
                    "key.categories.strife");

    /** 对话镜像的已处理版本号（tick 轮询，镜像惯例）。 */
    private long handledDialogRevision = -1L;

    public StrifeClientFx(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife clientfx entry constructed (version {})",
                container.getModInfo().getVersion());
        modEventBus.addListener(this::onRegisterGuiLayers);
        modEventBus.addListener(this::onRegisterKeyMappings);
        modEventBus.addListener(this::onRegisterRenderers);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        // P1-21（D12）：指引面板桥——指引物品右键在客户端开启本侧 GuideScreen。
        com.strife.core.GuideUiBridge.registerUiOpener(
                stack -> {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new GuideScreen());
                    return true;
                });
        // EP1（ADR-038 交互层）：炉交互屏开启器——右键炼丹炉经 StrifeUiPayload 路由到本侧 AlchemyScreen。
        com.strife.core.StrifeUiBridge.registerOpener(
                com.strife.core.StrifeUiBridge.Keys.ALCHEMY_SCREEN,
                data -> {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new AlchemyScreen(data));
                    return true;
                });
        // 坊市看板批：看板开启器——右键告示牌经 StrifeUiPayload 路由到本侧 MarketBoardScreen。
        com.strife.core.StrifeUiBridge.registerOpener(
                com.strife.core.StrifeUiBridge.Keys.MARKET_SCREEN,
                data -> {
                    net.minecraft.client.Minecraft.getInstance()
                            .setScreen(new MarketBoardScreen(data));
                    return true;
                });

        // 拍卖看板批：看板开启器——右键拍卖告示柱路由到本侧 AuctionBoardScreen。
        com.strife.core.StrifeUiBridge.registerOpener(
                com.strife.core.StrifeUiBridge.Keys.AUCTION_SCREEN,
                data -> {
                    net.minecraft.client.Minecraft.getInstance()
                            .setScreen(new AuctionBoardScreen(data));
                    return true;
                });
    }

    private void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, "cultivation_hud"),
                StrifeHudOverlay::render);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PANEL);
        event.register(TOGGLE_SIT);
        event.register(BREAKTHROUGH);
        event.register(SPELL_WHEEL);
        event.register(ARTIFACT_PANEL);
    }

    /**
     * 实体渲染器注册（NPC/妖兽/法术弹道）：实体类型从注册表按资源 ID 取（注册期已完成，此处只读）， 不 import quest/combat 类型（03 §2 clientfx
     * 只依赖 core）。弹道缺失渲染器 = 进世界施法即崩客户端， 因此弹道（ThrownItemRenderer，渲染物 = 实体的 ItemSupplier）与 NPC 同为必注册项。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (String type :
                new String[] {
                    "npc",
                    "monster",
                    "wolf_beast",
                    "rock_beast",
                    "fire_fox",
                    "ice_serpent",
                    "shadow_leopard",
                    "treant",
                }) {
            EntityType<?> entityType =
                    BuiltInRegistries.ENTITY_TYPE.get(
                            ResourceLocation.fromNamespaceAndPath(StrifeMod.MOD_ID, type));
            if (entityType == null) {
                continue;
            }
            if ("npc".equals(type)) {
                event.registerEntityRenderer((EntityType) entityType, StrifeNpcRenderer::new);
            } else {
                event.registerEntityRenderer((EntityType) entityType, StrifeMonsterRenderer::new);
            }
        }
        EntityType<?> projectile =
                BuiltInRegistries.ENTITY_TYPE.get(
                        ResourceLocation.fromNamespaceAndPath(
                                StrifeMod.MOD_ID, "spell_projectile"));
        if (projectile != null) {
            event.registerEntityRenderer(
                    (EntityType) projectile,
                    context ->
                            new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(
                                    context, 1.5f, true));
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // EP1（ADR-038）：异火粒子——持有异火物品时每 8 刻生成火焰粒子。
        if (minecraft.player != null && minecraft.screen == null) {
            fireParticleTick(minecraft);
        }
        // 对话推进（03 §4：UI 只显示服务端求值结果）：镜像有新节点且当前没有对话界面 → 开界面。
        // 已开着时由 DialogScreen.tick() 自己刷新，这里只负责"开"。
        long revision = DialogClientMirror.revision();
        if (revision != handledDialogRevision) {
            handledDialogRevision = revision;
            DialogPayloads.Open current = DialogClientMirror.current();
            if (current != null
                    && !current.terminated()
                    && !(minecraft.screen instanceof DialogScreen)
                    && minecraft.player != null) {
                minecraft.setScreen(new DialogScreen(revision));
            }
        }
        while (OPEN_PANEL.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new StrifePanelScreen());
            }
        }
        while (SPELL_WHEEL.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new SpellWheelScreen());
            }
        }
        while (ARTIFACT_PANEL.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new ArtifactScreen());
            }
        }
        if (minecraft.player == null || minecraft.screen != null) {
            // 有界面开着时不清队列会导致"关掉界面后一次性触发好几次"，所以这里照样把点击吃干净。
            drain(TOGGLE_SIT);
            drain(BREAKTHROUGH);
            return;
        }
        while (TOGGLE_SIT.consumeClick()) {
            ClientIntents.send(IntentRateLimiter.SIT);
        }
        while (BREAKTHROUGH.consumeClick()) {
            ClientIntents.send(IntentRateLimiter.BREAKTHROUGH);
        }
    }

    private static void drain(KeyMapping mapping) {
        while (mapping.consumeClick()) {
            // 丢弃：界面打开期间不触发世界内动作
        }
    }

    // ═══ EP1 异火粒子（ADR-038）═══

    private static final java.util.Map<String, Long> FIRE_COLORS =
            java.util.Map.of(
                    "item_fire_mu",
                    0x44BB44L,
                    "item_fire_dixin",
                    0x886633L,
                    "item_fire_xuanbing",
                    0x66AAEEL,
                    "item_fire_chiyan",
                    0xCC4422L,
                    "item_fire_jiuyou",
                    0x442266L,
                    "item_fire_taiyang",
                    0xFFCC33L);

    private void fireParticleTick(Minecraft mc) {
        var player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;
        if (player.tickCount % 8 != 0) return;
        String fireId = findFireItem(player);
        if (fireId == null) return;
        // 火色染色（DustParticleOptions）：六火六色，颜色即辨识度——木灵翠绿/地心土橙/玄冰冰蓝/赤炎红橙/九幽暗紫/太阳金白
        long color = FIRE_COLORS.getOrDefault(fireId, 0xFF8844L);
        var tint =
                new org.joml.Vector3f(
                        ((color >> 16) & 0xFF) / 255f,
                        ((color >> 8) & 0xFF) / 255f,
                        (color & 0xFF) / 255f);
        mc.level.addParticle(
                new net.minecraft.core.particles.DustParticleOptions(tint, 1.0f),
                player.getX() + (mc.level.random.nextDouble() - 0.5) * 1.2,
                player.getY() + mc.level.random.nextDouble() * 1.8,
                player.getZ() + (mc.level.random.nextDouble() - 0.5) * 1.2,
                0,
                0.02,
                0);
        mc.level.addParticle(
                net.minecraft.core.particles.ParticleTypes.SMOKE,
                player.getX(),
                player.getY() + 1.5,
                player.getZ(),
                0,
                0.01,
                0);
    }

    private String findFireItem(net.minecraft.world.entity.player.Player player) {
        var off = checkFireStack(player.getOffhandItem());
        if (off != null) return off;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var r = checkFireStack(player.getInventory().getItem(i));
            if (r != null) return r;
        }
        return null;
    }

    private String checkFireStack(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return null;
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key != null
                && key.getNamespace().equals("strife")
                && FIRE_COLORS.containsKey(key.getPath())) {
            return key.getPath();
        }
        return null;
    }
}
