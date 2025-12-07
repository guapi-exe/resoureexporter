package com.guapi_exe;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Main client-side resource exporter.
 * Coordinates the export of block/item definitions, models, textures and icons.
 */
public class ClientResourceExporter {
    
    // Queue for processing namespaces sequentially
    private static Queue<String> namespaceQueue = new LinkedList<>();
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
     * Schedules the export on the render thread to avoid thread issues.
     *
     * @param namespaceFilter If non-null, only export for this namespace
     * @param feedback        Consumer for progress messages
     */
    public static void export(String namespaceFilter, Consumer<Component> feedback) {
        // Schedule on render thread to avoid "RenderSystem called from wrong thread"
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
        Set<String> namespaces = new HashSet<>();
        if (namespaceFilter != null) {
            namespaces.add(namespaceFilter);
        } else {
            namespaces.addAll(manager.getNamespaces());
        }

        // Initialize queue
        namespaceQueue.clear();
        namespaceQueue.addAll(namespaces);
        totalNamespaces = namespaces.size();
        processedNamespaces = 0;

        // Start processing the first namespace
        processNextNamespace();
    }

    /**
     * Process the next namespace in the queue.
     */
    private static void processNextNamespace() {
        if (namespaceQueue.isEmpty()) {
            // All done
            ExporterLogger.info("Resource export complete!");
            currentFeedback.accept(Component.literal("Export complete!"));
            cleanup();
            return;
        }

        String namespace = namespaceQueue.poll();
        processedNamespaces++;
        
        ExporterLogger.info("Exporting mod: {} ({}/{})", namespace, processedNamespaces, totalNamespaces);
        currentFeedback.accept(Component.literal("Exporting mod: " + namespace + " (" + processedNamespaces + "/" + totalNamespaces + ")"));

        File modExportDir = new File(currentBaseExportDir, namespace);

        try {
            exportNamespace(namespace, modExportDir, Minecraft.getInstance().getResourceManager());
        } catch (Exception e) {
            ExporterLogger.error("Failed to export mod {}: {}", namespace, e.getMessage(), e);
            // Continue with next namespace even if this one failed
            processNextNamespace();
        }
    }

    /**
     * Export all resources for a single namespace.
     */
    private static void exportNamespace(String namespace, File modExportDir, ResourceManager manager) throws Exception {
        // Export definitions and models
        ModelExporter.exportBlockDefinitions(currentBlockStates, modExportDir, namespace);
        ModelExporter.exportBlockModels(currentBlockModels, modExportDir, namespace);
        ModelExporter.exportItemModels(currentItemModels, modExportDir, namespace);
        ModelExporter.exportOpaqueBlocks(modExportDir, namespace);

        // Export raw texture files
        currentFeedback.accept(Component.literal("Exporting textures for " + namespace + "..."));
        TextureUtils.exportRawTextures(currentBlockTextures, namespace, modExportDir, "textures/block");
        TextureUtils.exportRawTextures(currentItemTextures, namespace, modExportDir, "textures/item");

        // Collect textures for atlas
        List<TextureEntry> blockTextures = new ArrayList<>();
        List<TextureEntry> itemTextures = new ArrayList<>();
        TextureUtils.collectTextures(currentBlockTextures, namespace, "textures/", blockTextures);
        TextureUtils.collectTextures(currentItemTextures, namespace, "textures/", itemTextures);
        TextureUtils.collectMtlTextures(manager, currentBlockModels, namespace, blockTextures);
        TextureUtils.collectMtlTextures(manager, currentItemModels, namespace, itemTextures);

        // Export texture atlas to assets/atlas directory
        List<TextureEntry> allTextures = new ArrayList<>();
        allTextures.addAll(blockTextures);
        allTextures.addAll(itemTextures);
        if (!allTextures.isEmpty()) {
            currentFeedback.accept(Component.literal("Generating texture atlas for " + namespace + "..."));
            exportTextureAtlas(allTextures, modExportDir, namespace);
        }

        // Export metadata
        ModelExporter.exportMetadata(modExportDir, namespace);

        // Render icons
        currentFeedback.accept(Component.literal("Rendering icons for " + namespace + "..."));
        exportRenderedIcons(modExportDir, namespace, currentFeedback);
    }

    /**
     * Export texture atlas to assets/atlas directory.
     */
    private static void exportTextureAtlas(List<TextureEntry> textures, File exportDir, String namespace) {
        try {
            File atlasDir = new File(exportDir, "assets/atlas");
            atlasDir.mkdirs();
            
            // Generate atlas image and JSON
            com.guapi_exe.export.AtlasGenerator.generateAtlas(textures, atlasDir, "atlas.png", "data.min.json");
            ExporterLogger.info("Exported texture atlas with {} textures for {}", textures.size(), namespace);
        } catch (Exception e) {
            ExporterLogger.error("Failed to export texture atlas for {}: {}", namespace, e.getMessage());
        }
    }

    /**
     * Export rendered item icons for a namespace.
     */
    private static void exportRenderedIcons(File exportDir, String namespace, Consumer<Component> feedback) {
        Minecraft mc = Minecraft.getInstance();

        List<ItemStack> itemsToExport = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(namespace)) {
                itemsToExport.add(new ItemStack(item));
            }
        }

        if (itemsToExport.isEmpty()) {
            ExporterLogger.info("No items to render for {}", namespace);
            feedback.accept(Component.literal("No items to render for " + namespace));
            // Continue with next namespace
            processNextNamespace();
            return;
        }

        ExporterLogger.info("Rendering {} items for {}", itemsToExport.size(), namespace);
        
        // Create screen with completion callback to process next namespace
        IconExporterScreen screen = new IconExporterScreen(
                itemsToExport, 
                exportDir, 
                namespace, 
                feedback,
                ClientResourceExporter::processNextNamespace  // Callback when done
        );
        mc.execute(() -> mc.setScreen(screen));
    }

    /**
     * Clean up static references after export is complete.
     */
    private static void cleanup() {
        currentBlockStates = null;
        currentBlockModels = null;
        currentItemModels = null;
        currentBlockTextures = null;
        currentItemTextures = null;
        currentBaseExportDir = null;
        currentFeedback = null;
    }
}
