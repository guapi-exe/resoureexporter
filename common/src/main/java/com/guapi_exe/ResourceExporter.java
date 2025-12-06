package com.guapi_exe;

import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

public class ResourceExporter {

    public static int export(CommandContext<CommandSourceStack> context, String namespaceFilter) {
        if (Platform.getEnv() != EnvType.CLIENT) {
            context.getSource().sendFailure(Component.literal("This command can only be run on the client."));
            return 0;
        }

        try {
            ClientResourceExporter.export(namespaceFilter, (component) -> {
                context.getSource().sendSuccess(() -> component, false);
            });
            context.getSource().sendSuccess(() -> Component.literal("Resources exported successfully to 'resource_exports' folder."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            context.getSource().sendFailure(Component.literal("Failed to export resources: " + e.getMessage()));
            return 0;
        }
    }
}
