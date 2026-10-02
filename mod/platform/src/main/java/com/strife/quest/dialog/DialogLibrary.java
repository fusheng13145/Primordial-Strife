package com.strife.quest.dialog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * 对话产物的服务端加载缓存（{@code data/strife/dialog_trees/<章>.json} + {@code
 * data/strife/dialog_text/<章>.json}）。
 *
 * <p>与 {@code QuestAdapter.PROLOGUE} 同一"惰性单次加载"口径：产物是确定性输出（DataGen + V-FRESH 保证）， 进程内缓存一份即可；热更 zip
 * 重载世界后 {@code /reload} 走 ResourceManager 重读，本缓存在 {@code OnDatapackSynced}
 * 之外刻意不做失效——对话内容更新随重启生效（与任务簿同策略，避免半旧会话引用已 消失的节点 id）。
 */
public final class DialogLibrary {

    private record Chapter(DialogBook book, Map<String, String> texts) {}

    private static final Map<String, Chapter> CHAPTERS = new ConcurrentHashMap<>();

    private DialogLibrary() {}

    /** 某 NPC 的对话树所属章簿（跨章查找；章目录为空 = 无对话内容，返回 null 由调用方提示）。 */
    public static DialogBook bookByNpc(MinecraftServer server, String npcId) {
        for (String chapter : allChapters(server)) {
            DialogBook book = chapter(server, chapter).book();
            if (book.treeByNpc(npcId) != null) {
                return book;
            }
        }
        return null;
    }

    /** 某 NPC 的对话树（跨章查找）。 */
    public static DialogBook.TreeSpec treeByNpc(MinecraftServer server, String npcId) {
        DialogBook book = bookByNpc(server, npcId);
        return book == null ? null : book.treeByNpc(npcId);
    }

    /** 文本条目（服务端查成品后随 S2C 下发）。缺失返回占位串——热更 zip 绕过门禁时可见，不静默。 */
    public static String text(MinecraftServer server, String chapter, String textKey) {
        Map<String, String> texts = chapter(server, chapter).texts();
        String value = texts.get(textKey);
        return value == null ? "（缺失对话文本：" + textKey + "）" : value;
    }

    /** 章集合 = dialog_trees 目录下的产物清单（ResourceManager 列举，产物在 jar 内也可见）。 */
    private static java.util.Set<String> allChapters(MinecraftServer server) {
        java.util.Set<String> chapters = new java.util.HashSet<>();
        server.getResourceManager()
                .listResources(
                        "dialog_trees",
                        id -> id.getNamespace().equals("strife") && id.getPath().endsWith(".json"))
                .keySet()
                .forEach(
                        id ->
                                chapters.add(
                                        id.getPath()
                                                .replaceFirst("^dialog_trees/", "")
                                                .replaceFirst("\\.json$", "")));
        return java.util.Set.copyOf(chapters);
    }

    private static Chapter chapter(MinecraftServer server, String chapter) {
        return CHAPTERS.computeIfAbsent(chapter, key -> load(server, key));
    }

    private static Chapter load(MinecraftServer server, String chapter) {
        DialogBook book =
                readJson(server, "dialog_trees/" + chapter + ".json", "dialog tree")
                        .map(DialogBook::parse)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "dialog_trees/" + chapter + ".json disappeared"));
        Map<String, String> texts = new java.util.HashMap<>();
        Optional<JsonObject> textProduct =
                readJson(server, "dialog_text/" + chapter + ".json", "dialog text");
        if (textProduct.isPresent()) {
            for (Map.Entry<String, JsonElement> entry :
                    textProduct.get().getAsJsonObject("texts").entrySet()) {
                texts.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return new Chapter(book, Map.copyOf(texts));
    }

    private static Optional<JsonObject> readJson(MinecraftServer server, String path, String what) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("strife", path);
        return server.getResourceManager()
                .getResource(id)
                .map(
                        resource -> {
                            try (var stream = resource.open();
                                    var reader =
                                            new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                                return JsonParser.parseReader(reader).getAsJsonObject();
                            } catch (Exception e) {
                                throw new IllegalStateException(
                                        "cannot load " + what + " " + path, e);
                            }
                        });
    }
}
