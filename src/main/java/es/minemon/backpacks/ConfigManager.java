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
 * Gestor de configuración optimizado - Solo configuraciones esenciales
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

    // Métodos de configuración simplificados
    public static int getBackupIntervalTicks() {
        return config.backupIntervalMinutes * 20 * 60;
    }

    public static boolean isFeatureEnabled(String feature) {
        return switch (feature.toLowerCase()) {
            case "backup" -> config.enableBackupSystem;
            case "mongodb" -> config.enableMongoDB;
            case "rename" -> config.allowBackpackRename;
            case "stats" -> config.showBackpackStats;
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

            if (!Files.exists(Paths.get(config.backupDirectory))) {
                try {
                    Files.createDirectories(Paths.get(config.backupDirectory));
                } catch (IOException e) {
                    return false;
                }
            }

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
}