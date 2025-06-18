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
 * Gestor de configuración simplificado para BackpacksMod
 * Solo maneja configuraciones del mod, los mensajes van en LanguageManager
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
            BackpacksMod.LOGGER.info("Configuration loaded from: " + configPath);
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error initializing configuration", e);
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
                    BackpacksMod.LOGGER.warn("Null configuration, using default values");
                    createDefaultConfig();
                } else {
                    config = loadedConfig;
                    config.validateUserConfig();
                    saveConfig();
                    BackpacksMod.LOGGER.info("Configuration loaded and validated");
                }

            } catch (JsonSyntaxException e) {
                BackpacksMod.LOGGER.error("JSON syntax error in configuration file", e);
                BackpacksMod.LOGGER.info("Creating new default configuration");
                createDefaultConfig();
            } catch (IOException e) {
                BackpacksMod.LOGGER.error("Error reading configuration file", e);
                config = new BackpackConfig();
                config.validateUserConfig();
            }
        }
    }

    private static void createDefaultConfig() {
        config = new BackpackConfig();
        config.validateUserConfig();
        saveConfig();

        BackpacksMod.LOGGER.info("Configuration file created with optimized values");
        BackpacksMod.LOGGER.info("Commands configured: /" + config.mainCommand + " (admin), /" + config.playerCommand + " (player)");
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            BackpacksMod.LOGGER.error("Error saving configuration", e);
        }
    }

    public static void reloadConfig() {
        BackpacksMod.LOGGER.info("Reloading configuration...");
        loadConfig();
        BackpacksMod.LOGGER.info("Configuration reloaded and validated");
        BackpacksMod.LOGGER.info("Commands: /" + config.mainCommand + " (admin), /" + config.playerCommand + " (player)");
    }

    public static BackpackConfig getConfig() {
        return config;
    }

    // === DELEGACIÓN A LANGUAGEMANAGER ===

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

    // === MÉTODOS DE CONFIGURACIÓN ===

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

    public static String getConfigSummary() {
        return config.getFullConfigSummary();
    }

    public static boolean isConfigurable(String setting) {
        return switch (setting.toLowerCase()) {
            case "serverid", "maxbackpacksperplayer", "allowbackpackrename",
                 "showbackpackstats", "mongoconnectionstring", "databasename",
                 "mongoconnectiontimeoutms", "backupintervalminutes", "maxbackupfiles",
                 "backupdirectory", "adminpermissionlevel",
                 "maincommand", "playercommand" -> true;
            default -> false;
        };
    }

    public static String getConfigurableSettings() {
        return "§6=== Modifiable Configurations ===\n" +
                "§eGeneral:\n" +
                "  §7• serverId - Server ID\n" +
                "  §7• maxBackpacksPerPlayer - Backpack limit per player (1-100)\n" +
                "  §7• allowBackpackRename - Allow backpack renaming\n" +
                "  §7• showBackpackStats - Show statistics\n" +
                "\n" +
                "§eCommands:\n" +
                "  §7• mainCommand - Main admin command (default: backpack)\n" +
                "  §7• playerCommand - Player command (default: backpacks)\n" +
                "  §7  Commands use letters, numbers, - and _ only (max 20 chars)\n" +
                "  §7  ⚠ Server restart required for command changes\n" +
                "\n" +
                "§eMongoDB:\n" +
                "  §7• mongoConnectionString - Connection string\n" +
                "  §7• databaseName - Database name\n" +
                "  §7• mongoConnectionTimeoutMs - Connection timeout (5000-30000)\n" +
                "\n" +
                "§eBackup:\n" +
                "  §7• backupIntervalMinutes - Backup interval (5-60)\n" +
                "  §7• maxBackupFiles - Maximum backup files (10-200)\n" +
                "  §7• backupDirectory - Backup directory\n" +
                "\n" +
                "§ePermissions:\n" +
                "  §7• adminPermissionLevel - Admin permission level (1-4)\n" +
                "\n" +
                "§eLanguage:\n" +
                "  §7• All messages are now in config/backpacks/lang.json\n" +
                "  §7• Support for hex colors: <#ff5555>text</>\n" +
                "  §7• Gradients: <gradient:#ff0000:#00ff00>text</gradient>\n" +
                "  §7• Current: " + LanguageManager.getLanguageInfo() + "\n" +
                "  §7• Total messages: " + LanguageManager.getTotalMessages() + "\n" +
                "\n" +
                "§c=== FIXED Configurations (Non-Modifiable) ===\n" +
                "§7The following are optimized and cannot be changed:\n" +
                "§7• autoSaveOnClose (always true - critical)\n" +
                "§7• asyncOperations (always true - performance)\n" +
                "§7• enableMongoDB (always true - core functionality)\n" +
                "§7• enableBackupSystem (always true - security)\n" +
                "§7• validateDataIntegrity (always true - security)\n" +
                "§7• cacheTimeoutSeconds (optimized - 60s)\n" +
                "§7• backpackSlots (standard - 27 slots)\n" +
                "§7• And many more optimized for performance and stability";
    }

    public static String getCommandInfo() {
        return config.getCommandInfo();
    }

    public static boolean validateConfiguration() {
        try {
            config.validateUserConfig();

            if (config.mongoConnectionString == null ||
                    !config.mongoConnectionString.startsWith("mongodb://")) {
                BackpacksMod.LOGGER.warn("Invalid MongoDB connection string");
                return false;
            }

            if (!Files.exists(Paths.get(config.backupDirectory))) {
                try {
                    Files.createDirectories(Paths.get(config.backupDirectory));
                } catch (IOException e) {
                    BackpacksMod.LOGGER.warn("Could not create backup directory: " + config.backupDirectory);
                    return false;
                }
            }

            if (!config.mainCommand.matches("[a-zA-Z0-9_-]+") || config.mainCommand.length() > 20) {
                BackpacksMod.LOGGER.warn("Invalid main command: " + config.mainCommand);
                return false;
            }

            if (!config.playerCommand.matches("[a-zA-Z0-9_-]+") || config.playerCommand.length() > 20) {
                BackpacksMod.LOGGER.warn("Invalid player command: " + config.playerCommand);
                return false;
            }

            if (config.mainCommand.equals(config.playerCommand)) {
                BackpacksMod.LOGGER.warn("Main and player commands cannot be the same");
                return false;
            }

            BackpacksMod.LOGGER.info("Configuration validated successfully");
            BackpacksMod.LOGGER.info("Commands: /" + config.mainCommand + " (admin), /" + config.playerCommand + " (player)");
            return true;

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error validating configuration", e);
            return false;
        }
    }

    public static String getMessageWithCommands(String key, Object... args) {
        String message = getMessage(key, args);
        message = message.replace("%main_command%", config.mainCommand);
        message = message.replace("%player_command%", config.playerCommand);
        return message;
    }

    public static String getMainCommand() {
        return config.mainCommand;
    }

    public static String getPlayerCommand() {
        return config.playerCommand;
    }

    public static boolean commandsChanged(String oldMain, String oldPlayer) {
        return !config.mainCommand.equals(oldMain) || !config.playerCommand.equals(oldPlayer);
    }
}