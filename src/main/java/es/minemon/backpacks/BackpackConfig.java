// SIMPLIFICADO: BackpackConfig.java - SIN sistema de backups
package es.minemon.backpacks;

import java.util.Map;

/**
 * Configuración simplificada SIN sistema de backups
 * Solo MongoDB para persistencia de datos
 */
public class BackpackConfig {

    // === CONFIGURACIÓN BÁSICA (USUARIO) ===
    public String serverId = "default-server";
    public int maxBackpacksPerPlayer = 75;
    public boolean allowBackpackRename = true;
    public boolean showBackpackStats = true;

    // === CONFIGURACIÓN DE COMANDOS (USUARIO) ===
    public String mainCommand = "backpack";
    public String playerCommand = "backpacks";

    // === CONFIGURACIÓN MONGODB (USUARIO) ===
    public String mongoConnectionString = "mongodb://localhost:27017";
    public String databaseName = "minecraft_backpacks";
    public int mongoConnectionTimeoutMs = 10000;

    // === CONFIGURACIÓN DE PERMISOS (USUARIO) ===
    public int adminPermissionLevel = 2;

    // === CONFIGURACIÓN DE MOCHILAS POR DEFECTO ===
    public boolean giveDefaultBackpacks = true;
    public int defaultBackpackCount = 3;
    public String defaultBackpackNamePattern = "My Backpack %d";
    public int defaultBackpackSlots = 27;
    public String defaultBackpackIcon = "minecraft:chest";

    // === CONFIGURACIÓN VIP CUSTOMIZABLE ===
    public VipRankConfig cristalConfig = new VipRankConfig(
            "Cristal Storage", "%s %02d", 3, 27, "minecraft:light_blue_stained_glass",
            "#b3e5fc", "#81d4fa", true
    );

    public VipRankConfig rubyConfig = new VipRankConfig(
            "Ruby Vault", "%s %02d", 5, 27, "minecraft:red_stained_glass",
            "#ffcdd2", "#ef9a9a", true
    );

    public VipRankConfig esmeraldaConfig = new VipRankConfig(
            "Esmeralda Treasury", "%s %02d", 7, 27, "minecraft:green_stained_glass",
            "#c8e6c9", "#a5d6a7", true
    );

    public VipRankConfig perlaConfig = new VipRankConfig(
            "Perla Collection", "%s %02d", 10, 27, "minecraft:purple_stained_glass",
            "#f3e5f5", "#ce93d8", true
    );

    public VipRankConfig platinoConfig = new VipRankConfig(
            "Platino Reserve", "%s %02d", 15, 27, "minecraft:light_gray_stained_glass",
            "#eceff1", "#b0bec5", true
    );

    // === CONFIGURACIONES FIJAS (NO MODIFICABLES) ===
    public final boolean enableMongoDB = true;
    public final String collectionName = "player_backpacks";
    public final boolean mongoAutoReconnect = true;

    // ELIMINADO: Todo lo relacionado con backups
    // public final boolean enableBackupSystem = false;
    // public final boolean createEmergencyBackup = false;
    // etc...

    public final int backpackSlots = 27;
    public final boolean autoSaveOnClose = true;
    public final boolean allowCustomIcons = true;
    public final boolean allowCustomSlots = true;

    public final int cacheMaxSize = 500;
    public final int autoSaveIntervalSeconds = 30;
    public final boolean asyncOperations = true;
    public final int maxConcurrentWrites = 4;
    public final int cacheTimeoutSeconds = 60;
    public final boolean forceReloadOnJoin = true;
    public final boolean validateDataIntegrity = true;
    public final int syncRetryAttempts = 3;
    public final boolean logSyncOperations = false;

    public final String menuTitle = "My Backpacks";
    public final boolean showItemCount = true;
    public final boolean showUsagePercentage = true;
    public final boolean colorCodeBackpacks = false;
    public final boolean playOpenSound = false;
    public final boolean playersCanViewOwnStats = true;
    public final boolean adminsCanViewAllStats = true;

