package com.strife.combat;

import com.strife.core.RewardBridges;
import com.strife.core.StrifeCommands;
import com.strife.core.StrifeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * combat 分侧入口（docs/03 §7 模块入口模式）。
 *
 * <p>职责编排：功法倍率接进四因子公式、授功法奖励缝、{@code /strife technique} 命令子树、 法术产物表与 cast 意图（M2"法术弹道系统 +
 * 施法限速"）、combat 域实体注册（弹道/妖兽）。
 */
@Mod(value = StrifeMod.MOD_ID)
public final class StrifeCombat {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/combat");

    public StrifeCombat(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife combat entry constructed (version {})",
                container.getModInfo().getVersion());
        Techniques.registerFactor();
        RewardBridges.registerTechniqueGranter(Techniques::learn);
        StrifeCommands.MODULE_SUBTREES.add(Techniques.command());
        StrifeCommands.MODULE_SUBTREES.add(Spells.command());
        // M2：法术弹道系统（产物表 + cast 意图）与 combat 域实体（弹道/妖兽）。
        StrifeCombatEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(StrifeMonster::onAttributes);
        // 妖兽刷怪放置：主世界夜间（Monster 的光照/地面判定）+ 上界同款放置。
        modEventBus.addListener(RegisterSpawnPlacementsEvent.class, this::registerBeastSpawn);
        Spells.register();
    }

    /**
     * 为 {@code strife:monster} 注册刷怪放置规则（docs/07 §7 M2 妖兽 AI 的"妖兽真的会出现在世界上"一环）。 走 mod 总线 {@link
     * RegisterSpawnPlacementsEvent}（NeoForge 21.1 命名），放置类型 ON_GROUND + 高度图
     * MOTION_BLOCKING_NO_LEAVES，放置判定 {@link #beastSpawnRules} 在 {@link LevelReader} 上自实现 等效于原版
     * {@code Monster.checkMonsterSpawnRules} 的低光照/固体地面判定（原版重载要求一个本 dev 环境不可引用的
     * 私有类型，故不复用）。具体"加进哪些群系"由 content-base 的 {@code neoforge:add_spawns} biome_modifier 决定，
     * 不在此硬编码群系列表（数据驱动，便于 C 调整密度/分布）。
     */
    private void registerBeastSpawn(RegisterSpawnPlacementsEvent event) {
        event.register(
                StrifeCombatEntities.MONSTER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                StrifeCombat::beastSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * 妖兽自然生成的放置判定：实体下方是实心地面 + 当前光照足够低（夜间/洞穴）+ 生成方块非液体。 自实现以避开原版 {@code
     * Monster.checkMonsterSpawnRules} 对 {@code ServerLevelAccessor} 的依赖 （该类型在本 dev
     * 环境不可作为可引用类型）。STRUCTURE 生成（刷怪笼/结构）不走自然判定。
     */
    private static boolean beastSpawnRules(
            EntityType<? extends StrifeMonster> type,
            LevelReader level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random) {
        if (spawnType == MobSpawnType.STRUCTURE) {
            return false;
        }
        BlockPos ground = pos.below();
        boolean solidGround = level.getBlockState(ground).isSolid();
        boolean darkEnough = level.getRawBrightness(pos, 0) <= 8;
        boolean notInFluid = level.getBlockState(pos).getFluidState().isEmpty();
        return solidGround && darkEnough && notInFluid;
    }
}
