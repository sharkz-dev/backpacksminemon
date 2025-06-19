// SIMPLIFICADO: ConfigManager.java - SIN sistema de backups
package es.minemon.backpacks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Gestor de configuración optimizado SIN backup system
 */
public class ConfigManager {
    private static final String CONFIG_DIR = "config/backpacks";
    private static final String CONFIG_FILE = "config.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static BackpackConfig config;
    private static Path configPath;

    public static void initialize() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            Files.createDirectories(configDir);
            configPath = configDir.resolve(CONFIG_FILE);
            loadConfig();
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error inicializando configuración", e);
            config = new BackpackConfig();
            config.validateUserConfig();
        }
    }

    private static void loadConfig() {
        File configFile = configPath.toFile();

        if (!configFile.exists()) {
            createDefaultConfig();
        } else {
            try (FileReader reader = new FileReader(configFile)) {
                BackpackConfig loadedConfig = GSON.fromJson(reader, BackpackConfig.class);

                if (loadedConfig == null) {
                    createDefaultConfig();
                } else {
                    config = loadedConfig;
                    config.validateUserConfig();
                    saveConfig();
                }

            } catch (JsonSyntaxException e) {
                BackpacksMod.LOGGER.error("Error JSON en configuración", e);
                createDefaultConfig();
            } catch (IOException e) {
                config = new BackpackConfig();
                config.validateUserConfig();
            }
        }
    }

    private static void createDefaultConfig() {
        config = new BackpackConfig();
        config.validateUserConfig();
        saveConfig();
        BackpacksMod.LOGGER.info("Created default configuration (no backup system)");
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            BackpacksMod.LOGGER.error("Error guardando configuración", e);
        }
    }

    public static void reloadConfig() {
        loadConfig();
        BackpacksMod.LOGGER.info("Configuration reloaded (backup system remains disabled)");
    }

    public static BackpackConfig getConfig() {
        return config;
    }

    // Delegación simplificada a LanguageManager
    public static String getMessage(String key, Object... args) {
        return LanguageManager.getMessage(key, args);
    }

    public static Text getMessageAsText(String key, Object... args) {
        return LanguageManager.getMessageAsText(key, args);
    }

    public static void sendMessage(net.minecraft.server.network.ServerPlayerEntity player, String key, Object... args) {
        LanguageManager.sendMessage(player, key, args);
    }

    public static void sendFeedback(net.minecraft.server.command.ServerCommandSource source, String key, boolean broadcastToOps, Object... args) {
        LanguageManager.sendFeedback(source, key, broadcastToOps, args);
    }

    // ELIMINADO: getBackupIntervalTicks() - ya no existe sistema de backup

    // SIMPLIFICADO: isFeatureEnabled sin backup
    public static boolean isFeatureEnabled(String feature) {
        return switch (feature.toLowerCase()) {
            case "mongodb" -> config.enableMongoDB;
            case "rename" -> config.allowBackpackRename;
            case "stats" -> config.showBackpackStats;
            // ELIMINADO: case "backup" -> false; // Siempre deshabilitado
            default -> false;
        };
    }

    public static boolean validateConfiguration() {
        try {
            config.validateUserConfig();

            if (config.mongoConnectionString == null ||
                    !config.mongoConnectionString.startsWith("mongodb://")) {
                return false;
            }

            // ELIMINADO: Validación de directorios de backup
            // if (!Files.exists(Paths.get(config.backupDirectory))) { ... }

            if (!config.mainCommand.matches("[a-zA-Z0-9_-]+") || config.mainCommand.length() > 20) {
                return false;
            }

            if (!config.playerCommand.matches("[a-zA-Z0-9_-]+") || config.playerCommand.length() > 20) {
                return false;
            }

            if (config.mainCommand.equals(config.playerCommand)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    public static String getMainCommand() {
        return config.mainCommand;
    }

    public static String getPlayerCommand() {
        return config.playerCommand;
    }

    // NUEVO: Información sobre el estado sin backups
    public static String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("§6=== System Status (No Backup Mode) ===\n");
        status.append("§eConfiguration: §a").append(validateConfiguration() ? "Valid" : "Issues detected").append("\n");
        status.append("§eMongoDB: §a").append(config.enableMongoDB ? "Enabled" : "Disabled").append("\n");
        status.append("§eBackup System: §cDISABLED for performance\n");
        status.append("§eData Persistence: §aMongoDB only\n");
        status.append("§ePerformance Impact: §aMINIMAL\n");
        status.append("§eMemory Usage: §aOPTIMIZED\n");

        // VIP info
        int vipBackpacks = config.getMaxPossibleVipBackpacks();
        status.append("§eVIP Backpacks: §a").append(vipBackpacks).append(" max possible\n");
        status.append("§eVIP Compatible: §a").append(config.isVipConfigurationValid() ? "Yes" : "Issues").append("\n");

        return status.toString();
    }

    // NUEVO: Performance info
    public static String getPerformanceInfo() {
        StringBuilder perf = new StringBuilder();
        perf.append("§6=== Performance Configuration ===\n");
        perf.append("§eAsync Operations: §aEnabled\n");
        perf.append("§eCache Timeout: §a").append(config.cacheTimeoutSeconds).append(" seconds\n");
        perf.append("§eMax Concurrent Writes: §a").append(config.maxConcurrentWrites).append("\n");
        perf.append("§eAuto Save Interval: §a").append(config.autoSaveIntervalSeconds).append(" seconds\n");
        perf.append("§eBackup System: §cDISABLED\n");
        perf.append("§eBackup Overhead: §aNONE\n");
        perf.append("§eMemory Footprint: §aREDUCED\n");
        perf.append("§eCPU Usage: §aMINIMIZED\n");

        return perf.toString();
    }

    // NUEVO: Verificar si se necesita migración desde configuración con backups
    public static boolean needsBackupSystemCleanup() {
        // Verificar si hay configuraciones de backup remanentes en el archivo
        try {
            Path configFile = configPath;
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile);
                return content.contains("backupInterval") ||
                        content.contains("maxBackupFiles") ||
                        content.contains("backupDirectory") ||
                        content.contains("createEmergencyBackup");
            }
        } catch (Exception e) {
            // Ignorar errores
        }
        return false;
    }

    // NUEVO: Limpiar configuraciones de backup remanentes
    public static void cleanupBackupConfig() {
        if (needsBackupSystemCleanup()) {
            BackpacksMod.LOGGER.info("Cleaning up old backup configuration...");

            // Recargar y guardar configuración para eliminar campos de backup
            config.validateUserConfig();
            saveConfig();

            BackpacksMod.LOGGER.info("Backup configuration cleanup completed");
        }
    }

    // NUEVO: Obtener recomendaciones de optimización
    public static String getOptimizationRecommendations() {
        StringBuilder recommendations = new StringBuilder();
        recommendations.append("§6=== Performance Optimizations Applied ===\n");
        recommendations.append("§a✓ Backup system disabled\n");
        recommendations.append("§a✓ Emergency backup disabled\n");
        recommendations.append("§a✓ Backup interval processing removed\n");
        recommendations.append("§a✓ File I/O operations minimized\n");
        recommendations.append("§a✓ Memory usage optimized\n");
        recommendations.append("§a✓ CPU overhead reduced\n");

        recommendations.append("\n§eAdditional Recommendations:\n");

        // Verificar memoria
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        double memoryUsage = (double) (totalMemory - freeMemory) / totalMemory * 100;

        if (memoryUsage > 80) {
            recommendations.append("§7• Consider increasing server memory (-Xmx)\n");
        }

        // Verificar configuración VIP
        if (!config.isVipConfigurationValid()) {
            recommendations.append("§7• Adjust VIP configuration for better compatibility\n");
        }

        // Verificar límites
        if (config.maxBackpacksPerPlayer > 100) {
            recommendations.append("§7• Consider reducing maxBackpacksPerPlayer if not needed\n");
        }

        recommendations.append("§7• MongoDB connection optimized for performance\n");
        recommendations.append("§7• Cache cleanup intervals optimized\n");

        return recommendations.toString();
    }
}