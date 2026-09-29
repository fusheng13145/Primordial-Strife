package com.strife.realm;

import java.util.List;

/**
 * §1.4 unlock_key 词表 → StrifeData.flags 位域的位序。词表顺序是契约登记（JSON_SCHEMA §1.4），
 * 不是受管数值；H5/后续新增解锁键按词表追加即可（long 64 位，词表 15 项余量充足）。
 */
public final class UnlockBits {

    private static final List<String> KEYS =
            List.of(
                    "meditation",
                    "spiritroot_panel",
                    "cultivation_panel",
                    "technique_equip",
                    "spell_cast",
                    "pill_crafting",
                    "artifact_slot",
                    "item_refine",
                    "quest_line_ch1",
                    "tribulation",
                    "sect_join",
                    "ambient_qi_affinity",
                    "soul_scan",
                    "upper_realm_gate",
                    "ascension");

    private UnlockBits() {}

    public static long bit(String unlockKey) {
        int index = KEYS.indexOf(unlockKey);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "unlock_key '" + unlockKey + "' is not in the JSON_SCHEMA §1.4 vocabulary");
        }
        return 1L << index;
    }

    public static boolean has(long flags, String unlockKey) {
        return (flags & bit(unlockKey)) != 0;
    }

    public static long with(long flags, String unlockKey) {
        return flags | bit(unlockKey);
    }
}
