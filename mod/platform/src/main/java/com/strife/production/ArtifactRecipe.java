package com.strife.production;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 器图（{@code tables/artifacts.csv} → {@code strife_artifacts/<id>.json}，契约 JSON_SCHEMA §4.5）。
 *
 * <p>材料按「器胚 + 核心 + materials」的并集去重取最大数量：当前器图会把器胚与核心材料也列进 materials，逐项相加会重复扣料。
 */
public record ArtifactRecipe(
        String id,
        String blank,
        String coreSlot,
        List<AlchemyRecipe.Material> materials,
        List<Quality> qualityProbs,
        String slotType,
        String requiredRealm,
        ActiveSkill activeSkill,
        int durabilityPoints) {

    public record Quality(String tier, double prob) {}

    /** 主动技能只登记法术与冷却键；冷却秒数由 combat 规则表统一给，避免产物里多一份会漂移的第二真相。 */
    public record ActiveSkill(String spellId, String cooldownKey) {

        public ActiveSkill {
            requireText(spellId, "active_skill.spell_id");
            cooldownKey = cooldownKey == null ? "" : cooldownKey;
        }
    }

    public ArtifactRecipe {
        requireText(id, "id");
        requireText(blank, "blank");
        requireText(coreSlot, "core_slot");
        requireText(slotType, "slot_type");
        if (!SLOT_TYPES.contains(slotType)) {
            throw new IllegalStateException("artifact '" + id + "' slot_type '" + slotType + "'");
        }
        requireText(requiredRealm, "required_realm");
        materials = materials == null ? List.of() : List.copyOf(materials);
        qualityProbs = qualityProbs == null ? List.of() : List.copyOf(qualityProbs);
        if (materials.isEmpty()) {
            throw new IllegalStateException("artifact '" + id + "' has no materials");
        }
        for (AlchemyRecipe.Material material : materials) {
            requireText(material.itemId(), "materials.item_id");
            if (material.count() <= 0) {
                throw new IllegalStateException("artifact '" + id + "' material count must be > 0");
            }
        }
        if (qualityProbs.isEmpty()) {
            throw new IllegalStateException("artifact '" + id + "' has no quality_probs");
        }
        double sum = 0;
        for (Quality quality : qualityProbs) {
            requireText(quality.tier(), "quality_probs.quality_tier");
            if (quality.prob() <= 0) {
                throw new IllegalStateException("artifact '" + id + "' quality prob must be > 0");
            }
            sum += quality.prob();
        }
        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new IllegalStateException(
                    "artifact '" + id + "' quality probs sum " + sum + " != 1");
        }
        if (durabilityPoints <= 0) {
            throw new IllegalStateException("artifact '" + id + "' durability must be > 0");
        }
    }

    /** 实际扣料清单：器胚、核心与 materials 并集，同一物品取最大数量（避免重复列项被加两次）。 */
    public List<AlchemyRecipe.Material> consumePlan() {
        Map<String, Integer> merged = new LinkedHashMap<>();
        merged.merge(blank, 1, Math::max);
        merged.merge(coreSlot, 1, Math::max);
        for (AlchemyRecipe.Material material : materials) {
            merged.merge(material.itemId(), material.count(), Math::max);
        }
        return merged.entrySet().stream()
                .map(entry -> new AlchemyRecipe.Material(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** 品质抽签：按 quality_probs 顺序累积区间；和已校验为 1，末档兜底。 */
    public String rollQuality(AlchemyRecipe.IntRng rng) {
        int roll = rng.nextInt(1000);
        long threshold = 0;
        for (Quality quality : qualityProbs) {
            threshold += Math.round(quality.prob() * 1000);
            if (roll < threshold) {
                return quality.tier();
            }
        }
        return qualityProbs.get(qualityProbs.size() - 1).tier();
    }

    public static ArtifactRecipe parse(JsonObject json) {
        for (String key : json.keySet()) {
            if (!ALLOWED.contains(key)) {
                throw new IllegalStateException(
                        "unknown field '" + key + "' in artifact（§1.2 规则 2 fail-fast）");
            }
        }
        String id = requiredString(json, "id");
        String blank = requiredString(json, "blank");
        String coreSlot = requiredString(json, "core_slot");
        String slotType = requiredString(json, "slot_type");
        if (requiredInt(json, "restriction_count") < 1) {
            throw new IllegalStateException("artifact '" + id + "' restriction_count < 1");
        }
        List<AlchemyRecipe.Material> materials =
                json.getAsJsonArray("materials").asList().stream()
                        .map(
                                element -> {
                                    JsonObject material = element.getAsJsonObject();
                                    for (String key : material.keySet()) {
                                        if (!MATERIAL_KEYS.contains(key)) {
                                            throw new IllegalStateException(
                                                    "unknown field '"
                                                            + key
                                                            + "' in materials of '"
                                                            + id
                                                            + "'");
                                        }
                                    }
                                    return new AlchemyRecipe.Material(
                                            requiredString(material, "item_id"),
                                            requiredInt(material, "count"));
                                })
                        .toList();
        List<Quality> qualityProbs =
                json.getAsJsonArray("quality_probs").asList().stream()
                        .map(
                                element -> {
                                    JsonObject quality = element.getAsJsonObject();
                                    for (String key : quality.keySet()) {
                                        if (!QUALITY_KEYS.contains(key)) {
                                            throw new IllegalStateException(
                                                    "unknown field '"
                                                            + key
                                                            + "' in quality_probs of '"
                                                            + id
                                                            + "'");
                                        }
                                    }
                                    return new Quality(
                                            requiredString(quality, "quality_tier"),
                                            quality.get("prob").getAsDouble());
                                })
                        .toList();
        return new ArtifactRecipe(
                id,
                blank,
                coreSlot,
                materials,
                qualityProbs,
                slotType,
                requiredString(json, "required_realm"),
                parseActiveSkill(json, id),
                requiredInt(json, "durability_points"));
    }

    private static ActiveSkill parseActiveSkill(JsonObject json, String artifactId) {
        if (!json.has("active_skill") || json.get("active_skill").isJsonNull()) {
            return null;
        }
        JsonObject skill = json.getAsJsonObject("active_skill");
        for (String key : skill.keySet()) {
            if (!ACTIVE_SKILL_KEYS.contains(key)) {
                throw new IllegalStateException(
                        "unknown field '" + key + "' in active_skill of '" + artifactId + "'");
            }
        }
        String cooldownKey =
                skill.has("cooldown_key") && !skill.get("cooldown_key").isJsonNull()
                        ? skill.get("cooldown_key").getAsString()
                        : "";
        return new ActiveSkill(requiredString(skill, "spell_id"), cooldownKey);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("artifact " + field + " must not be blank");
        }
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalStateException("artifact product missing '" + key + "'");
        }
        return json.get(key).getAsString();
    }

    private static int requiredInt(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalStateException("artifact product missing '" + key + "'");
        }
        return json.get(key).getAsInt();
    }

    private static final Set<String> SLOT_TYPES = Set.of("weapon", "armor", "treasure");
    private static final Set<String> MATERIAL_KEYS = Set.of("item_id", "count");
    private static final Set<String> QUALITY_KEYS = Set.of("quality_tier", "prob");
    private static final Set<String> ACTIVE_SKILL_KEYS =
            Set.of("spell_id", "cooldown_key", "cooldown_sec");
    private static final Set<String> ALLOWED =
            Set.of(
                    "@generated",
                    "content_format",
                    "id",
                    "blank",
                    "restriction_count",
                    "core_slot",
                    "materials",
                    "quality_probs",
                    "slot_type",
                    "required_realm",
                    "active_skill",
                    "passives",
                    "durability_points",
                    "price");
}
