package com.strife.combat;

import com.strife.core.PlaceBridges;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 符箓与保命道具（EP1，ADR-035）：一次性战术资源——越阶战斗的"底牌"。
 *
 * <p><b>遁符</b>（{@code use}）：传送到 NUMBERS {@code escape_place} 指定地点的中心（地点数据经 core 只读桥解析，combat 不
 * import world）；未上架、无地点数据、规则缺失都不消耗（fail-soft： 底牌不能变成陷阱）。冷却走原版 {@code getCooldowns}（服务端权威），时长读
 * NUMBERS。
 *
 * <p><b>替身草人</b>（{@code LivingDeathEvent}）：玩家致死一击取消、保留 {@code substitute_health}
 * 点生命、消耗一个。不防虚空/创造规则外死亡由原版语义决定——草人是"第二次机会"，不是不死金身。
 */
public final class TalismanEffects {

    private TalismanEffects() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(TalismanEffects::onLivingDeath);
    }

    /** 遁符使用入口（{@link StrifeTalismanItems} 的 use 委托）。 */
    public static InteractionResultHolder<ItemStack> useEscape(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        CombatTables.TalismanRules rules = CombatTables.talismanRules();
        if (!rules.present()) {
            sp.displayClientMessage(Component.literal("（符箓之力暂时不通）"), true);
            return InteractionResultHolder.fail(stack);
        }
        Optional<PlaceBridges.PlaceCoords> target =
                PlaceBridges.findPlace(server.getServer(), rules.escapePlace());
        if (target.isEmpty()) {
            sp.displayClientMessage(
                    Component.literal("（遁无可遁：落点 '" + rules.escapePlace() + "' 未知）"), true);
            return InteractionResultHolder.fail(stack);
        }
        PlaceBridges.PlaceCoords coords = target.get();
        player.getCooldowns().addCooldown(stack.getItem(), rules.escapeCooldownSec() * 20);
        sp.teleportTo(
                server,
                coords.x() + 0.5,
                coords.y() + 1.0,
                coords.z() + 0.5,
                java.util.Set.<net.minecraft.world.entity.RelativeMovement>of(),
                sp.getYRot(),
                sp.getXRot());
        server.playSound(
                null,
                sp.getX(),
                sp.getY(),
                sp.getZ(),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                1.0f,
                1.0f);
        if (!sp.getAbilities().instabuild) {
            stack.shrink(1);
        }
        sp.displayClientMessage(Component.literal("✦ 遁符燃起——你出现在" + coords.name()), true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player) || player.getAbilities().instabuild) {
            return;
        }
        CombatTables.TalismanRules rules = CombatTables.talismanRules();
        if (!rules.present()) {
            return;
        }
        ItemStack substitute =
                player.getInventory().contains(new ItemStack(StrifeTalismanItems.SUBSTITUTE.get()))
                        ? findSubstitute(player)
                        : ItemStack.EMPTY;
        if (substitute.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        player.setHealth((float) rules.substituteHealth());
        substitute.shrink(1);
        player.displayClientMessage(Component.literal("✦ 替身草人替你挡下了致命一击！"), true);
        player.level()
                .playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.GRASS_BREAK,
                        SoundSource.PLAYERS,
                        1.0f,
                        0.8f);
    }

    /** 找到第一个替身草人（在背包里，非掉落物）；调用方已确认存在。 */
    private static ItemStack findSubstitute(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == StrifeTalismanItems.SUBSTITUTE.get()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
