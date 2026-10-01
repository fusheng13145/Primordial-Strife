package com.strife.client_fx;

import com.strife.core.net.StrifeIntentPayload;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * C2S 意图的客户端出口（docs/03 §4）：客户端只表达"我想做什么"，是否成立、代价多少全由服务端判定。
 *
 * <p>没有把意图 id 写死在各个调用点：id 是 core 的契约常量（与 NUMBERS {@code @@rate_limits} 的键一一对应）， 拼错字符串的表现是"服务端
 * fail-closed 拒收、界面毫无反应"，属于最难查的一类问题。
 */
final class ClientIntents {

    private ClientIntents() {}

    /** 发送一个无参数意图。 */
    static void send(String intentId) {
        PacketDistributor.sendToServer(new StrifeIntentPayload(intentId, new CompoundTag()));
    }
}
