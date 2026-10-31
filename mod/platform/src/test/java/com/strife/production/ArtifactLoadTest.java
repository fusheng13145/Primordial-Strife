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

/** 器图产物与炼器运行表的一致性：产物进了 jar，就必须能查、能炼。 */
class ArtifactLoadTest {

    @Test
    @DisplayName("每一份随 jar 分发的器图产物都进入炼器表")
    void loadsEveryShippedArtifact() {
        Set<String> shipped =
                ShippedProducts.allArtifacts().stream()
                        .map(product -> product.get("id").getAsString())
                        .collect(Collectors.toSet());
        ArtifactTables.register();
        assertEquals(shipped, ArtifactTables.recipes().keySet(), "有器图产物未加载");
        assertNotNull(ArtifactTables.recipes().get("art_xuanyu"), "art_xuanyu 未加载");
    }

    @Test
    @DisplayName("索引列出的器图路径与 jar 内产物一一对应")
    void indexListsEveryShippedArtifact() {
        Set<String> shipped =
                ShippedProducts.allArtifacts().stream()
                        .map(
                                product ->
                                        "data/strife/strife_artifacts/"
                                                + product.get("id").getAsString()
                                                + ".json")
                        .collect(Collectors.toSet());

        assertEquals(shipped, new HashSet<>(ArtifactTables.indexPaths()), "索引与产物不一致");
    }

    @Test
    @DisplayName("只加载索引列出的器图，索引才是唯一真相")
    void loadsOnlyIndexedArtifacts() {
        assertEquals(
                Set.of("art_xuanyu"),
                ArtifactTables.loadRecipes(List.of("data/strife/strife_artifacts/art_xuanyu.json"))
                        .keySet());
    }

    @Test
    @DisplayName("索引指向不存在的产物时 fail-fast，而不是悄悄少一张器图")
    void missingIndexedRecipeFailsFast() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        ArtifactTables.loadRecipes(
                                List.of("data/strife/strife_artifacts/art_not_in_jar.json")));
    }
}
