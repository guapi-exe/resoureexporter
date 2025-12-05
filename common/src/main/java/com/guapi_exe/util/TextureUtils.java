package com.guapi_exe.util;

import com.guapi_exe.export.TextureEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for texture collection and processing.
 */
public final class TextureUtils {

    private TextureUtils() {
        // Utility class, no instantiation
    }

    /**
     * Collect textures from resource map.
     */
    public static void collectTextures(Map<ResourceLocation, Resource> resources, String namespace,
                                       String prefixToRemove, List<TextureEntry> textures) {
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;

            try (InputStream stream = entry.getValue().open()) {
                BufferedImage image = ImageIO.read(stream);
                if (image != null) {
                    String path = location.getPath();
                    String name = path.substring(prefixToRemove.length(), path.length() - ".png".length());
                    textures.add(new TextureEntry(name, image));
                }
            } catch (Exception e) {
                ExporterLogger.debug("Failed to collect texture {}: {}", location, e.getMessage());
            }
        }
    }

    /**
     * Collect textures referenced in MTL files.
     */
    public static void collectMtlTextures(ResourceManager manager, Map<ResourceLocation, Resource> models,
                                          String namespace, List<TextureEntry> textures) {
        for (Map.Entry<ResourceLocation, Resource> entry : models.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(namespace)) continue;
            if (!location.getPath().endsWith(".mtl")) continue;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String texturePath = extractTexturePathFromMtl(line);
                    if (texturePath != null && !texturePath.isEmpty()) {
                        processTexturePath(manager, namespace, texturePath, textures);
                    }
                }
            } catch (Exception e) {
                ExporterLogger.error("Failed to parse MTL {}: {}", location, e.getMessage());
            }
        }
    }

    private static String extractTexturePathFromMtl(String line) {
        if (line.startsWith("map_Kd ")) return line.substring(7).trim();
        if (line.startsWith("map_Ka ")) return line.substring(7).trim();
        if (line.startsWith("map_Ks ")) return line.substring(7).trim();
        if (line.startsWith("map_d ")) return line.substring(6).trim();
        return null;
    }

    private static void processTexturePath(ResourceManager manager, String namespace, String texturePath,
                                           List<TextureEntry> textures) {
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

                    // Check for duplicates
                    final String finalKey = key;
                    boolean exists = textures.stream().anyMatch(t -> t.getKey().equals(finalKey));
                    if (!exists) {
                        textures.add(new TextureEntry(key, image));
                    }
                }
            } catch (Exception e) {
                ExporterLogger.debug("Failed to load MTL texture {}: {}", texLoc, e.getMessage());
            }
        }
    }
}
