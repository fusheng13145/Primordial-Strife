package com.strife.tools.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 妖兽掉落表生成器用例：消费 {@code NUMBERS @@world} 的 {@code beast_loot_rolls}，产出 {@code
 * loot_table/entities/monster.json} 掉落 {@code strife:item_yaocai}。
 */
class BeastLootGeneratorTest {

    @TempDir Path temp;

    @Test
    void emitsEntityLootTableDroppingYaoCai() {
        NumbersSource numbers = numbersWithBeastRolls(3);
        List<Product> products = new BeastLootGenerator().generate(numbers);

        assertEquals(1, products.size());
        Product product = products.get(0);
        assertEquals(BeastLootGenerator.RELATIVE_PATH, product.relativePath());

        String json = product.toJson();
        assertTrue(json.contains("\"type\": \"minecraft:entity\""), json);
        assertTrue(json.contains("\"rolls\": 3"), json);
        assertTrue(json.contains("\"name\": \"strife:item_yaocai\""), json);
        assertTrue(json.contains("\"@generated\""), json);
    }

    @Test
    void rejectsZeroRolls() {
        NumbersSource numbers = numbersWithBeastRolls(0);
        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> new BeastLootGenerator().generate(numbers));
        assertTrue(error.getMessage().contains("beast_loot_rolls"), error.getMessage());
    }

    @Test
    void failsWhenWorldBlockMissing() {
        writeNumbers("@@meditation\n```yaml\ntick_interval_ticks: 40\n```\n");
        NumbersSource numbers = NumbersSource.at(temp);
        assertThrows(IllegalStateException.class, () -> new BeastLootGenerator().generate(numbers));
    }

    private NumbersSource numbersWithBeastRolls(long rolls) {
        writeNumbers("@@world\n```yaml\nbeast_loot_rolls: " + rolls + "\n```\n");
        return NumbersSource.at(temp);
    }

    private void writeNumbers(String body) {
        try {
            Files.writeString(temp.resolve("NUMBERS.md"), body);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
