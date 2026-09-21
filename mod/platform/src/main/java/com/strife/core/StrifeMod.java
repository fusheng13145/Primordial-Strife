package com.strife.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform entry point (A0-1 skeleton). Registration pipelines and the StrifeData attachment
 * framework land in A0-3/A0-5; this class only proves the MOD loads and the {@code /strife} command
 * root exists.
 */
@Mod(StrifeMod.MOD_ID)
public final class StrifeMod {

    public static final String MOD_ID = "strife";

    private static final Logger LOGGER = LoggerFactory.getLogger("strife/core");

    public StrifeMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info(
                "strife platform entry constructed (version {})",
                container.getModInfo().getVersion());
        // RegisterCommandsEvent is fired on the game bus whenever Commands is rebuilt.
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        registerStrifeCommand(event.getDispatcher());
    }

    static void registerStrifeCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        // A0-5 replaces this placeholder with the fallback-command registry (docs/03 §9).
        dispatcher.register(
                Commands.literal(MOD_ID).then(Commands.literal("info").executes(StrifeMod::info)));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        context.getSource()
                .sendSuccess(
                        () -> Component.literal("strife skeleton — data layer pending A0-3/A0-5"),
                        false);
        return 1;
    }
}
