package com.strife.world;

import com.strife.core.InventoryOps;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 灵气结晶的可交互面（2026-11-04）：上界的灵晶不只是矿——右键"引灵"，按 NUMBERS {@code fires_crystal_*} [拟] 权重
 * 引导一缕异火入体（越贵越稀有，四成概率灵气涣散）。异火从此有野外获取路径，不再只是黑市商品； 上界"高风险高回报"的回报侧落地。
 *
 * <p>维度门禁：仅上界（strife:upper_realm）的灵晶回应引灵——秘境/上界的结晶矿是材料矿（挖它），望仙台/上界晶串是引灵台（右键它）。 冷却为服务端内存态（{@code
 * cooldown_sec} [拟]，重启即清——冷却不是持久承诺）。
 */
public final class QiCrystalBlock extends Block {

    /** 同玩家上次引导时刻（server tick 基准；内存态）。 */
    private static final ConcurrentHashMap<UUID, Long> LAST_ATTEMPT = new ConcurrentHashMap<>();

    public QiCrystalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level instanceof ServerLevel serverLevel
                && level.dimension().equals(StrifeWorld.UPPER_REALM)) {
            guideFire(serverPlayer, serverLevel);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 引灵（服务端主线程）：冷却 → 抽权重 → 发火或"灵气涣散"。 */
    static void guideFire(ServerPlayer player, ServerLevel level) {
        WorldTables tables = WorldTables.getOrNull(level.getServer());
        if (tables == null) {
            return;
        }
        WorldTables.FireCrystal rules = tables.fireCrystal();
        long now = level.getGameTime();
        long cooldownTicks = rules.cooldownSec() * 20L;
        Long last = LAST_ATTEMPT.get(player.getUUID());
        if (last != null && now - last < cooldownTicks) {
            long remainSec = (cooldownTicks - (now - last)) / 20L;
            player.displayClientMessage(
                    Component.literal("（灵晶尚在沉眠——约 " + remainSec + " 秒后再引）"), true);
            return;
        }
        LAST_ATTEMPT.put(player.getUUID(), now);
        String picked = rules.roll(() -> level.getRandom().nextDouble());
        if ("none".equals(picked)) {
            player.displayClientMessage(Component.literal("灵气涣散——晶石沉默不语。"), true);
            return;
        }
        var containers =
                new InventoryOps.PlayerContainers(
                        player.getInventory(), player.getEnderChestInventory());
        var item = InventoryOps.resolve(picked).orElse(null);
        if (item == null) {
            player.displayClientMessage(Component.literal("（" + picked + " 尚未注册）"), true);
            return;
        }
        player.getInventory().add(new ItemStack(item));
        String name =
                net.minecraft.network.chat.Component.translatable("item.strife." + picked)
                        .getString();
        player.displayClientMessage(
                Component.literal("✦ 一缕" + name + "自晶石中引出，绕体三匝而入")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
                false);
    }

    /** 仅测试用：清冷却（用例间隔离）。 */
    static void resetCooldownsForTest() {
        LAST_ATTEMPT.clear();
    }
}
