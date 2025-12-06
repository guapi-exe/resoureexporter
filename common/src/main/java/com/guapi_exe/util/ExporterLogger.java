package com.guapi_exe.util;

import com.guapi_exe.ResoureExporterMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for the Resource Exporter mod.
 */
public final class ExporterLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResoureExporterMod.MOD_ID);

    private ExporterLogger() {
        // Utility class, no instantiation
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void info(String format, Object... args) {
        LOGGER.info(format, args);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void warn(String format, Object... args) {
        LOGGER.warn(format, args);
    }

    public static void error(String message) {
        LOGGER.error(message);
    }

    public static void error(String format, Object... args) {
        LOGGER.error(format, args);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    public static void debug(String message) {
        LOGGER.debug(message);
    }

    public static void debug(String format, Object... args) {
        LOGGER.debug(format, args);
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
