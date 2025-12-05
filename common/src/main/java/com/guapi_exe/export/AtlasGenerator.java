package com.guapi_exe.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.guapi_exe.util.ExporterLogger;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for generating texture atlases from multiple images.
 */
public final class AtlasGenerator {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_ATLAS_WIDTH = 4096;

    private AtlasGenerator() {
        // Utility class, no instantiation
    }

    /**
     * Generate an atlas from a list of textures.
     *
     * @param textures  List of texture entries to pack
     * @param outputDir Output directory for atlas files
     * @param imageName Name of the output atlas image
     * @param jsonName  Name of the output JSON metadata file
     * @throws IOException If writing fails
     */
    public static void generateAtlas(List<TextureEntry> textures, File outputDir,
                                     String imageName, String jsonName) throws IOException {
        if (textures.isEmpty()) {
            ExporterLogger.debug("No textures to generate atlas");
            return;
        }

        List<PackedTexture> packed = packTextures(textures);

        int atlasWidth = MAX_ATLAS_WIDTH;
        int atlasHeight = calculateAtlasHeight(packed, textures);
        if (atlasHeight == 0) atlasHeight = 1;

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = atlas.createGraphics();

        JsonObject atlasJson = new JsonObject();

        for (PackedTexture p : packed) {
            g2d.drawImage(p.getEntry().getImage(), p.getX(), p.getY(), null);

            com.google.gson.JsonArray rect = new com.google.gson.JsonArray();
            rect.add(p.getX());
            rect.add(p.getY());
            rect.add(p.getEntry().getWidth());
            rect.add(p.getEntry().getHeight());
            atlasJson.add(p.getEntry().getKey(), rect);
        }
        g2d.dispose();

        outputDir.mkdirs();
        ImageIO.write(atlas, "png", new File(outputDir, imageName));

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(new File(outputDir, jsonName)), StandardCharsets.UTF_8)) {
            GSON.toJson(atlasJson, writer);
        }

        ExporterLogger.info("Generated atlas with {} textures", textures.size());
    }

    /**
     * Generate an atlas with detailed position info (used for rendered icons).
     */
    public static void generateAtlasDetailed(List<TextureEntry> textures, File outputDir,
                                              String imageName, String jsonName) throws IOException {
        if (textures.isEmpty()) {
            ExporterLogger.debug("No textures to generate detailed atlas");
            return;
        }

        // Sort by height descending for better packing
        textures.sort((a, b) -> Integer.compare(b.getHeight(), a.getHeight()));

        List<PackedTexture> packed = packTextures(textures);

        int atlasWidth = MAX_ATLAS_WIDTH;
        int atlasHeight = calculateAtlasHeight(packed, textures);

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();

        JsonObject json = new JsonObject();

        for (PackedTexture pt : packed) {
            g.drawImage(pt.getEntry().getImage(), pt.getX(), pt.getY(), null);

            JsonObject entry = new JsonObject();
            entry.addProperty("x", pt.getX());
            entry.addProperty("y", pt.getY());
            entry.addProperty("w", pt.getEntry().getWidth());
            entry.addProperty("h", pt.getEntry().getHeight());
            json.add(pt.getEntry().getKey(), entry);
        }

        g.dispose();

        outputDir.mkdirs();
        ImageIO.write(atlas, "png", new File(outputDir, imageName));

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(new File(outputDir, jsonName)), StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }

        ExporterLogger.info("Generated detailed atlas with {} textures", textures.size());
    }

    private static List<PackedTexture> packTextures(List<TextureEntry> textures) {
        List<PackedTexture> packed = new ArrayList<>();
        int currentX = 0;
        int currentY = 0;
        int rowHeight = 0;

        for (TextureEntry entry : textures) {
            if (currentX + entry.getWidth() > MAX_ATLAS_WIDTH) {
                currentX = 0;
                currentY += rowHeight;
                rowHeight = 0;
            }

            packed.add(new PackedTexture(entry, currentX, currentY));
            currentX += entry.getWidth();
            rowHeight = Math.max(rowHeight, entry.getHeight());
        }

        return packed;
    }

    private static int calculateAtlasHeight(List<PackedTexture> packed, List<TextureEntry> textures) {
        if (packed.isEmpty()) return 0;

        int maxY = 0;
        for (PackedTexture pt : packed) {
            int bottom = pt.getY() + pt.getEntry().getHeight();
            maxY = Math.max(maxY, bottom);
        }
        return maxY;
    }
}
