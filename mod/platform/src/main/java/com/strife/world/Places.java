package com.strife.world;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 地点（世界域）运行时表：从服务端 datapack 读 DataGen 产物 {@code strife_places/*.json}（{@code tables/places.csv}
 * 直出），与 {@link WorldTables} 同一懒加载通道。
 *
 * <p>地点是<b>双消费</b>真相源：G-4 任务导航（坐标 + 维度）与灵气地点差异化（{@code qi_scale}）都从这份数据取——本类只负责
 * 「读出来并校验形状」，不决定这两个消费者怎么用。
 *
 * <p>MVP 简化：首次访问懒加载并缓存，{@code /reload} 不刷新（重进服务端即生效），与 WorldTables 同口径。
 */
public final class Places {

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/world");

    public record Place(
            String id,
            ResourceLocation dimension,
            String name,
            int x,
            int y,
            int z,
            int radius,
            double qiScale,
            String chapter) {}

    private static volatile List<Place> places;

    private Places() {}

    /** 服务端启动时加载全部地点；datapack 缺失则抛错（导航与灵气差异化都依赖这份数据，不能静默退化）。 */
    public static List<Place> getOrThrow(MinecraftServer server) {
        List<Place> loaded = places;
        if (loaded != null) {
            return loaded;
        }
        synchronized (Places.class) {
            if (places != null) {
                return places;
            }
            places = parse(server.getResourceManager());
            return places;
        }
    }

    /** 解析真实产物（包内可见，用例直接喂 classpath 资源，不依赖开服）。 */
    static List<Place> parse(ResourceManager resources) {
        List<Place> result = new ArrayList<>();
        for (ResourceLocation id :
                resources
                        .listResources("strife_places", rl -> rl.getPath().endsWith(".json"))
                        .keySet()) {
            Optional<Resource> resource = resources.getResource(id);
            if (resource.isEmpty()) {
                continue;
            }
            try (var reader =
                    new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                result.add(fromJson(json));
            } catch (Exception e) {
                throw new IllegalStateException("地点产物解析失败 " + id + ": " + e.getMessage(), e);
            }
        }
        result.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(result);
    }

    /** 单份产物的字段解析（包内可见以便用例断言）。 */
    static Place fromJson(JsonObject json) {
        String id = string(json, "id");
        ResourceLocation dimension =
                ResourceLocation.parse(string(json, "dimension"));
        String name = string(json, "name");
        int x = json.get("x").getAsInt();
        int y = json.get("y").getAsInt();
        int z = json.get("z").getAsInt();
        int radius = json.get("radius").getAsInt();
        double qiScale = json.get("qi_scale").getAsDouble();
        String chapter = json.has("chapter") ? json.get("chapter").getAsString() : null;
        return new Place(id, dimension, name, x, y, z, radius, qiScale, chapter);
    }

    /**
     * 玩家所在区块是否落在某地点半径内（同维度）。返回最近的一个地点（半径取平方比较，免开方）。
     *
     * <p>只在<b>水平面</b>判定——高度不参与地点归属（洞窟也算落霞山麓，玩家下矿仍享受山麓灵气），与「地点是区域灵气」语义一致。
     */
    public static Place placeAt(List<Place> all, ResourceLocation dimension, int blockX, int blockZ) {
        Place best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Place p : all) {
            if (!p.dimension().equals(dimension)) {
                continue;
            }
            double dx = blockX - p.x();
            double dz = blockZ - p.z();
            double distSq = dx * dx + dz * dz;
            double rSq = (long) p.radius() * p.radius();
            if (distSq <= rSq && distSq < bestDistSq) {
                best = p;
                bestDistSq = distSq;
            }
        }
        return best;
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new IllegalStateException("地点产物缺字段 '" + key + "'");
        }
        return json.get(key).getAsString();
    }

    /** 仅供测试：清空缓存。 */
    static void clearForTest() {
        places = null;
    }
}
