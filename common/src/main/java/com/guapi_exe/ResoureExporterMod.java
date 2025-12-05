package com.guapi_exe;

import com.guapi_exe.export.ExportSettings;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ResoureExporterMod {
    public static final String MOD_ID = "resoureexporter";

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            // Export resources command
            dispatcher.register(Commands.literal("exportresources")
                    .executes(ctx -> ResourceExporter.export(ctx, null))
                    .then(Commands.argument("modid", StringArgumentType.string())
                            .executes(ctx -> ResourceExporter.export(ctx, StringArgumentType.getString(ctx, "modid")))));

            // Export config command
            dispatcher.register(Commands.literal("exportconfig")
                    .executes(ctx -> {
                        ExportSettings settings = ExportSettings.getInstance();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Export Settings: iconSize=" + settings.getIconSize() +
                                ", itemsPerFrame=" + settings.getItemsPerFrame()
                        ), false);
                        return 1;
                    })
                    .then(Commands.literal("iconsize")
                            .then(Commands.argument("size", IntegerArgumentType.integer(16, 512))
                                    .executes(ctx -> {
                                        int size = IntegerArgumentType.getInteger(ctx, "size");
                                        ExportSettings.getInstance().setIconSize(size);
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "Icon size set to " + size
                                        ), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("speed")
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                    .executes(ctx -> {
                                        int count = IntegerArgumentType.getInteger(ctx, "count");
                                        ExportSettings.getInstance().setItemsPerFrame(count);
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "Items per frame set to " + count
                                        ), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("reset")
                            .executes(ctx -> {
                                ExportSettings.getInstance().reset();
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Export settings reset to defaults"
                                ), false);
                                return 1;
                            })));
        });
    }
}
