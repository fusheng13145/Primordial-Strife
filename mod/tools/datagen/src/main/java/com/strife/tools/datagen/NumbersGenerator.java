package com.strife.tools.datagen;

import java.util.List;

/**
 * Converts the NUMBERS truth source into generated products (docs/04 §5), for domains with no CSV
 * table — realms come from NUMBERS §1 @@realms (JSON_SCHEMA §4.1: 境界无 CSV，链序即真相).
 *
 * <p>Run under the same skip contract as the validator's V-GROWTH: while {@code content/} (A0-7) is
 * unmerged the generator is skipped with a printed notice instead of failing CI, and arms itself
 * the moment the truth source lands.
 */
public interface NumbersGenerator {

    List<Product> generate(NumbersSource numbers);
}