    public static class VipRankConfig {
        public String displayName;
        public String namePattern;
        public int backpackCount;
        public int slotsPerBackpack;
        public String defaultIcon;
        public String primaryColor;
        public String secondaryColor;
        public boolean enabled;

        public VipRankConfig() {
            this.displayName = "VIP";
            this.namePattern = "%s %02d";
            this.backpackCount = 5;
            this.slotsPerBackpack = 27;
            this.defaultIcon = "minecraft:chest";
            this.primaryColor = "#ffd700";
            this.secondaryColor = "#ffaa00";
            this.enabled = true;
        }

        public VipRankConfig(String displayName, String namePattern, int backpackCount,
                             int slotsPerBackpack, String defaultIcon, String primaryColor,
                             String secondaryColor, boolean enabled) {
            this.displayName = displayName;
            this.namePattern = namePattern;
            this.backpackCount = backpackCount;
            this.slotsPerBackpack = slotsPerBackpack;
            this.defaultIcon = defaultIcon;
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
            this.enabled = enabled;
        }

        public String generateBackpackName(int number) {
            return String.format(namePattern, displayName, number);
        }
    }

    public void validateUserConfig() {
        if (maxBackpacksPerPlayer < 10 || maxBackpacksPerPlayer > 150) {
            maxBackpacksPerPlayer = 75;
        }

        // ELIMINADO: Validaciones de backup
        // if (backupIntervalMinutes < 5 || backupIntervalMinutes > 60) ...

        if (mongoConnectionTimeoutMs < 5000 || mongoConnectionTimeoutMs > 30000) {
            mongoConnectionTimeoutMs = 10000;
        }

        if (adminPermissionLevel < 1 || adminPermissionLevel > 4) {
            adminPermissionLevel = 2;
        }

        if (serverId == null || serverId.trim().isEmpty()) {
            serverId = "default-server";
        }

        if (mongoConnectionString == null || mongoConnectionString.trim().isEmpty()) {
            mongoConnectionString = "mongodb://localhost:27017";
        }

        if (databaseName == null || databaseName.trim().isEmpty()) {
            databaseName = "minecraft_backpacks";
        }

        // ELIMINADO: Validación de directorios de backup

        if (mainCommand == null || mainCommand.trim().isEmpty() || !isValidCommand(mainCommand)) {
            mainCommand = "backpack";
        }

        if (playerCommand == null || playerCommand.trim().isEmpty() || !isValidCommand(playerCommand)) {
            playerCommand = "backpacks";
        }

        if (mainCommand.equals(playerCommand)) {
            playerCommand = mainCommand + "s";
        }

        validateDefaultBackpackConfig();
        validateVipConfigs();
        validateVipCompatibility();
    }

    private void validateDefaultBackpackConfig() {
        if (defaultBackpackCount < 0 || defaultBackpackCount > 10) {
            defaultBackpackCount = 3;
        }

        if (defaultBackpackSlots < 9 || defaultBackpackSlots > 54 || defaultBackpackSlots % 9 != 0) {
            defaultBackpackSlots = 27;
        }

        if (defaultBackpackNamePattern == null || defaultBackpackNamePattern.trim().isEmpty()) {
            defaultBackpackNamePattern = "My Backpack %d";
        }

        if (defaultBackpackIcon == null || defaultBackpackIcon.trim().isEmpty()) {
            defaultBackpackIcon = "minecraft:chest";
        }
    }

    private void validateVipConfigs() {
        validateVipRankConfig(cristalConfig, "Cristal Storage", 3);
        validateVipRankConfig(rubyConfig, "Ruby Vault", 5);
        validateVipRankConfig(esmeraldaConfig, "Esmeralda Treasury", 7);
        validateVipRankConfig(perlaConfig, "Perla Collection", 10);
        validateVipRankConfig(platinoConfig, "Platino Reserve", 15);
    }

