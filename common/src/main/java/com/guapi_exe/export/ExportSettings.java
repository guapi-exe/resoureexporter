package com.guapi_exe.export;

/**
 * Configuration settings for resource export.
 */
public final class ExportSettings {

    /** Default icon size in pixels */
    public static final int DEFAULT_ICON_SIZE = 32;

    /** Default number of items to process per frame */
    public static final int DEFAULT_ITEMS_PER_FRAME = 1;

    /** Minimum allowed icon size */
    public static final int MIN_ICON_SIZE = 16;

    /** Maximum allowed icon size */
    public static final int MAX_ICON_SIZE = 512;

    /** Minimum items per frame */
    public static final int MIN_ITEMS_PER_FRAME = 1;

    /** Maximum items per frame */
    public static final int MAX_ITEMS_PER_FRAME = 200;

    private int iconSize;
    private int itemsPerFrame;

    private static ExportSettings instance;

    private ExportSettings() {
        this.iconSize = DEFAULT_ICON_SIZE;
        this.itemsPerFrame = DEFAULT_ITEMS_PER_FRAME;
    }

    /**
     * Get the singleton instance.
     */
    public static ExportSettings getInstance() {
        if (instance == null) {
            instance = new ExportSettings();
        }
        return instance;
    }

    /**
     * Get the icon size in pixels.
     */
    public int getIconSize() {
        return iconSize;
    }

    /**
     * Set the icon size in pixels.
     * @param size Icon size (clamped to MIN_ICON_SIZE - MAX_ICON_SIZE)
     */
    public void setIconSize(int size) {
        this.iconSize = Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, size));
    }

    /**
     * Get the number of items to process per frame.
     */
    public int getItemsPerFrame() {
        return itemsPerFrame;
    }

    /**
     * Set the number of items to process per frame.
     * @param count Items per frame (clamped to MIN_ITEMS_PER_FRAME - MAX_ITEMS_PER_FRAME)
     */
    public void setItemsPerFrame(int count) {
        this.itemsPerFrame = Math.max(MIN_ITEMS_PER_FRAME, Math.min(MAX_ITEMS_PER_FRAME, count));
    }

    /**
     * Reset all settings to defaults.
     */
    public void reset() {
        this.iconSize = DEFAULT_ICON_SIZE;
        this.itemsPerFrame = DEFAULT_ITEMS_PER_FRAME;
    }

    @Override
    public String toString() {
        return "ExportSettings{iconSize=" + iconSize + ", itemsPerFrame=" + itemsPerFrame + "}";
    }
}
