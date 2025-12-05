package com.guapi_exe;

import com.guapi_exe.export.IconRenderer;
import com.guapi_exe.export.ModelExporter;
import com.guapi_exe.export.TextureEntry;
import com.guapi_exe.util.ExporterLogger;
import com.guapi_exe.util.TextureUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Main client-side resource exporter.
 * Coordinates the export of block/item definitions, models, textures and icons.
 */
public class ClientResourceExporter {

    private ClientResourceExporter() {
        // Utility class, no instantiation
    }

    /**
     * Export resources for one or all namespaces.
     *
     * @param namespaceFilter If non-null, only export for this namespace
     * @param feedback        Consumer for progress messages
     */
    public static void export(String namespaceFilter, Consumer<Component> feedback) {
        Minecraft mc = Minecraft.getInstance();
        ResourceManager manager = mc.getResourceManager();
        File baseExportDir = new File(mc.gameDirectory, "resource_exports");

        ExporterLogger.info("Starting resource export...");
        feedback.accept(Component.literal("Scanning resources..."));

        // Collect all resource maps
        Map<ResourceLocation, Resource> blockStates = manager.listResources("blockstates",
                l -> l.getPath().endsWith(".json"));
        Map<ResourceLocation, Resource> blockModels = manager.listResources("models",
                l -> !l.getPath().contains("models/item/"));
        Map<ResourceLocation, Resource> itemModels = manager.listResources("models/item",
                l -> true);
        Map<ResourceLocation, Resource> blockTextures = manager.listResources("textures/block",
                l -> l.getPath().endsWith(".png"));
        Map<ResourceLocation, Resource> itemTextures = manager.listResources("textures/item",
                l -> l.getPath().endsWith(".png"));

        // Determine namespaces to export
        Set<String> namespaces = new HashSet<>();
        if (namespaceFilter != null) {
            namespaces.add(namespaceFilter);
        } else {
            namespaces.addAll(manager.getNamespaces());
        }

        int total = namespaces.size();
        int current = 0;

        for (String namespace : namespaces) {
            current++;
            ExporterLogger.info("Exporting mod: {} ({}/{})", namespace, current, total);
            feedback.accept(Component.literal("Exporting mod: " + namespace + " (" + current + "/" + total + ")"));

            File modExportDir = new File(baseExportDir, namespace);

            try {
                exportNamespace(namespace, modExportDir, manager, blockStates, blockModels,
                        itemModels, blockTextures, itemTextures, feedback);
            } catch (Exception e) {
                ExporterLogger.error("Failed to export mod {}: {}", namespace, e.getMessage(), e);
            }
        }

        ExporterLogger.info("Resource export complete!");
        feedback.accept(Component.literal("Export complete!"));
    }

    /**
     * Export all resources for a single namespace.
     */
    private static void exportNamespace(String namespace, File modExportDir, ResourceManager manager,
                                        Map<ResourceLocation, Resource> blockStates,
                                        Map<ResourceLocation, Resource> blockModels,
                                        Map<ResourceLocation, Resource> itemModels,
                                        Map<ResourceLocation, Resource> blockTextures,
                                        Map<ResourceLocation, Resource> itemTextures,
                                        Consumer<Component> feedback) throws Exception {
        // Export definitions and models
        ModelExporter.exportBlockDefinitions(blockStates, modExportDir, namespace);
        ModelExporter.exportBlockModels(blockModels, modExportDir, namespace);
        ModelExporter.exportItemModels(itemModels, modExportDir, namespace);
        ModelExporter.exportOpaqueBlocks(modExportDir, namespace);

        // Collect textures (currently unused but can be used for atlas export)
        List<TextureEntry> allTextures = new ArrayList<>();
        TextureUtils.collectTextures(blockTextures, namespace, "textures/", allTextures);
        TextureUtils.collectTextures(itemTextures, namespace, "textures/", allTextures);
        TextureUtils.collectMtlTextures(manager, blockModels, namespace, allTextures);
        TextureUtils.collectMtlTextures(manager, itemModels, namespace, allTextures);

        // Export metadata
        ModelExporter.exportMetadata(modExportDir, namespace);

        // Render icons
        feedback.accept(Component.literal("Rendering icons for " + namespace + "..."));
        IconRenderer.exportRenderedIcons(modExportDir, namespace, feedback);
    }
}
