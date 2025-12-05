package com.guapi_exe;

import com.guapi_exe.export.ExportSettings;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

public final class ResoureExporterMod {
    public static final String MOD_ID = "resoureexporter";

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            // Main export command
            dispatcher.register(Commands.literal("exportresources")
                    .executes(ctx -> ResourceExporter.export(ctx, null))
                    .then(Commands.argument("modid", StringArgumentType.string())
                            .executes(ctx -> ResourceExporter.export(ctx, StringArgumentType.getString(ctx, "modid")))));

            // Settings commands
            dispatcher.register(Commands.literal("exportconfig")
                    // Show current settings
                    .executes(ctx -> {
                        ExportSettings settings = ExportSettings.getInstance();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Export Settings: iconSize=" + settings.getIconSize() +
                                ", itemsPerFrame=" + settings.getItemsPerFrame()), false);
                        return 1;
                    })
                    // Set icon size
                    .then(Commands.literal("iconsize")
                            .then(Commands.argument("size", IntegerArgumentType.integer(
                                    ExportSettings.MIN_ICON_SIZE, ExportSettings.MAX_ICON_SIZE))
                                    .executes(ctx -> {
                                        int size = IntegerArgumentType.getInteger(ctx, "size");
                                        ExportSettings.getInstance().setIconSize(size);
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "Icon size set to " + size + " pixels"), false);
                                        return 1;
                                    })))
                    // Set export speed (items per frame)
                    .then(Commands.literal("speed")
                            .then(Commands.argument("itemsPerFrame", IntegerArgumentType.integer(
                                    ExportSettings.MIN_ITEMS_PER_FRAME, ExportSettings.MAX_ITEMS_PER_FRAME))
                                    .executes(ctx -> {
                                        int speed = IntegerArgumentType.getInteger(ctx, "itemsPerFrame");
                                        ExportSettings.getInstance().setItemsPerFrame(speed);
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "Export speed set to " + speed + " items per frame"), false);
                                        return 1;
                                    })))
                    // Reset to defaults
                    .then(Commands.literal("reset")
                            .executes(ctx -> {
                                ExportSettings.getInstance().reset();
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Export settings reset to defaults"), false);
                                return 1;
                            })));
        });
    }
}
