package com.guapi_exe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ClientResourceExporter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static void export(String namespaceFilter, Consumer<Component> feedback) {
        Minecraft mc = Minecraft.getInstance();
        ResourceManager manager = mc.getResourceManager();
        File baseExportDir = new File(mc.gameDirectory, "resource_exports");

        feedback.accept(Component.literal("Scanning resources..."));

        Map<ResourceLocation, Resource> blockStates = manager.listResources("blockstates", l -> l.getPath().endsWith(".json"));
        Map<ResourceLocation, Resource> blockModels = manager.listResources("models", l -> !l.getPath().contains("models/item/"));
        // Scan item models
        Map<ResourceLocation, Resource> itemModels = manager.listResources("models/item", l -> true);
        Map<ResourceLocation, Resource> blockTextures = manager.listResources("textures/block", l -> l.getPath().endsWith(".png"));
        Map<ResourceLocation, Resource> itemTextures = manager.listResources("textures/item", l -> l.getPath().endsWith(".png"));

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
            feedback.accept(Component.literal("Exporting mod: " + namespace + " (" + current + "/" + total + ")"));
            File modExportDir = new File(baseExportDir, namespace);

            try {
                exportBlockDefinitions(blockStates, modExportDir, namespace);
                exportBlockModels(blockModels, modExportDir, namespace);
                exportItemModels(itemModels, modExportDir, namespace);
                exportOpaqueBlocks(modExportDir, namespace);

                List<TextureEntry> allTextures = new ArrayList<>();
                collectTextures(blockTextures, namespace, "textures/", allTextures);
                collectTextures(itemTextures, namespace, "textures/", allTextures);
                collectMtlTextures(manager, blockModels, namespace, allTextures);
                collectMtlTextures(manager, itemModels, namespace, allTextures);

                if (!allTextures.isEmpty()) {
                    allTextures.sort((a, b) -> Integer.compare(b.image.getHeight(), a.image.getHeight()));
                    exportAtlas(allTextures, modExportDir);
                    exportIcons(allTextures, modExportDir, "original");
                }

                exportMetadata(modExportDir, namespace);

                feedback.accept(Component.literal("Rendering icons for " + namespace + "..."));
                exportRenderedIcons(modExportDir, namespace, feedback);

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Failed to export mod " + namespace + ": " + e.getMessage());
            }
        }
        feedback.accept(Component.literal("Export complete!"));
    }

    private static void exportRenderedIcons(File exportDir, String namespace, Consumer<Component> feedback) {
        Minecraft mc = Minecraft.getInstance();

        CompletableFuture.runAsync(() -> {
            try {
                List<TextureEntry> renderedTextures = new ArrayList<>();
                int size = 32;

                RenderTarget target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
                target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);

                RenderSystem.assertOnRenderThread();

                for (Item item : BuiltInRegistries.ITEM) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    if (!id.getNamespace().equals(namespace)) continue;

                    ItemStack stack = new ItemStack(item);

                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);

                    RenderSystem.backupProjectionMatrix();
                    Matrix4f projection = new Matrix4f().setOrtho(0, size, size, 0, -1000, 3000);
                    RenderSystem.setProjectionMatrix(projection, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

                    PoseStack modelViewStack = RenderSystem.getModelViewStack();
                    modelViewStack.pushPose();
                    modelViewStack.setIdentity();
                    RenderSystem.applyModelViewMatrix();

                    Lighting.setupFor3DItems();
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableCull();
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                    GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                    graphics.pose().pushPose();
                    float scale = size / 16f;
                    graphics.pose().scale(scale, scale, 1.0f);

                    try {
                        graphics.renderItem(stack, 0, 0);
                    } catch (Exception e) {
                    }

                    graphics.pose().popPose();
                    mc.renderBuffers().bufferSource().endBatch();
                    NativeImage nativeImage = new NativeImage(size, size, true);
                    RenderSystem.bindTexture(target.getColorTextureId());
                    nativeImage.downloadTexture(0, false);
                    nativeImage.flipY();

                    BufferedImage bufferedImage = nativeImageToBufferedImage(nativeImage);
                    renderedTextures.add(new TextureEntry(id.toString(), bufferedImage));

                    nativeImage.close();

                    modelViewStack.popPose();
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.restoreProjectionMatrix();
                }

                target.destroyBuffers();

                if (!renderedTextures.isEmpty()) {
                    renderedTextures.sort((a, b) -> Integer.compare(b.image.getHeight(), a.image.getHeight()));
                    exportIcons(renderedTextures, exportDir, "rendered");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, mc).join();
    }

    private static BufferedImage nativeImageToBufferedImage(NativeImage nativeImage) {
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = nativeImage.getPixelRGBA(x, y);
                int a = (rgba >> 24) & 0xFF;
                int b = (rgba >> 16) & 0xFF;
                int g = (rgba >> 8) & 0xFF;
                int r = (rgba >> 0) & 0xFF;

                int argb = (a << 24) | (r << 16) | (g << 8) | (b << 0);
                bufferedImage.setRGB(x, y, argb);
            }
        }
        return bufferedImage;
    }

    private static void exportBlockDefinitions(Map<ResourceLocation, Resource> resources, File exportDir, String namespace) throws IOException {
        JsonObject root = new JsonObject();
        boolean hasData = false;

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                String path = location.getPath();
                String name = path.substring("blockstates/".length(), path.length() - ".json".length());
                String key = /*location.getNamespace() + ":" +*/ name;
                root.add(key, json);
                hasData = true;
            } catch (Exception e) {
                System.err.println("Failed to parse blockstate " + location + ": " + e.getMessage());
            }
        }

        if (hasData) {
            File outputFile = new File(exportDir, "assets/block_definition/data.min.json");
            outputFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        }
    }

    private static void exportBlockModels(Map<ResourceLocation, Resource> resources, File exportDir, String namespace) throws IOException {
        JsonObject root = new JsonObject();
        boolean hasData = false;

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;

            String path = location.getPath();

            // Handle JSON models
            if (path.endsWith(".json")) {
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement json = JsonParser.parseReader(reader);
                    String name = path.substring("models/".length(), path.length() - ".json".length());
                    String key = /*location.getNamespace() + ":" +*/ name;
                    root.add(key, json);
                    hasData = true;
                } catch (Exception e) {
                    System.err.println("Failed to parse model " + location + ": " + e.getMessage());
                }
            }
            // Handle Raw Models (OBJ, MTL, etc.)
            else if (path.endsWith(".obj")) {
                try (InputStream in = entry.getValue().open()) {
                    JsonObject objJson = convertObjToJson(in);
                    String name = path.substring("models/".length(), path.length() - ".obj".length());
                    root.add(name, objJson);
                    hasData = true;
                } catch (Exception e) {
                    System.err.println("Failed to convert obj model " + location + ": " + e.getMessage());
                }
            }
            else if (path.endsWith(".mtl")) {
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
                    System.err.println("Failed to export raw model " + location + ": " + e.getMessage());
                }
            }
        }

        if (hasData) {
            File outputFile = new File(exportDir, "assets/model/data.min.json");
            outputFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        }
    }

    private static void exportItemModels(Map<ResourceLocation, Resource> resources, File exportDir, String namespace) throws IOException {
        JsonObject root = new JsonObject();
        boolean hasData = false;

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;

            String path = location.getPath();

            // Handle JSON models
            if (path.endsWith(".json")) {
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement json = JsonParser.parseReader(reader);
                    String name = path.substring("models/".length(), path.length() - ".json".length());
                    String key = /*location.getNamespace() + ":" + */name;
                    root.add(key, json);
                    hasData = true;
                } catch (Exception e) {
                    System.err.println("Failed to parse item model " + location + ": " + e.getMessage());
                }
            }
            // Handle Raw Models (OBJ, MTL, etc.) for items
            else if (path.endsWith(".obj")) {
                try (InputStream in = entry.getValue().open()) {
                    JsonObject objJson = convertObjToJson(in);
                    String name = path.substring("models/".length(), path.length() - ".obj".length());
                    root.add(name, objJson);
                    hasData = true;
                } catch (Exception e) {
                    System.err.println("Failed to convert obj item model " + location + ": " + e.getMessage());
                }
            }
            else if (path.endsWith(".mtl")) {
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
                    System.err.println("Failed to export raw item model " + location + ": " + e.getMessage());
                }
            }
        }

        if (hasData) {
            File outputFile = new File(exportDir, "assets/item_definition/data.min.json");
            outputFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        }
    }

    private static void exportOpaqueBlocks(File exportDir, String namespace) throws IOException {
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
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            opaque.forEach(array::add);
            root.add("opaque", array);

            File outputFile = new File(exportDir, "assets/opaque/blocks.json");
            outputFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        }
    }

    private static void exportAtlas(List<TextureEntry> textures, File exportDir) throws IOException {
        File atlasDir = new File(exportDir, "assets/atlas");
        atlasDir.mkdirs();
        generateAtlas(textures, atlasDir, "atlas.png", "data.min.json");
    }

    private static void exportIcons(List<TextureEntry> textures, File exportDir, String subFolder) throws IOException {
        File iconsDir = new File(exportDir, "icons");
        File targetDir = new File(iconsDir, subFolder);
        targetDir.mkdirs();

        // Export originals
        for (TextureEntry entry : textures) {
            String subPath = entry.key.substring(entry.key.indexOf(":") + 1) + ".png";
            File file = new File(targetDir, subPath);
            file.getParentFile().mkdirs();
            ImageIO.write(entry.image, "png", file);
        }

        generateAtlas(textures, iconsDir, "atlas.png", "data.min.json");
    }

    private static void generateAtlas(List<TextureEntry> textures, File outputDir, String imageName, String jsonName) throws IOException {
        int maxWidth = 4096;
        int currentX = 0;
        int currentY = 0;
        int rowHeight = 0;

        List<PackedTexture> packed = new ArrayList<>();

        for (TextureEntry entry : textures) {
            if (currentX + entry.image.getWidth() > maxWidth) {
                currentX = 0;
                currentY += rowHeight;
                rowHeight = 0;
            }

            packed.add(new PackedTexture(entry, currentX, currentY));

            rowHeight = Math.max(rowHeight, entry.image.getHeight());
            currentX += entry.image.getWidth();
        }

        int totalHeight = currentY + rowHeight;
        if (totalHeight == 0) totalHeight = 1;

        BufferedImage atlas = new BufferedImage(maxWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = atlas.createGraphics();

        JsonObject atlasJson = new JsonObject();

        for (PackedTexture p : packed) {
            g2d.drawImage(p.entry.image, p.x, p.y, null);
            com.google.gson.JsonArray rect = new com.google.gson.JsonArray();
            rect.add(p.x);
            rect.add(p.y);
            rect.add(p.entry.image.getWidth());
            rect.add(p.entry.image.getHeight());
            atlasJson.add(p.entry.key, rect);
        }
        g2d.dispose();

        File atlasFile = new File(outputDir, imageName);
        ImageIO.write(atlas, "png", atlasFile);

        File jsonFile = new File(outputDir, jsonName);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(jsonFile), StandardCharsets.UTF_8)) {
            GSON.toJson(atlasJson, writer);
        }
    }

    private static void collectTextures(Map<ResourceLocation, Resource> resources, String namespace, String prefixToRemove, List<TextureEntry> textures) {
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;

            try (InputStream stream = entry.getValue().open()) {
                BufferedImage image = ImageIO.read(stream);
                if (image != null) {
                    String path = location.getPath();
                    String name = path.substring(prefixToRemove.length(), path.length() - ".png".length());
                    String key = /*location.getNamespace() + ":" +*/ name;
                    textures.add(new TextureEntry(key, image));
                }
            } catch (Exception e) {
            }
        }
    }

    private static void collectMtlTextures(ResourceManager manager, Map<ResourceLocation, Resource> models, String namespace, List<TextureEntry> textures) {
        for (Map.Entry<ResourceLocation, Resource> entry : models.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;
            if (!location.getPath().endsWith(".mtl")) continue;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String texturePath = null;
                    if (line.startsWith("map_Kd ")) texturePath = line.substring(7).trim();
                    else if (line.startsWith("map_Ka ")) texturePath = line.substring(7).trim();
                    else if (line.startsWith("map_Ks ")) texturePath = line.substring(7).trim();
                    else if (line.startsWith("map_d ")) texturePath = line.substring(6).trim();

                    if (texturePath != null && !texturePath.isEmpty()) {
                        ResourceLocation texLoc;
                        if (texturePath.contains(":")) {
                            texLoc = new ResourceLocation(texturePath);
                        } else {
                            texLoc = new ResourceLocation(namespace, texturePath);
                        }

                        Optional<Resource> res = manager.getResource(texLoc);
                        if (res.isEmpty() && !texLoc.getPath().startsWith("textures/")) {
                            texLoc = new ResourceLocation(texLoc.getNamespace(), "textures/" + texLoc.getPath());
                            res = manager.getResource(texLoc);
                        }

                        if (res.isPresent()) {
                            try (InputStream stream = res.get().open()) {
                                BufferedImage image = ImageIO.read(stream);
                                if (image != null) {
                                    String path = texLoc.getPath();
                                    String key = path.startsWith("textures/") ? path.substring("textures/".length()) : path;
                                    if (key.endsWith(".png")) key = key.substring(0, key.length() - 4);

                                    boolean exists = false;
                                    for (TextureEntry t : textures) {
                                        if (t.key.equals(key)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        textures.add(new TextureEntry(key, image));
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse MTL " + location + ": " + e.getMessage());
            }
        }
    }

    private static void exportMetadata(File exportDir, String namespace) {
        try {
            com.google.gson.JsonArray itemsArray = new com.google.gson.JsonArray();
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

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JsonObject convertObjToJson(InputStream inputStream) throws IOException {
        JsonObject model = new JsonObject();
        com.google.gson.JsonArray vertices = new com.google.gson.JsonArray();
        com.google.gson.JsonArray texCoords = new com.google.gson.JsonArray();
        com.google.gson.JsonArray normals = new com.google.gson.JsonArray();
        com.google.gson.JsonArray faces = new com.google.gson.JsonArray();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length == 0) continue;

                switch (parts[0]) {
                    case "v":
                        for (int i = 1; i < 4 && i < parts.length; i++) vertices.add(Float.parseFloat(parts[i]));
                        break;
                    case "vt":
                        for (int i = 1; i < 3 && i < parts.length; i++) texCoords.add(Float.parseFloat(parts[i]));
                        break;
                    case "vn":
                        for (int i = 1; i < 4 && i < parts.length; i++) normals.add(Float.parseFloat(parts[i]));
                        break;
                    case "f":
                        com.google.gson.JsonArray face = new com.google.gson.JsonArray();
                        for (int i = 1; i < parts.length; i++) {
                            face.add(parts[i]);
                        }
                        faces.add(face);
                        break;
                }
            }
        }

        model.addProperty("loader", "forge:obj");
        model.add("vertices", vertices);
        model.add("tex_coords", texCoords);
        model.add("normals", normals);
        model.add("faces", faces);
        return model;
    }

    private static class TextureEntry {
        String key;
        BufferedImage image;

        public TextureEntry(String key, BufferedImage image) {
            this.key = key;
            this.image = image;
        }
    }

    private static class PackedTexture {
        TextureEntry entry;
        int x, y;

        public PackedTexture(TextureEntry entry, int x, int y) {
            this.entry = entry;
            this.x = x;
            this.y = y;
        }
    }
}
