package com.strife.clientfx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** 客户端只读法术清单：轮盘展示所需的公开字段，判定与数值真相仍在服务端。 */
public final class SpellClientTable {

    public record Entry(
            String id,
            String element,
            String requiredTechnique,
            long costQi,
            double cooldownSeconds) {}

    private SpellClientTable() {}

    static List<Entry> load(ResourceManager resources) {
        List<JsonObject> products = new ArrayList<>();
        Map<ResourceLocation, Resource> files =
                resources.listResources("strife_spells", path -> path.getPath().endsWith(".json"));
        files.forEach((location, resource) -> products.add(read(resource)));
        return parse(products);
    }

    static List<Entry> parse(List<JsonObject> products) {
        Map<String, Entry> byId = new LinkedHashMap<>();
        for (JsonObject product : products) {
            String id = requiredString(product, "id");
            if (byId.containsKey(id)) {
                throw new IllegalStateException("duplicate spell id: " + id);
            }
            byId.put(
                    id,
                    new Entry(
                            id,
                            requiredString(product, "element"),
                            requiredString(product, "required_technique"),
                            requiredNumber(product, "cost_qi").longValue(),
                            requiredNumber(product, "cooldown_sec").doubleValue()));
        }
        return byId.values().stream().sorted(Comparator.comparing(Entry::id)).toList();
    }

    private static JsonObject read(Resource resource) {
        try (InputStream stream = resource.open();
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "cannot read spell product " + resource.sourcePackId(), e);
        }
    }

    private static String requiredString(JsonObject product, String field) {
        if (!product.has(field) || !product.get(field).isJsonPrimitive()) {
            throw new IllegalStateException("spell product missing '" + field + "': " + product);
        }
        String value = product.get(field).getAsString();
        if (value.isBlank()) {
            throw new IllegalStateException("spell product has blank '" + field + "': " + product);
        }
        return value;
    }

    private static Number requiredNumber(JsonObject product, String field) {
        if (!product.has(field) || !product.get(field).isJsonPrimitive()) {
            throw new IllegalStateException("spell product missing '" + field + "': " + product);
        }
        return product.get(field).getAsNumber();
    }
}
