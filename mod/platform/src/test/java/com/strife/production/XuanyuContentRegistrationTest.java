package com.strife.production;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 玄玉系列物品的注册与可渲染资源：内容 ID 有 lang 键不等于运行时存在。 */
class XuanyuContentRegistrationTest {

    @Test
    @DisplayName("玄玉材料与玄玉丹进入物品注册表")
    void registersXuanyuItems() {
        Set<String> ids =
                StrifeItems.ITEMS.getEntries().stream()
                        .map(entry -> entry.getId().getPath())
                        .collect(Collectors.toSet());
        assertTrue(ids.contains("item_xuanyu"), ids.toString());
        assertTrue(ids.contains("pill_xuanyu"), ids.toString());
    }

    @Test
    @DisplayName("玄玉物品具备可渲染模型资源")
    void xuanyuItemsHaveModels() {
        assertNotNull(getClass().getResource("/assets/strife/models/item/item_xuanyu.json"));
        assertNotNull(getClass().getResource("/assets/strife/models/item/pill_xuanyu.json"));
    }
}
