package com.guapi_exe;

import com.guapi_exe.export.AtlasGenerator;
import com.guapi_exe.export.IconExporterScreen;
import com.guapi_exe.export.ModelExporter;
import com.guapi_exe.export.TextureEntry;
import com.guapi_exe.util.ExporterLogger;
import com.guapi_exe.util.TextureUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Main client-side resource exporter.
 * Coordinates the export of block/item definitions, models, textures and icons.
 * Uses queue-based sequential processing to handle multiple namespaces correctly.
 * All rendering operations are scheduled on the render thread.
 */
public class ClientResourceExporter {

    // Static state for queue-based processing
    private static Queue<String> pendingNamespaces = new LinkedList<>();
    private static Map<ResourceLocation, Resource> currentBlockStates;
    private static Map<ResourceLocation, Resource> currentBlockModels;
    private static Map<ResourceLocation, Resource> currentItemModels;
    private static Map<ResourceLocation, Resource> currentBlockTextures;
    private static Map<ResourceLocation, Resource> currentItemTextures;
    private static File currentBaseExportDir;
    private static Consumer<Component> currentFeedback;
    private static int totalNamespaces;
    private static int processedNamespaces;

    private ClientResourceExporter() {
        // Utility class, no instantiation
    }

    /**
     * Export resources for one or all namespaces.
     * Uses queue-based sequential processing to ensure each mod's atlas is generated correctly.
     * All operations are scheduled on the render thread to avoid thread issues.
     *
     * @param namespaceFilter If non-null, only export for this namespace
     * @param feedback        Consumer for progress messages
     */
    public static void export(String namespaceFilter, Consumer<Component> feedback) {
        // Schedule everything on the render thread to avoid "RenderSystem called from wrong thread"
        Minecraft.getInstance().execute(() -> doExport(namespaceFilter, feedback));
    }

    /**
     * Internal export method - must be called on render thread.
     */
    private static void doExport(String namespaceFilter, Consumer<Component> feedback) {
        Minecraft mc = Minecraft.getInstance();
        ResourceManager manager = mc.getResourceManager();
        currentBaseExportDir = new File(mc.gameDirectory, "resource_exports");
        currentFeedback = feedback;

        ExporterLogger.info("Starting resource export...");
        feedback.accept(Component.literal("Scanning resources..."));

        // Collect all resource maps
        currentBlockStates = manager.listResources("blockstates",
                l -> l.getPath().endsWith(".json"));
        currentBlockModels = manager.listResources("models",
                l -> !l.getPath().contains("models/item/"));
        currentItemModels = manager.listResources("models/item",
                l -> true);
        currentBlockTextures = manager.listResources("textures/block",
                l -> l.getPath().endsWith(".png"));
        currentItemTextures = manager.listResources("textures/item",
                l -> l.getPath().endsWith(".png"));

        // Determine namespaces to export
        Set<String> namespaces = new LinkedHashSet<>();
        if (namespaceFilter != null) {
            namespaces.add(namespaceFilter);
        } else {
            namespaces.addAll(manager.getNamespaces());
        }

        // Initialize queue
        pendingNamespaces = new LinkedList<>(namespaces);
        totalNamespaces = namespaces.size();
        processedNamespaces = 0;

        // Start processing the first namespace
        processNextNamespace();
    }

    /**
     * Process the next namespace in the queue.
     */
    private static void processNextNamespace() {
        if (pendingNamespaces.isEmpty()) {
            // All namespaces processed
            ExporterLogger.info("Resource export complete!");
            currentFeedback.accept(Component.literal("Export complete!"));
            return;
        }

        String namespace = pendingNamespaces.poll();
        processedNamespaces++;

        ExporterLogger.info("Exporting mod: {} ({}/{})", namespace, processedNamespaces, totalNamespaces);
        currentFeedback.accept(Component.literal("Exporting mod: " + namespace + " (" + processedNamespaces + "/" + totalNamespaces + ")"));

        File modExportDir = new File(currentBaseExportDir, namespace);
        ResourceManager manager = Minecraft.getInstance().getResourceManager();

        try {
            // Export definitions, models, and textures synchronously
            ModelExporter.exportBlockDefinitions(currentBlockStates, modExportDir, namespace);
            ModelExporter.exportBlockModels(currentBlockModels, modExportDir, namespace);
            ModelExporter.exportItemModels(currentItemModels, modExportDir, namespace);
            ModelExporter.exportOpaqueBlocks(modExportDir, namespace);

            // Collect and export textures
            List<TextureEntry> allTextures = new ArrayList<>();
            TextureUtils.collectTextures(currentBlockTextures, namespace, "textures/", allTextures);
            TextureUtils.collectTextures(currentItemTextures, namespace, "textures/", allTextures);
            TextureUtils.collectMtlTextures(manager, currentBlockModels, namespace, allTextures);
            TextureUtils.collectMtlTextures(manager, currentItemModels, namespace, allTextures);

            // Export raw textures to assets directory
            TextureUtils.exportRawTextures(currentBlockTextures, modExportDir, namespace, "block");
            TextureUtils.exportRawTextures(currentItemTextures, modExportDir, namespace, "item");

            // Generate texture atlas in assets directory
            if (!allTextures.isEmpty()) {
                File atlasDir = new File(modExportDir, "assets/atlas");
                atlasDir.mkdirs();

                AtlasGenerator.generateAtlas(allTextures, atlasDir, "atlas.png", "data.min.json");
                currentFeedback.accept(Component.literal("Generated texture atlas with " + allTextures.size() + " textures"));
            }

            // Export metadata
            ModelExporter.exportMetadata(modExportDir, namespace);

            // Render icons - this is async (uses Screen), will call processNextNamespace when done
            currentFeedback.accept(Component.literal("Rendering icons for " + namespace + "..."));
            exportRenderedIcons(modExportDir, namespace, currentFeedback);

        } catch (Exception e) {
            ExporterLogger.error("Failed to export mod {}: {}", namespace, e.getMessage(), e);
            // Continue to next namespace even if this one failed
            processNextNamespace();
        }
    }

    /**
     * Export rendered icons for items in a namespace.
     * Opens a screen to render items, then calls processNextNamespace when done.
     */
    private static void exportRenderedIcons(File exportDir, String namespace, Consumer<Component> feedback) {
        Minecraft mc = Minecraft.getInstance();

        // Collect all items in this namespace
        List<ItemStack> itemsToExport = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(namespace)) {
                itemsToExport.add(new ItemStack(item));
            }
        });

        if (itemsToExport.isEmpty()) {
            feedback.accept(Component.literal("No items to render for " + namespace));
            // Continue to next namespace
            processNextNamespace();
            return;
        }

        // Open the icon exporter screen with callback to process next namespace
        IconExporterScreen screen = new IconExporterScreen(
                itemsToExport,
                exportDir,
                namespace,
                feedback,
                ClientResourceExporter::processNextNamespace  // Callback when done
        );
        mc.setScreen(screen);
    }
}
