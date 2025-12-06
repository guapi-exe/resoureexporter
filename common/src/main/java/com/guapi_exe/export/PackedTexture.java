package com.guapi_exe.export;

/**
 * Represents a packed texture with position information in an atlas.
 */
public class PackedTexture {
    private final TextureEntry entry;
    private final int x;
    private final int y;

    public PackedTexture(TextureEntry entry, int x, int y) {
        this.entry = entry;
        this.x = x;
        this.y = y;
    }

    public TextureEntry getEntry() {
        return entry;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
