package com.strife.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.strife.testing.ShippedProducts;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 法术产物与运行时加载表的一致性：产物进了 jar，就必须能施放或至少被识别。 */
class SpellTablesLoadTest {

    @Test
    @DisplayName("每一份随 jar 分发的法术产物都进入运行表")
    void loadsEveryShippedSpell() {
        Set<String> shipped =
                ShippedProducts.allSpells().stream()
                        .map(product -> product.get("id").getAsString())
                        .collect(Collectors.toSet());
        SpellTables tables = SpellTables.load();
        assertEquals(shipped, tables.all().keySet(), "有法术产物未加载");
        assertNotNull(tables.spell("spell_xuanyu_ren"), "spell_xuanyu_ren 未加载");
    }

    @Test
    @DisplayName("索引列出的法术路径与 jar 内产物一一对应")
    void indexListsEveryShippedSpell() {
        Set<String> shipped =
                ShippedProducts.allSpells().stream()
                        .map(
                                product ->
                                        "data/strife/strife_spells/"
                                                + product.get("id").getAsString()
                                                + ".json")
                        .collect(Collectors.toSet());

        assertEquals(shipped, new HashSet<>(SpellTables.indexPaths()), "索引与产物不一致");
    }

    @Test
    @DisplayName("只加载索引列出的法术，索引才是唯一真相")
    void loadsOnlyIndexedSpells() {
        SpellTables tables =
                SpellTables.loadFromPaths(
                        List.of("data/strife/strife_spells/spell_xuanyu_ren.json"));

        assertEquals(Set.of("spell_xuanyu_ren"), tables.all().keySet());
    }

    @Test
    @DisplayName("索引指向不存在的产物时 fail-fast，而不是悄悄少一个法术")
    void missingIndexedProductFailsFast() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        SpellTables.loadFromPaths(
                                List.of("data/strife/strife_spells/spell_not_in_jar.json")));
    }
}
