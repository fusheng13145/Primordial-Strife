package com.strife.production;

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

/** 丹方产物与炼丹运行表的一致性：产物进了 jar，就必须能查、能炼。 */
class AlchemyLoadTest {

    @Test
    @DisplayName("每一份随 jar 分发的丹方产物都进入炼丹表")
    void loadsEveryShippedPill() {
        Set<String> shipped =
                ShippedProducts.allPills().stream()
                        .map(product -> product.get("id").getAsString())
                        .collect(Collectors.toSet());
        Alchemy.register();
        assertEquals(shipped, Alchemy.recipes().keySet(), "有丹方产物未加载");
        assertNotNull(Alchemy.recipes().get("pill_xuanyu"), "pill_xuanyu 未加载");
    }

    @Test
    @DisplayName("索引列出的丹方路径与 jar 内产物一一对应")
    void indexListsEveryShippedPill() {
        Set<String> shipped =
                ShippedProducts.allPills().stream()
                        .map(
                                product ->
                                        "data/strife/strife_pills/"
                                                + product.get("id").getAsString()
                                                + ".json")
                        .collect(Collectors.toSet());

        assertEquals(shipped, new HashSet<>(Alchemy.indexPaths()), "索引与产物不一致");
    }

    @Test
    @DisplayName("只加载索引列出的丹方，索引才是唯一真相")
    void loadsOnlyIndexedPills() {
        assertEquals(
                Set.of("pill_xuanyu"),
                Alchemy.loadRecipes(List.of("data/strife/strife_pills/pill_xuanyu.json")).keySet());
    }

    @Test
    @DisplayName("索引指向不存在的产物时 fail-fast，而不是悄悄少一张丹方")
    void missingIndexedRecipeFailsFast() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        Alchemy.loadRecipes(
                                List.of("data/strife/strife_pills/pill_not_in_jar.json")));
    }
}
