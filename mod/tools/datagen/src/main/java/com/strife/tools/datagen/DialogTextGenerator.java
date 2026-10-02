package com.strife.tools.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code tables/dialog_<章>_text.csv} -&gt; {@code data/strife/dialog_text/<章>.json}（一章一文件）。
 *
 * <p>对话文本的运行时通道刻意<b>不走客户端 lang</b>：lang 文件是人工润色资产（无 @generated 头、不参与 V-FRESH）， DataGen
 * 合并写它会把"生成"与"手种"两种来源混进一个不可审计的文件。改为独立数据产物，服务端在对话打开/推进时 把成品文本随 S2C 包下发（服务端权威，客户端零 lang 依赖）。
 *
 * <p>条目 id 约定（`[拟]`，已登记 JSON_SCHEMA §4.10）：节点行 id = 节点 id；选项行 id = {@code <node_id>_opt<N>} （N 按
 * options 数组序从 1 起）。树里的 {@code text_key} = {@code dialog.strife.<条目id>}。 "树引用的 key 是否都有正文"是跨表校验，属
 * Validator 的 V-TEXT/V-REF（04 §6），生成器不做。
 */
public final class DialogTextGenerator implements TableGenerator {

    private static final String CONTRACT = "JSON_SCHEMA §4.10";
    private static final String KEY_PREFIX = "dialog.strife.";

    private final String textTableFile;
    private final String chapter;

    public DialogTextGenerator(String chapter) {
        this.textTableFile = "dialog_" + chapter + "_text.csv";
        this.chapter = chapter;
    }

    @Override
    public String tableFile() {
        return textTableFile;
    }

    @Override
    public List<Product> generate(TableSource source) {
        if (source.rows().isEmpty()) {
            return List.of();
        }
        Map<String, String> texts = new LinkedHashMap<>();
        for (TableSource.Record row : source.rows()) {
            String entryId = Cells.required(source, row, "id", CONTRACT);
            String text = Cells.required(source, row, "text", CONTRACT);
            if (texts.put(KEY_PREFIX + entryId, text) != null) {
                throw new IllegalStateException(
                        source.fileName() + ":" + row.line() + ": entry '" + entryId + "' 声明两次");
            }
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "dialog_text_" + chapter);
        fields.put("chapter", chapter);
        fields.put("texts", texts);
        return List.of(
                new Product(
                        "data/strife/dialog_text/" + chapter + ".json",
                        fields,
                        source.generatedHeader()));
    }
}