    private void validateVipRankConfig(VipRankConfig config, String defaultName, int defaultCount) {
        if (config.displayName == null || config.displayName.trim().isEmpty()) {
            config.displayName = defaultName;
        }

        if (config.namePattern == null || config.namePattern.trim().isEmpty()) {
            config.namePattern = "%s %02d";
        }

        if (config.backpackCount < 0 || config.backpackCount > 50) {
            config.backpackCount = defaultCount;
        }

        if (config.slotsPerBackpack < 9 || config.slotsPerBackpack > 54 || config.slotsPerBackpack % 9 != 0) {
            config.slotsPerBackpack = 27;
        }

        if (config.defaultIcon == null || config.defaultIcon.trim().isEmpty()) {
            config.defaultIcon = "minecraft:chest";
        }

        if (config.primaryColor == null || config.primaryColor.trim().isEmpty()) {
            config.primaryColor = "#ffd700";
        }

        if (config.secondaryColor == null || config.secondaryColor.trim().isEmpty()) {
            config.secondaryColor = "#ffaa00";
        }
    }

    private void validateVipCompatibility() {
        int maxVipBackpacks = getMaxPossibleVipBackpacks();
        int minRequiredLimit = maxVipBackpacks + Math.max(15, defaultBackpackCount + 5);

        if (maxBackpacksPerPlayer < minRequiredLimit) {
            BackpacksMod.LOGGER.warn("Current backpack limit (" + maxBackpacksPerPlayer +
                    ") may not be sufficient for VIP system + default backpacks (requires at least " + minRequiredLimit + ")");

            if (maxBackpacksPerPlayer < 60) {
                maxBackpacksPerPlayer = Math.max(75, minRequiredLimit);
                BackpacksMod.LOGGER.info("Auto-adjusted maxBackpacksPerPlayer to " + maxBackpacksPerPlayer + " for VIP compatibility");
            }
        }
    }

    public int getMaxPossibleVipBackpacks() {
        int total = 0;
        if (cristalConfig.enabled) total += cristalConfig.backpackCount;
        if (rubyConfig.enabled) total += rubyConfig.backpackCount;
        if (esmeraldaConfig.enabled) total += esmeraldaConfig.backpackCount;
        if (perlaConfig.enabled) total += perlaConfig.backpackCount;
        if (platinoConfig.enabled) total += platinoConfig.backpackCount;
        return total;
    }

    private boolean isValidCommand(String command) {
        return command.matches("[a-zA-Z0-9_-]+") && command.length() <= 20;
    }

