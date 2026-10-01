package com.strife.combat;

import com.strife.realm.FiveElements;

/**
 * 学法/装备的门禁判定（docs/04 §2 {@code required_*} 字段、NUMBERS §1 的 {@code technique_equip} 解锁）。
 *
 * <p>纯函数 + 一个玩家快照入参：门禁是"能不能"的全部答案，把它从 MC 类型里摘出来之后，"为什么不能"就能被用例逐条钉住，
 * 也能被面板/命令直接复用成一句可读的拒绝理由（而不是"点了没反应"）。
 *
 * <p>三条规则：
 *
 * <ol>
 *   <li><b>境界与小境界</b>：境界够了就不再看小境界（化神修士不该因为"段位"被练气功法挡住）；
 *   <li><b>灵根</b>：{@code required_spiritroot} 是"必须都具备"（位掩码包含），不是"任一具备"；
 *   <li><b>装备</b>：学会只是拿到手，真正生效还要 {@code technique_equip} 解锁（STORY §4 #4：功法只给，装备在筑基后）。
 * </ol>
 */
public final class TechniqueGate {

    /** 拒绝原因；{@link #ALLOWED} 表示通过。 */
    public enum Denial {
        ALLOWED,
        REALM_TOO_LOW,
        STAGE_TOO_LOW,
        SPIRITROOT_MISMATCH,
        FACTION_MISMATCH,
        NOT_LEARNED,
        EQUIP_LOCKED,
        UNKNOWN_TECHNIQUE
    }

    /** 判定所需的玩家状态快照（由调用方从附件里取，纯函数不碰 MC 类型）。 */
    public record PlayerSnapshot(
            int realmOrdinal, int stage, int rootMask, boolean equipUnlocked, String affiliation) {}

    /** 功法侧的门禁字段快照。 */
    public record Requirements(
            int requiredRealmOrdinal,
            int requiredStage,
            int requiredRootMask,
            String requiredFaction) {}

    private TechniqueGate() {}

    /** 能否学会。 */
    public static Denial canLearn(PlayerSnapshot player, Requirements requirements) {
        if (player.realmOrdinal() < requirements.requiredRealmOrdinal()) {
            return Denial.REALM_TOO_LOW;
        }
        if (player.realmOrdinal() == requirements.requiredRealmOrdinal()
                && player.stage() < Math.max(1, requirements.requiredStage())) {
            return Denial.STAGE_TOO_LOW;
        }
        if (requirements.requiredRootMask() != FiveElements.NONE
                && (player.rootMask() & requirements.requiredRootMask())
                        != requirements.requiredRootMask()) {
            return Denial.SPIRITROOT_MISMATCH;
        }
        String required =
                requirements.requiredFaction() == null ? "" : requirements.requiredFaction();
        String affiliation = player.affiliation() == null ? "" : player.affiliation();
        // 散修（affiliation 为空）不受门第限制：他们本来就是从别处得到功法的；有门第的玩家不能跨门用别家功法。
        if (!required.isEmpty() && !affiliation.isEmpty() && !required.equals(affiliation)) {
            return Denial.FACTION_MISMATCH;
        }
        return Denial.ALLOWED;
    }

    /** 能否装备。 */
    public static Denial canEquip(PlayerSnapshot player, boolean learned) {
        if (!learned) {
            return Denial.NOT_LEARNED;
        }
        if (!player.equipUnlocked()) {
            return Denial.EQUIP_LOCKED;
        }
        return Denial.ALLOWED;
    }

    /** 拒绝原因 → 本地化键（调用方拼 {@code msg.strife.technique.denied.*}）。 */
    public static String messageKey(Denial denial) {
        return switch (denial) {
            case ALLOWED -> "msg.strife.technique.ok";
            case REALM_TOO_LOW -> "msg.strife.technique.denied.realm";
            case STAGE_TOO_LOW -> "msg.strife.technique.denied.stage";
            case SPIRITROOT_MISMATCH -> "msg.strife.technique.denied.spiritroot";
            case FACTION_MISMATCH -> "msg.strife.technique.denied.faction";
            case NOT_LEARNED -> "msg.strife.technique.denied.not_learned";
            case EQUIP_LOCKED -> "msg.strife.technique.denied.equip_locked";
            case UNKNOWN_TECHNIQUE -> "msg.strife.technique.denied.unknown";
        };
    }
}
