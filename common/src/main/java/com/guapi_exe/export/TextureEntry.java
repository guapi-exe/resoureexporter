package com.guapi_exe.export;

import java.awt.image.BufferedImage;

/**
 * Represents a texture entry with a key identifier and image data.
 */
public class TextureEntry {
    private final String key;
    private final BufferedImage image;

    public TextureEntry(String key, BufferedImage image) {
        this.key = key;
        this.image = image;
    }

    public String getKey() {
        return key;
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }
}
