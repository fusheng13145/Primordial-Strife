package com.strife.tools.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** beasts.csv → strife_alchemy/beasts.json（EP1，ADR-039 妖兽体系扩展）。 */
public final class BeastTableGenerator implements TableGenerator {

    @Override
    public String tableFile() {
        return "beasts.csv";
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
        fields.put("id", "beasts");
        fields.put("entries", entries);
        return List.of(
                new Product("data/strife/strife_alchemy/beasts.json", fields, source.generatedHeader()));
    }
}
