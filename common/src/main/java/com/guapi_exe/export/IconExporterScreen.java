package com.guapi_exe.export;

import com.guapi_exe.util.ExporterLogger;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Screen for rendering and exporting item icons.
 * Processes multiple items per frame for faster export.
 */
public class IconExporterScreen extends Screen {
    private static final int BACKGROUND_COLOR = (255 << 24) | (254 << 16) | (255 << 8) | 255; // 0xFFFEFFFF
    private static final int BACKGROUND_COLOR_SHIFTED = (255 << 24) | (255 << 16) | (255 << 8) | 254;

    private final List<ItemStack> itemsToExport;
    private final File exportDir;
    private final String namespace;
    private final Consumer<Component> feedback;
    private final Runnable onComplete;
    private final float scale;
    private final int iconSize;
    private final int itemsPerFrame;
    private int currentIndex = 0;
    private int lastReportedProgress = -1;

    public IconExporterScreen(List<ItemStack> items, File exportDir, String namespace,
                              Consumer<Component> feedback, Runnable onComplete) {
        super(Component.literal("Icon Exporter"));
        this.itemsToExport = items;
        this.exportDir = exportDir;
        this.namespace = namespace;
        this.feedback = feedback;
        this.onComplete = onComplete;

        ExportSettings settings = ExportSettings.getInstance();
        this.iconSize = settings.getIconSize();
        this.itemsPerFrame = settings.getItemsPerFrame();

        Minecraft mc = Minecraft.getInstance();
        this.scale = (float) (iconSize / mc.getWindow().getGuiScale());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (currentIndex >= itemsToExport.size()) {
            finishExport();
            return;
        }

        // Process multiple items per frame for faster export
        int itemsProcessed = 0;
        while (currentIndex < itemsToExport.size() && itemsProcessed < itemsPerFrame) {
            ItemStack stack = itemsToExport.get(currentIndex);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());

            try {
                int scaleRounded = (int) Math.ceil(scale);
                guiGraphics.fill(0, 0, scaleRounded, scaleRounded, BACKGROUND_COLOR);

                renderItem(guiGraphics, stack, scale);

                // Flush the render buffer to ensure item is rendered
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch();

                exportImageFromScreenshot(exportDir, id.getPath(), iconSize, BACKGROUND_COLOR_SHIFTED);
            } catch (Exception e) {
                ExporterLogger.error("Failed to export {}: {}", id, e.getMessage());
            }

            currentIndex++;
            itemsProcessed++;
        }

        int progressPercent = (currentIndex * 100) / itemsToExport.size();
        int progressDecile = progressPercent / 10;
        if (progressDecile != lastReportedProgress) {
            lastReportedProgress = progressDecile;
            feedback.accept(Component.literal("Rendering items: " + currentIndex + "/" + itemsToExport.size() + " (" + progressPercent + "%)"));
        }
    }

    @Override
    protected void renderBlurredBackground(float partialTicks) {
        // No background blur
    }

    private void finishExport() {
        try {
            generateRenderedAtlas(exportDir);
            feedback.accept(Component.literal("Exported " + itemsToExport.size() + " rendered icons and atlas for " + namespace));
            ExporterLogger.info("Exported {} rendered icons for {}", itemsToExport.size(), namespace);
        } catch (Exception e) {
            ExporterLogger.error("Failed to generate rendered atlas: {}", e.getMessage());
            feedback.accept(Component.literal("Exported " + itemsToExport.size() + " rendered icons (atlas failed)"));
        }

        Minecraft.getInstance().setScreen(null);
        if (onComplete != null) {
            Minecraft.getInstance().execute(onComplete);
        }
    }

    /**
     * Render item at specified scale.
     */
    private void renderItem(GuiGraphics gui, ItemStack itemStack, float scale) {
        var poseStack = gui.pose();
        poseStack.pushPose();

        poseStack.scale(scale / 16, scale / 16, 1);
        poseStack.translate(0, 0, 100);
        poseStack.translate(8, 8, 0);
        poseStack.mulPose(new org.joml.Matrix4f().scaling(1, -1, 1));
        poseStack.scale(16, 16, 16);

        Minecraft mc = Minecraft.getInstance();
        var bufferSource = mc.renderBuffers().bufferSource();
        BakedModel model = mc.getItemRenderer().getModel(itemStack, null, null, 0);

        boolean useFlatLighting = !model.usesBlockLight();
        if (useFlatLighting) {
            Lighting.setupForFlatItems();
        }

        mc.getItemRenderer().render(itemStack, ItemDisplayContext.GUI, false, poseStack, bufferSource,
                15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();

        if (useFlatLighting) {
            Lighting.setupFor3DItems();
        }

        poseStack.popPose();
        Lighting.setupFor3DItems();
    }

    /**
     * Take screenshot and export to file.
     */
    private void exportImageFromScreenshot(File dir, String baseFilename, int scaleImage, int backgroundColor) throws IOException {
        NativeImage imageFull = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        NativeImage image = getSubImage(imageFull, scaleImage, scaleImage);
        imageFull.close();

        // Replace background color with transparency
        for (int cx = 0; cx < image.getWidth(); cx++) {
            for (int cy = 0; cy < image.getHeight(); cy++) {
                int color = image.getPixelRGBA(cx, cy);
                if (color == backgroundColor) {
                    image.setPixelRGBA(cx, cy, 0);
                }
            }
        }

        File iconsDir = new File(dir, "icons/rendered");
        iconsDir.mkdirs();
        File file = new File(iconsDir, baseFilename + ".png");
        image.writeToFile(file);
        image.close();
    }

    /**
     * Extract sub-image from top-left corner.
     */
    private NativeImage getSubImage(NativeImage image, int width, int height) {
        NativeImage imageNew = new NativeImage(width, height, false);
        for (int y = 0; y < height && y < image.getHeight(); y++) {
            for (int x = 0; x < width && x < image.getWidth(); x++) {
                imageNew.setPixelRGBA(x, y, image.getPixelRGBA(x, y));
            }
        }
        return imageNew;
    }

    /**
     * Generate atlas from rendered icons.
     */
    private void generateRenderedAtlas(File exportDir) throws IOException {
        File renderedDir = new File(exportDir, "icons/rendered");
        if (!renderedDir.exists()) return;

        List<TextureEntry> textures = new ArrayList<>();
        File[] files = renderedDir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) return;

        for (File file : files) {
            try {
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    String name = file.getName();
                    name = name.substring(0, name.length() - 4);
                    textures.add(new TextureEntry(this.namespace + ":" + name, img));
                }
            } catch (Exception e) {
                ExporterLogger.warn("Failed to read rendered icon: {}", file.getName());
            }
        }

        if (textures.isEmpty()) return;

        File iconsDir = new File(exportDir, "icons");
        AtlasGenerator.generateAtlas(textures, iconsDir, "atlas.png", "data.min.json");
    }
}
