package com.strife.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 捕兽符（P1-22 御兽 v1，[拟]）：对**重伤（生命 ≤ 阈值比例）的野生妖兽**使用 → 概率驯服为灵宠。
 *
 * <p>判定链（服务端全权）：目标为野生 strife 妖兽 → 生命比例 ≤ {@code beast_taming_health_ratio} [拟] → 掷 {@code
 * beast_taming_chance} [拟] → 成功驯服（灵宠上限 MAX_PETS/人 [拟]，超出即失败不耗符） / 失败妖兽被激怒。驯服语义 = {@link
 * StrifeMonster#tame}（AI 分流 + NBT 持久化）。
 */
public final class BeastTalismanItem extends Item {

    /** 每人灵宠上限（[拟]——超出即驯服失败，符不消耗）。 */
    static final int MAX_PETS = 2;

    public BeastTalismanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            net.minecraft.world.entity.player.Player player,
            LivingEntity target,
            InteractionHand hand) {
        if (player.level().isClientSide
                || !(player instanceof ServerPlayer serverPlayer)
                || !(target instanceof StrifeMonster monster)
                || !(player.level()
                        instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        var rules = CombatTables.beastTaming();
        // 野生判定：已驯服的妖兽（别人的灵宠）不可再捕
        if (monster.isTamed()) {
            serverPlayer.displayClientMessage(Component.literal("（它已有主人了）"), true);
            return InteractionResult.FAIL;
        }
        double healthRatio = monster.getHealth() / monster.getMaxHealth();
        if (healthRatio > rules.maxHealthRatio()) {
            serverPlayer.displayClientMessage(
                    Component.literal(
                            "（它伤得不够重——先打到生命 "
                                    + Math.round(rules.maxHealthRatio() * 100)
                                    + "% 以下再试）"),
                    true);
            return InteractionResult.FAIL;
        }
        // 灵宠上限：全加载实体扫描（驯服是一次性低频操作，成本可接受——Spells.countAlive 同款口径）
        long owned =
                serverLevel
                        .getEntitiesOfClass(
                                StrifeMonster.class,
                                target.getBoundingBox().inflate(128),
                                m -> player.getUUID().equals(m.ownerUuid()))
                        .size();
        if (owned >= MAX_PETS) {
            serverPlayer.displayClientMessage(
                    Component.literal("（灵宠已满（" + MAX_PETS + "）——御兽有数，贪多必失）"), true);
            return InteractionResult.FAIL;
        }
        if (serverLevel.getRandom().nextDouble() >= rules.chance()) {
            // 驯服失败：妖兽被激怒，符已耗
            stack.shrink(1);
            monster.setTarget(serverPlayer);
            serverPlayer.displayClientMessage(
                    Component.literal("✗ 捕兽符燃尽——妖兽被激怒了！")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true);
            return InteractionResult.CONSUME;
        }
        // 驯服成功
        stack.shrink(1);
        monster.tame(player.getUUID());
        serverPlayer.displayClientMessage(
                Component.literal("✦ 妖兽俯首——灵宠随你修行")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
                false);
        return InteractionResult.SUCCESS;
    }
}
