package com.strife.combat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.strife.core.StrifeAttachmentTypes;
import com.strife.core.StrifeData;
import com.strife.core.StrifeTime;
import com.strife.core.net.IntentRateLimiter;
import com.strife.core.net.StrifeIntents;
import com.strife.realm.RealmTables;
import com.strife.realm.UnlockBits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 施法服务（docs/07 §7 M2"施法 C2S 意图包，接 03 §4 限速"；M2 准出"施法限速生效"+"法术延迟 &lt;100ms"）。
 *
 * <p>判定链（全部服务端，客户端只发"我要放哪个法术"）：限速（03 §4 令牌桶，StrifeIntents 统一校验）→ 同屏弹道 ≤64 → 法术存在 → 解锁 spell_cast →
 * 功法要求 → 冷却 → 修为消耗。任一不过即给玩家一句可读原因 （"为什么放不出来"可解释，不静默）；全过则当刻扣气、记冷却、生成弹道——意图到实体生成零跨 tick 等待。
 */
public final class Spells {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/combat");

    private static SpellTables tables;

    private Spells() {}

    /** combat 入口构造期调用：加载产物表并登记 cast 意图。 */
    public static synchronized void register() {
        if (tables != null) {
            return;
        }
        tables = SpellTables.load();
        StrifeIntents.register(IntentRateLimiter.CAST, Spells::onCastIntent);
        LOGGER.info(
                "strife combat spells loaded: {} spells, realm_coeff={} entries",
                tables.all().size(),
                tables.rules().realmCoeff().size());
    }

    public static SpellTables tables() {
        if (tables == null) {
            throw new IllegalStateException("spell tables not loaded");
        }
        return tables;
    }

    private static void onCastIntent(StrifeIntents.Invocation invocation) {
        CompoundTag args = invocation.args();
        if (args == null || !args.contains("spell") || args.getString("spell").isEmpty()) {
            return; // 参数缺失：意图丢弃（fail-closed，限速噪声不进玩家聊天栏）
        }
        if (invocation.player() instanceof ServerPlayer player && !player.level().isClientSide) {
            cast(player, args.getString("spell"));
        }
    }

    /** 施法主链（服务端主线程；意图 → 弹道生成当刻完成）。 */
    public static void cast(ServerPlayer player, String spellId) {
        SpellTables.SpellSpec spec = tables().spell(spellId);
        if (spec == null) {
            player.displayClientMessage(Component.literal("（未知法术：" + spellId + "）"), true);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        StrifeData data = player.getData(StrifeAttachmentTypes.PLAYER_DATA);
        long now = player.level().getGameTime();

        // 同屏上限（docs/07 §7"实体对象池 ≤64 同屏"——全服实时扫描，无计数泄漏风险）
        if (countAlive(level) >= SpellProjectile.MAX_ALIVE) {
            player.displayClientMessage(Component.literal("（灵气扰动过强，法术暂无法成形）"), true);
            return;
        }

        // 判定链：解锁 → 功法 → 冷却 → 修为
        String equipped = data.techniques().equipped();
        var rejection =
                SpellMath.reject(
                        spec,
                        UnlockBits.has(data.flags(), "spell_cast"),
                        equipped,
                        data.qi(),
                        data.spells().onCooldown(spellId, now));
        if (rejection.isPresent()) {
            player.displayClientMessage(
                    Component.literal("（" + describe(rejection.get()) + "）"), true);
            return;
        }

        // 结算：扣气 + 冷却
        data = data.withQi(data.qi() - spec.costQi());
        data =
                data.withSpells(
                        data.spells()
                                .withCooldown(
                                        spellId,
                                        SpellMath.cooldownUntil(
                                                spec, now, StrifeTime.TICKS_PER_SECOND)));
        player.setData(StrifeAttachmentTypes.PLAYER_DATA, data);

        // 弹道生成（当刻；延迟契约路径）
        RealmTables.RealmEntry realm = RealmTables.get(player.server).realm(data.realmOrdinal());
        double damage = SpellMath.damage(spec, tables().rules(), realm.id());
        SpellProjectile projectile = StrifeCombatEntities.SPELL_PROJECTILE.get().create(level);
        if (projectile == null) {
            return;
        }
        projectile.setup(
                spellId,
                player,
                player.getViewVector(1.0f),
                damage,
                spec.projectileSpeed(),
                spec.projectileGravity(),
                spec.projectileRange(),
                spec.projectilePierceCount(),
                (float) spec.aoeRadiusBlocks());
        level.addFreshEntity(projectile);
    }

    /** 同屏弹道数（全服扫描——64 上限场景实体数小，O(n) 每施法一次可接受，无计数泄漏风险）。 */
    private static int countAlive(ServerLevel level) {
        return level.getEntitiesOfClass(
                        SpellProjectile.class, new AABB(-512, -64, -512, 512, 512, 512))
                .size();
    }

    /** {@code /strife spell cast <id>}：施法的兜底命令入口（03 §9；施法轮盘 UI 属 client_fx 立项）。 */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<
                    net.minecraft.commands.CommandSourceStack>
            command() {
        return net.minecraft.commands.Commands.literal("spell")
                .then(
                        net.minecraft.commands.Commands.literal("cast")
                                .then(
                                        net.minecraft.commands.Commands.argument(
                                                        "id", StringArgumentType.word())
                                                .executes(
                                                        context -> {
                                                            var player =
                                                                    context.getSource()
                                                                            .getPlayerOrException();
                                                            cast(
                                                                    player,
                                                                    StringArgumentType.getString(
                                                                            context, "id"));
                                                            return 1;
                                                        })));
    }

    private static String describe(SpellMath.Rejection rejection) {
        if (rejection instanceof SpellMath.Rejection.NotUnlocked) {
            return "尚未领悟施法之法";
        }
        if (rejection instanceof SpellMath.Rejection.TechniqueRequired(String required)) {
            return "需装备功法 " + required;
        }
        if (rejection instanceof SpellMath.Rejection.NotEnoughQi(long have, long need)) {
            return "修为不足（现有 " + have + "，需 " + need + "）";
        }
        if (rejection instanceof SpellMath.Rejection.OnCooldown) {
            return "法术尚在冷却";
        }
        if (rejection instanceof SpellMath.Rejection.NoEffectContract(String id)) {
            return "该法术的效果形态尚未觉醒（" + id + "）";
        }
        return "无法施放";
    }
}