    // SIMPLIFICADO: Summary sin información de backups
    public String getFullConfigSummary() {
        validateUserConfig();

        StringBuilder summary = new StringBuilder();
        summary.append("§6=== BackpacksMod Configuration (NO BACKUPS) ===\n");
        summary.append("§eUser Configuration:\n");
        summary.append("  §7Server ID: §a").append(serverId).append("\n");
        summary.append("  §7Max backpacks/player: §a").append(maxBackpacksPerPlayer).append("\n");

        summary.append("  §7Default backpacks: §a").append(giveDefaultBackpacks ? "Enabled" : "Disabled").append("\n");
        if (giveDefaultBackpacks) {
            summary.append("  §7Default count: §a").append(defaultBackpackCount).append("\n");
            summary.append("  §7Default slots: §a").append(defaultBackpackSlots).append("\n");
        }

        int maxVipBackpacks = getMaxPossibleVipBackpacks();
        boolean vipCompatible = isVipConfigurationValid();
        summary.append("  §7VIP System: §a").append(vipCompatible ? "Compatible" : "⚠ May have issues").append("\n");
        summary.append("  §7Max VIP backpacks: §a").append(maxVipBackpacks).append("\n");

        summary.append("  §7Rename enabled: §a").append(allowBackpackRename).append("\n");
        summary.append("  §7Stats enabled: §a").append(showBackpackStats).append("\n");
        summary.append("  §7Main command: §a/").append(mainCommand).append("\n");
        summary.append("  §7Player command: §a/").append(playerCommand).append("\n");
        summary.append("  §7MongoDB: §a").append(mongoConnectionString).append("\n");
        summary.append("  §7Database: §a").append(databaseName).append("\n");

        // ELIMINADO: Información de backup
        summary.append("  §7Backup system: §cDISABLED for performance\n");
        summary.append("  §7Data persistence: §aMongoDB only\n");

        summary.append("  §7Admin permission level: §a").append(adminPermissionLevel).append("\n");

        summary.append("\n§eLanguage System:\n");
        summary.append("  §7Language file: §aconfig/backpacks/lang.json\n");
        summary.append("  §7Current language: §a").append(LanguageManager.getLanguageInfo()).append("\n");
        summary.append("  §7Total messages: §a").append(LanguageManager.getTotalMessages()).append("\n");

        summary.append("\n§eVIP System:\n");
        int enabledRanks = 0;
        if (cristalConfig.enabled) enabledRanks++;
        if (rubyConfig.enabled) enabledRanks++;
        if (esmeraldaConfig.enabled) enabledRanks++;
        if (perlaConfig.enabled) enabledRanks++;
        if (platinoConfig.enabled) enabledRanks++;

        summary.append("  §7VIP ranks enabled: §a").append(enabledRanks).append("/5\n");
        summary.append("  §7Total VIP backpacks: §a").append(maxVipBackpacks).append("\n");
        summary.append("  §7Limit compatibility: §a").append(vipCompatible ? "✓ Good" : "⚠ Check limits").append("\n");
        summary.append("  §7Normal backpack space: §a").append(Math.max(0, maxBackpacksPerPlayer - maxVipBackpacks)).append(" slots\n");

        summary.append("\n§eFixed Configuration (Performance Optimized):\n");
        summary.append("  §7Auto-save on close: §aEnabled (Critical)\n");
        summary.append("  §7Async operations: §aEnabled (Performance)\n");
        summary.append("  §7Cache timeout: §a").append(cacheTimeoutSeconds).append("s (Optimized)\n");
        summary.append("  §7Default slots: §a").append(backpackSlots).append(" (Standard)\n");
        summary.append("  §7Custom icons: §aEnabled (Functionality)\n");
        summary.append("  §7Backup system: §cDISABLED (Maximum Performance)\n");
        summary.append("  §7Emergency backup: §cDISABLED\n");
        summary.append("  §7Data integrity validation: §aEnabled (Security)\n");

        return summary.toString();
    }

    // SIMPLIFICADO: Command info sin backups
    public String getCommandInfo() {
        return String.format("§6=== Command Configuration (No Backups) ===\n" +
                        "§eMain command (admin): §a/%s\n" +
                        "§7- /%s give <player> <name> <slots>\n" +
                        "§7- /%s remove <player> <id>\n" +
                        "§7- /%s admin view <player>\n" +
                        "§7- /%s rename <player> <id> <name>\n" +
                        "§7- /%s info <player>\n" +
                        "§7- /%s sync <player>\n" +
                        "§7- /%s-vip (VIP management)\n" +
                        "\n§ePlayer command: §a/%s\n" +
                        "§7- Opens backpack menu\n" +
                        "\n§eOther commands:\n" +
                        "§7- /rename-backpack <id> <name>\n" +
                        "§7- /%s-config (admin configuration)\n" +
                        "§7- /%s-perms (permission management)\n" +
                        "\n§eVIP System:\n" +
                        "§7- Max VIP backpacks: §a%d\n" +
                        "§7- Current limit allows: §a%d normal + %d VIP\n" +
                        "§7- VIP compatible: §a%s\n" +
                        "\n§eDefault Backpacks:\n" +
                        "§7- Auto-give on join: §a%s\n" +
                        "§7- Default count: §a%d\n" +
                        "§7- Default slots: §a%d\n" +
                        "\n§eData Storage:\n" +
                        "§7- Primary storage: §aMongoDB\n" +
                        "§7- Backup system: §cDISABLED\n" +
                        "§7- Emergency backup: §cDISABLED\n" +
                        "§7- Performance impact: §aMINIMAL\n" +
                        "\n§eLanguage:\n" +
                        "§7- All messages in: §aconfig/backpacks/lang.json\n" +
                        "§7- Current: §a%s\n" +
                        "§7- Use /%s-config reload to refresh",
                mainCommand, mainCommand, mainCommand, mainCommand,
                mainCommand, mainCommand, mainCommand, mainCommand, playerCommand, mainCommand, mainCommand,
                getMaxPossibleVipBackpacks(),
                Math.max(0, maxBackpacksPerPlayer - getMaxPossibleVipBackpacks()),
                getMaxPossibleVipBackpacks(),
                isVipConfigurationValid() ? "Yes" : "No",
                giveDefaultBackpacks ? "Yes" : "No",
                defaultBackpackCount,
                defaultBackpackSlots,
                LanguageManager.getLanguageInfo(), mainCommand);
    }

