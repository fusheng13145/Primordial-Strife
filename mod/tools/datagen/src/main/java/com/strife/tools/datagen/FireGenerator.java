package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** fires.csv → strife_alchemy/fires.json（EP1，ADR-038 丹药体系——设计+数据批）。 */
public final class FireGenerator implements TableGenerator {

    @Override
    public String tableFile() {
        return "fires.csv";
    }

    @Override
    public List<Product> generate(TableSource source) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (TableSource.Record row : source.rows()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            for (String col : source.columns()) {
                String v = row.values().get(col);
                if (v != null) {
                    entry.put(col, v);
                }
            }
            entries.add(entry);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", "fires");
        fields.put("entries", entries);
        return List.of(
                new Product(
                        "data/strife/strife_alchemy/fires.json", fields, source.generatedHeader()));
    }
}
