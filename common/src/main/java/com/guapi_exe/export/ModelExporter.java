package com.guapi_exe.export;

import com.google.gson.*;
import com.guapi_exe.util.ExporterLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles exporting of block/item definitions, models, and metadata.
 */
public final class ModelExporter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ModelExporter() {
        // Utility class, no instantiation
    }

    /**
     * Export block state definitions.
     */
    public static void exportBlockDefinitions(Map<ResourceLocation, Resource> resources,
                                               File exportDir, String namespace) throws IOException {
        JsonObject root = new JsonObject();
        boolean hasData = false;

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                String path = location.getPath();
                String name = path.substring("blockstates/".length(), path.length() - ".json".length());
                root.add(name, json);
                hasData = true;
            } catch (Exception e) {
                ExporterLogger.error("Failed to parse blockstate {}: {}", location, e.getMessage());
            }
        }

        if (hasData) {
            File outputFile = new File(exportDir, "assets/block_definition/data.min.json");
            outputFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            ExporterLogger.info("Exported block definitions for {}", namespace);
        }
    }

    /**
     * Export block models.
     */
    public static void exportBlockModels(Map<ResourceLocation, Resource> resources,
                                          File exportDir, String namespace) throws IOException {
        JsonObject root = new JsonObject();
        boolean hasData = false;

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;

            String path = location.getPath();

            if (path.endsWith(".json")) {
                hasData |= exportJsonModel(entry, root, location, path, "models/");
            } else if (path.endsWith(".obj")) {
                // Export OBJ as JSON for model data
                hasData |= exportObjModel(entry, root, path, "models/");
                // Also export raw OBJ file
                exportRawFile(entry, exportDir, namespace, path);
            } else if (path.endsWith(".mtl")) {
                exportRawFile(entry, exportDir, namespace, path);
            }
        }

        if (hasData) {
            File outputFile = new File(exportDir, "assets/model/data.min.json");
            outputFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            ExporterLogger.info("Exported block models for {}", namespace);
        }
    }

    /**
     * Export item models.
     */
    public static void exportItemModels(Map<ResourceLocation, Resource> resources,
                                         File exportDir, String namespace) throws IOException {
        JsonObject root = new JsonObject();
        boolean hasData = false;

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;

            String path = location.getPath();

            if (path.endsWith(".json")) {
                hasData |= exportJsonModel(entry, root, location, path, "models/");
            } else if (path.endsWith(".obj")) {
                // Export OBJ as JSON for model data
                hasData |= exportObjModel(entry, root, path, "models/");
                // Also export raw OBJ file
                exportRawFile(entry, exportDir, namespace, path);
            } else if (path.endsWith(".mtl")) {
                exportRawFile(entry, exportDir, namespace, path);
            }
        }

        if (hasData) {
            File outputFile = new File(exportDir, "assets/item_definition/data.min.json");
            outputFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            ExporterLogger.info("Exported item models for {}", namespace);
        }
    }

    /**
     * Export list of opaque blocks.
     */
    public static void exportOpaqueBlocks(File exportDir, String namespace) throws IOException {
        List<String> opaque = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id.getNamespace().equals(namespace)) {
                if (block.defaultBlockState().canOcclude()) {
                    opaque.add(id.toString());
                }
            }
        }

        if (!opaque.isEmpty()) {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            opaque.forEach(array::add);
            root.add("opaque", array);

            File outputFile = new File(exportDir, "assets/opaque/blocks.json");
            outputFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            ExporterLogger.info("Exported {} opaque blocks for {}", opaque.size(), namespace);
        }
    }

    /**
     * Export metadata (items list, config).
     */
    public static void exportMetadata(File exportDir, String namespace) {
        try {
            JsonArray itemsArray = new JsonArray();
            int itemCount = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id.getNamespace().equals(namespace)) {
                    itemsArray.add(id.getPath());
                    itemCount++;
                }
            }

            File itemsFile = new File(exportDir, "assets/item/items.json");
            itemsFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(itemsFile), StandardCharsets.UTF_8)) {
                GSON.toJson(itemsArray, writer);
            }

            int blockCount = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if (id.getNamespace().equals(namespace)) {
                    blockCount++;
                }
            }

            JsonObject configJson = new JsonObject();
            configJson.addProperty("namespace", namespace);
            configJson.addProperty("blockCount", blockCount);
            configJson.addProperty("itemCount", itemCount);

            File configFile = new File(exportDir, "config.json");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                GSON.toJson(configJson, writer);
            }

            ExporterLogger.info("Exported metadata for {} (blocks: {}, items: {})", namespace, blockCount, itemCount);
        } catch (IOException e) {
            ExporterLogger.error("Failed to export metadata for {}", namespace, e);
        }
    }

    private static boolean exportJsonModel(Map.Entry<ResourceLocation, Resource> entry, JsonObject root,
                                           ResourceLocation location, String path, String prefix) {
        try (Reader reader = entry.getValue().openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            String name = path.substring(prefix.length(), path.length() - ".json".length());
            root.add(name, json);
            return true;
        } catch (Exception e) {
            ExporterLogger.error("Failed to parse model {}: {}", location, e.getMessage());
            return false;
        }
    }

    private static boolean exportObjModel(Map.Entry<ResourceLocation, Resource> entry, JsonObject root,
                                          String path, String prefix) {
        try (InputStream in = entry.getValue().open()) {
            JsonObject objJson = ObjConverter.convertObjToJson(in);
            String name = path.substring(prefix.length(), path.length() - ".obj".length());
            root.add(name, objJson);
            return true;
        } catch (Exception e) {
            ExporterLogger.error("Failed to convert obj model: {}", e.getMessage());
            return false;
        }
    }

    private static void exportRawFile(Map.Entry<ResourceLocation, Resource> entry, File exportDir,
                                      String namespace, String path) {
        try {
            File rawFile = new File(exportDir, "assets/" + namespace + "/" + path);
            rawFile.getParentFile().mkdirs();
            try (InputStream in = entry.getValue().open();
                 OutputStream out = new FileOutputStream(rawFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (Exception e) {
            ExporterLogger.error("Failed to export raw file {}: {}", path, e.getMessage());
        }
    }
}