    public String getVipConfigInfo() {
        int maxVipBackpacks = getMaxPossibleVipBackpacks();
        boolean isCompatible = isVipConfigurationValid();
        int remainingSlots = Math.max(0, maxBackpacksPerPlayer - maxVipBackpacks);

        StringBuilder info = new StringBuilder();
        info.append("§6=== VIP Configuration Status (No Backups) ===\n");
        info.append("§eBackpack Limits:\n");
        info.append("  §7• Total limit per player: §a").append(maxBackpacksPerPlayer).append("\n");
        info.append("  §7• Max possible VIP backpacks: §a").append(maxVipBackpacks).append("\n");
        info.append("  §7• Remaining for normal backpacks: §a").append(remainingSlots).append("\n");
        info.append("  §7• Configuration status: ").append(isCompatible ? "§a✓ Compatible" : "§c⚠ Issues detected").append("\n\n");

        info.append("§eVIP Rank Breakdown:\n");
        if (cristalConfig.enabled) {
            info.append("  §7• ").append(cristalConfig.displayName).append(": §a").append(cristalConfig.backpackCount).append(" backpacks (").append(cristalConfig.slotsPerBackpack).append(" slots each)\n");
        }
        if (rubyConfig.enabled) {
            info.append("  §7• ").append(rubyConfig.displayName).append(": §a").append(rubyConfig.backpackCount).append(" backpacks (").append(rubyConfig.slotsPerBackpack).append(" slots each)\n");
        }
        if (esmeraldaConfig.enabled) {
            info.append("  §7• ").append(esmeraldaConfig.displayName).append(": §a").append(esmeraldaConfig.backpackCount).append(" backpacks (").append(esmeraldaConfig.slotsPerBackpack).append(" slots each)\n");
        }
        if (perlaConfig.enabled) {
            info.append("  §7• ").append(perlaConfig.displayName).append(": §a").append(perlaConfig.backpackCount).append(" backpacks (").append(perlaConfig.slotsPerBackpack).append(" slots each)\n");
        }
        if (platinoConfig.enabled) {
            info.append("  §7• ").append(platinoConfig.displayName).append(": §a").append(platinoConfig.backpackCount).append(" backpacks (").append(platinoConfig.slotsPerBackpack).append(" slots each)\n");
        }

        if (!isCompatible) {
            info.append("\n§cRecommendations:\n");
            info.append("  §7• Increase maxBackpacksPerPlayer to at least §a").append(maxVipBackpacks + 15).append("\n");
            info.append("  §7• Or reduce VIP backpack counts in configuration\n");
            info.append("  §7• Current setting may cause VIP creation errors\n");
        } else {
            info.append("\n§aConfiguration is optimal for VIP system!\n");
        }

        info.append("\n§ePerformance Notes:\n");
        info.append("  §7• Backup system disabled for maximum performance\n");
        info.append("  §7• All data persisted to MongoDB only\n");
        info.append("  §7• Reduced memory and CPU overhead\n");

        return info.toString();
    }

    public boolean isVipConfigurationValid() {
        int maxVipBackpacks = getMaxPossibleVipBackpacks();
        int requiredSpace = maxVipBackpacks + Math.max(10, defaultBackpackCount);
        return maxBackpacksPerPlayer >= requiredSpace;
    }
}