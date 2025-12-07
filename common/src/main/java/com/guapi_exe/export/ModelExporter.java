package com.guapi_exe.export;

import com.google.gson.*;
import com.guapi_exe.util.ExporterLogger;
import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles exporting of block/item definitions, models, and metadata.
 */
public final class ModelExporter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson GSON_PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

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
     * Export metadata (items list, config, mod info).
     */
    public static void exportMetadata(File exportDir, String namespace) {
        try {
            // Export items list
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

            // Count blocks
            int blockCount = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if (id.getNamespace().equals(namespace)) {
                    blockCount++;
                }
            }

            // Build config with mod info
            JsonObject configJson = new JsonObject();
            configJson.addProperty("namespace", namespace);
            configJson.addProperty("blockCount", blockCount);
            configJson.addProperty("itemCount", itemCount);

            // Try to get mod info from Architectury Platform API
            Optional<Mod> modOpt = Platform.getOptionalMod(namespace);
            if (modOpt.isPresent()) {
                Mod mod = modOpt.get();
                configJson.addProperty("name", mod.getName());
                configJson.addProperty("version", mod.getVersion());
                configJson.addProperty("description", mod.getDescription());
                
                // Export authors as array
                if (!mod.getAuthors().isEmpty()) {
                    JsonArray authorsArray = new JsonArray();
                    mod.getAuthors().forEach(authorsArray::add);
                    configJson.add("authors", authorsArray);
                }
                
                // Export homepage if available
                mod.getHomepage().ifPresent(url -> configJson.addProperty("homepage", url));
                
                // Export mod logo/icon
                exportModIcon(mod, exportDir);
            } else {
                // For vanilla or unknown mods, use namespace as name
                configJson.addProperty("name", namespace);
                configJson.addProperty("version", "unknown");
                configJson.addProperty("description", "");
            }

            File configFile = new File(exportDir, "config.json");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                GSON_PRETTY.toJson(configJson, writer);
            }

            ExporterLogger.info("Exported metadata for {} (blocks: {}, items: {})", namespace, blockCount, itemCount);
        } catch (IOException e) {
            ExporterLogger.error("Failed to export metadata for {}", namespace, e);
        }
    }

    /**
     * Export mod icon/logo to the export directory.
     */
    private static void exportModIcon(Mod mod, File exportDir) {
        try {
            Optional<String> logoPath = mod.getLogoFile(128);
            if (logoPath.isPresent()) {
                String logoResource = logoPath.get();
                // Try to get the mod file path using the deprecated method
                // This is needed to access the mod's resources directly
                @SuppressWarnings("deprecation")
                Path modPath = mod.getFilePath();
                if (modPath != null) {
                    if (Files.isDirectory(modPath)) {
                        // Development environment - resources are in a folder
                        Path logoFile = modPath.resolve(logoResource);
                        if (Files.exists(logoFile)) {
                            File iconFile = new File(exportDir, "icon.png");
                            Files.copy(logoFile, iconFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            ExporterLogger.info("Exported mod icon for {}", mod.getModId());
                            return;
                        }
                    } else if (Files.isRegularFile(modPath)) {
                        // Production - read from jar
                        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(modPath.toFile())) {
                            java.util.zip.ZipEntry entry = zip.getEntry(logoResource);
                            if (entry != null) {
                                File iconFile = new File(exportDir, "icon.png");
                                try (InputStream in = zip.getInputStream(entry);
                                     OutputStream out = new FileOutputStream(iconFile)) {
                                    byte[] buffer = new byte[8192];
                                    int read;
                                    while ((read = in.read(buffer)) != -1) {
                                        out.write(buffer, 0, read);
                                    }
                                }
                                ExporterLogger.info("Exported mod icon for {}", mod.getModId());
                                return;
                            }
                        }
                    }
                }
                ExporterLogger.debug("Could not locate icon file {} for mod {}", logoResource, mod.getModId());
            }
        } catch (Exception e) {
            ExporterLogger.debug("No icon available for mod {}: {}", mod.getModId(), e.getMessage());
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
