package com.strife.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 世界 NPC 摆放表：从服务端 datapack 读 DataGen 产物 {@code strife_npcs/*.json}（{@code tables/npcs.csv} 直出）。与
 * {@code Places}/{@code DialogLibrary} 同一懒加载口径，{@code /reload} 不刷新（重启生效）。
 *
 * <p>只负责「读出来并校验形状」：坐标是绝对格坐标，高度由生成器按地形高度图决定；quest 侧不 import world， 因此地点只作为 {@code place}
 * 字段随行，供构建期校验与日志诊断。
 */
public final class NpcPlacements {

    /** 玩家进入该水平距离内才尝试生成（区块由玩家附近加载保证已就绪）。 */
    static final double NEAR_RANGE_BLOCKS = 64.0;

    public record Placement(String id, ResourceLocation dimension, String place, int x, int z) {}

    private static volatile List<Placement> placements;

    private NpcPlacements() {}

    /** 服务端首次用到时加载全部摆放；目录为空或产物损坏即抛错（序章 NPC 缺失会让任务链断在起点）。 */
    static List<Placement> getOrThrow(MinecraftServer server) {
        List<Placement> loaded = placements;
        if (loaded != null) {
            return loaded;
        }
        synchronized (NpcPlacements.class) {
            if (placements != null) {
                return placements;
            }
            placements = parse(server.getResourceManager());
            return placements;
        }
    }

    /** 解析真实产物（包内可见，用例直接喂 classpath 资源，不依赖开服）。 */
    static List<Placement> parse(ResourceManager resources) {
        List<Placement> result = new ArrayList<>();
        for (ResourceLocation id :
                resources
                        .listResources(
                                "strife_npcs",
                                rl ->
                                        rl.getNamespace().equals("strife")
                                                && rl.getPath().endsWith(".json"))
                        .keySet()) {
            Optional<Resource> resource = resources.getResource(id);
            if (resource.isEmpty()) {
                continue;
            }
            try (var reader =
                    new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                result.add(fromJson(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                throw new IllegalStateException("NPC 摆放产物解析失败 " + id + ": " + e.getMessage(), e);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException(
                    "strife_npcs 目录为空：DataGen 未产出 NPC 摆放（tables/npcs.csv 被清空或产物未进 jar）");
        }
        result.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(result);
    }

    /** 单份产物的字段解析（包内可见以便用例断言）。 */
    static Placement fromJson(JsonObject json) {
        String id = requiredString(json, "id");
        if (!id.matches("^npc_[a-z0-9_]+$")) {
            throw new IllegalStateException("NPC id 必须是 npc_ 前缀的小写蛇形，得到 '" + id + "'");
        }
        String dimension = requiredString(json, "dimension");
        if (!dimension.startsWith("minecraft:") && !dimension.startsWith("strife:")) {
            throw new IllegalStateException(
                    "dimension 必须带已知命名空间（minecraft:/strife:），得到 '" + dimension + "'");
        }
        return new Placement(
                id,
                ResourceLocation.parse(dimension),
                requiredString(json, "place"),
                requiredInt(json, "x"),
                requiredInt(json, "z"));
    }

    /** 玩家是否已进入该摆放的生成范围（同维度 + 水平距离 ≤ 64 格）。 */
    static boolean near(Placement placement, ResourceLocation dimension, double x, double z) {
        if (!placement.dimension().equals(dimension)) {
            return false;
        }
        double dx = x - placement.x();
        double dz = z - placement.z();
        return dx * dx + dz * dz <= NEAR_RANGE_BLOCKS * NEAR_RANGE_BLOCKS;
    }

    private static String requiredString(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()) {
            throw new IllegalStateException("NPC 摆放产物缺字段 '" + field + "'");
        }
        return json.get(field).getAsString();
    }

    private static int requiredInt(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()) {
            throw new IllegalStateException("NPC 摆放产物缺字段 '" + field + "'");
        }
        return json.get(field).getAsInt();
    }
}
