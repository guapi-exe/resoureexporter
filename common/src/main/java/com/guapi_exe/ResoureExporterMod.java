package com.guapi_exe;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;

public final class ResoureExporterMod {
    public static final String MOD_ID = "resoureexporter";

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(Commands.literal("exportresources")
                    .executes(ctx -> ResourceExporter.export(ctx, null))
                    .then(Commands.argument("modid", StringArgumentType.string())
                            .executes(ctx -> ResourceExporter.export(ctx, StringArgumentType.getString(ctx, "modid")))));
        });
    }
}
