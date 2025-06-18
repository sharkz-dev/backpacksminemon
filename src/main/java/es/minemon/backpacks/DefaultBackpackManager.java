package es.minemon.backpacks;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Gestor de mochilas por defecto para nuevos jugadores
 * Proporciona mochilas automáticas al unirse al servidor
 */
public class DefaultBackpackManager {

    /**
     * Da mochilas por defecto a un jugador si está habilitado en la configuración
     */
    public static void giveDefaultBackpacks(ServerPlayerEntity player) {
        BackpackConfig config = ConfigManager.getConfig();

        if (!config.giveDefaultBackpacks || config.defaultBackpackCount <= 0) {
            return; // Sistema deshabilitado
        }

        UUID playerId = player.getUuid();

        try {
            // Verificar si el jugador ya tiene mochilas (para evitar dar duplicadas)
            MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(playerId);

            // Si ya tiene mochilas, no dar más por defecto
            if (!playerBackpacks.getAllBackpacks().isEmpty()) {
                BackpacksMod.LOGGER.debug("Player " + player.getName().getString() + " already has backpacks, skipping default backpacks");
                return;
            }

            // Verificar límite total
            int totalNeeded = config.defaultBackpackCount;
            if (totalNeeded > config.maxBackpacksPerPlayer) {
                BackpacksMod.LOGGER.warn("Default backpack count (" + totalNeeded + ") exceeds max limit (" +
                        config.maxBackpacksPerPlayer + ") for player " + player.getName().getString());
                totalNeeded = config.maxBackpacksPerPlayer;
            }

            // Crear mochilas por defecto
            for (int i = 1; i <= totalNeeded; i++) {
                try {
                    createDefaultBackpack(playerId, i, config);
                } catch (Exception e) {
                    BackpacksMod.LOGGER.error("Failed to create default backpack " + i + " for player " +
                            player.getName().getString() + ": " + e.getMessage());
                    break; // Parar si hay error en una mochila
                }
            }

            // Informar al jugador
            if (totalNeeded > 0) {
                LanguageManager.sendMessage(player, "defaultBackpacksGiven", totalNeeded);
                BackpacksMod.LOGGER.info("Gave " + totalNeeded + " default backpacks to new player: " +
                        player.getName().getString());
            }

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error giving default backpacks to player " + player.getName().getString(), e);
        }
    }

    /**
     * Crea una mochila por defecto individual
     */
    private static void createDefaultBackpack(UUID playerId, int number, BackpackConfig config) {
        // Generar nombre usando el patrón configurado
        String backpackName = String.format(config.defaultBackpackNamePattern, number);

        // Usar ID secuencial empezando desde 0
        int backpackId = number - 1;

        // Verificar que el ID no esté en uso (por seguridad)
        int attempts = 0;
        while (BackpackManager.getBackpack(playerId, backpackId) != null && attempts < 100) {
            backpackId++;
            attempts++;
        }

        if (attempts >= 100) {
            throw new RuntimeException("Could not find available ID for default backpack");
        }

        // Crear la mochila
        BackpackManager.addBackpack(playerId, backpackId, backpackName, config.defaultBackpackSlots);

        // Establecer icono por defecto
        MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(playerId, backpackId);
        if (backpack != null) {
            ItemStack defaultIcon = parseIconFromString(config.defaultBackpackIcon);
            backpack.setIcon(defaultIcon);
            BackpackManager.markBackpackDirty(playerId);
        }

        BackpacksMod.LOGGER.debug("Created default backpack: " + backpackName + " (ID: " + backpackId +
                ", Slots: " + config.defaultBackpackSlots + ") for player: " + playerId);
    }

    /**
     * Convierte un string de item a ItemStack
     */
    private static ItemStack parseIconFromString(String itemString) {
        try {
            Identifier identifier = Identifier.tryParse(itemString);
            if (identifier != null && Registries.ITEM.containsId(identifier)) {
                return new ItemStack(Registries.ITEM.get(identifier));
            }
        } catch (Exception e) {
            BackpacksMod.LOGGER.warn("Could not parse default icon '" + itemString + "', using chest");
        }
        return new ItemStack(Items.CHEST);
    }

    /**
     * Verifica si la configuración de mochilas por defecto es válida
     */
    public static boolean isDefaultBackpackConfigValid() {
        BackpackConfig config = ConfigManager.getConfig();

        if (!config.giveDefaultBackpacks) {
            return true; // Si está deshabilitado, es válido
        }

        // Verificar que no exceda el límite máximo
        if (config.defaultBackpackCount > config.maxBackpacksPerPlayer) {
            return false;
        }

        // Verificar que deje espacio para mochilas VIP y normales
        int vipBackpacks = VipBackpackManager.getMaxPossibleVipBackpacks();
        int totalNeeded = config.defaultBackpackCount + vipBackpacks + 5; // +5 buffer para mochilas normales

        return totalNeeded <= config.maxBackpacksPerPlayer;
    }

    /**
     * Obtiene información de diagnóstico sobre las mochilas por defecto
     */
    public static String getDefaultBackpackDiagnostic() {
        BackpackConfig config = ConfigManager.getConfig();
        StringBuilder diagnostic = new StringBuilder();

        diagnostic.append("=== Default Backpack System ===\n");
        diagnostic.append("Enabled: ").append(config.giveDefaultBackpacks ? "Yes" : "No").append("\n");

        if (config.giveDefaultBackpacks) {
            diagnostic.append("Count: ").append(config.defaultBackpackCount).append("\n");
            diagnostic.append("Slots each: ").append(config.defaultBackpackSlots).append("\n");
            diagnostic.append("Name pattern: ").append(config.defaultBackpackNamePattern).append("\n");
            diagnostic.append("Default icon: ").append(config.defaultBackpackIcon).append("\n");
            diagnostic.append("Valid configuration: ").append(isDefaultBackpackConfigValid() ? "Yes" : "No").append("\n");

            if (!isDefaultBackpackConfigValid()) {
                diagnostic.append("Issues: Default + VIP backpacks may exceed player limit\n");
            }
        }

        return diagnostic.toString();
    }

    /**
     * Obtiene un resumen de la configuración para mostrar en comandos
     */
    public static String getConfigurationSummary() {
        BackpackConfig config = ConfigManager.getConfig();
        StringBuilder summary = new StringBuilder();

        summary.append("§6=== Default Backpack Configuration ===\n");
        summary.append("§eStatus: ").append(config.giveDefaultBackpacks ? "§aEnabled" : "§cDisabled").append("\n");

        if (config.giveDefaultBackpacks) {
            summary.append("§eSettings:\n");
            summary.append("  §7• Count per new player: §a").append(config.defaultBackpackCount).append("\n");
            summary.append("  §7• Slots per backpack: §a").append(config.defaultBackpackSlots).append("\n");
            summary.append("  §7• Name pattern: §f").append(config.defaultBackpackNamePattern).append("\n");
            summary.append("  §7• Default icon: §f").append(config.defaultBackpackIcon).append("\n");
            summary.append("  §7• Configuration valid: ").append(isDefaultBackpackConfigValid() ? "§a✓ Yes" : "§c✗ No").append("\n");

            // Mostrar ejemplos de nombres
            summary.append("\n§eExample names:\n");
            for (int i = 1; i <= Math.min(3, config.defaultBackpackCount); i++) {
                String exampleName = String.format(config.defaultBackpackNamePattern, i);
                summary.append("  §7• §f").append(exampleName).append("\n");
            }
            if (config.defaultBackpackCount > 3) {
                summary.append("  §7• ... and ").append(config.defaultBackpackCount - 3).append(" more\n");
            }

            // Mostrar impacto en límites
            int vipBackpacks = VipBackpackManager.getMaxPossibleVipBackpacks();
            int totalUsed = config.defaultBackpackCount + vipBackpacks;
            int remaining = config.maxBackpacksPerPlayer - totalUsed;

            summary.append("\n§eLimit Impact:\n");
            summary.append("  §7• Default backpacks: §a").append(config.defaultBackpackCount).append("\n");
            summary.append("  §7• Max VIP backpacks: §a").append(vipBackpacks).append("\n");
            summary.append("  §7• Total reserved: §a").append(totalUsed).append("/").append(config.maxBackpacksPerPlayer).append("\n");
            summary.append("  §7• Remaining for normal: §a").append(Math.max(0, remaining)).append("\n");

            if (remaining < 5) {
                summary.append("\n§c⚠ Warning: Very little space left for normal backpacks!\n");
            }
        } else {
            summary.append("\n§7Players will not receive any backpacks automatically.\n");
            summary.append("§7Admins must give backpacks manually using commands.\n");
        }

        return summary.toString();
    }

    /**
     * Valida y aplica una nueva configuración de mochilas por defecto
     */
    public static boolean validateAndApplyNewConfig(int count, int slots, String namePattern, String icon) {
        // Validar parámetros
        if (count < 0 || count > 50) {
            return false;
        }

        if (slots < 9 || slots > 54 || slots % 9 != 0) {
            return false;
        }

        if (namePattern == null || namePattern.trim().isEmpty() || !namePattern.contains("%d")) {
            return false;
        }

        if (icon == null || icon.trim().isEmpty()) {
            return false;
        }

        // Verificar que el icono sea válido
        try {
            Identifier identifier = Identifier.tryParse(icon);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        // Verificar compatibilidad con límites
        BackpackConfig config = ConfigManager.getConfig();
        int vipBackpacks = VipBackpackManager.getMaxPossibleVipBackpacks();
        int totalNeeded = count + vipBackpacks + 5; // +5 buffer

        if (totalNeeded > config.maxBackpacksPerPlayer) {
            return false;
        }

        // Aplicar nueva configuración
        config.defaultBackpackCount = count;
        config.defaultBackpackSlots = slots;
        config.defaultBackpackNamePattern = namePattern.trim();
        config.defaultBackpackIcon = icon.trim();

        return true;
    }

    /**
     * Resetea la configuración de mochilas por defecto a valores por defecto
     */
    public static void resetToDefaults() {
        BackpackConfig config = ConfigManager.getConfig();
        config.giveDefaultBackpacks = true;
        config.defaultBackpackCount = 3;
        config.defaultBackpackSlots = 27;
        config.defaultBackpackNamePattern = "My Backpack %d";
        config.defaultBackpackIcon = "minecraft:chest";
    }

    /**
     * Obtiene ejemplos de nombres basados en el patrón actual
     */
    public static String[] getNameExamples(int count) {
        BackpackConfig config = ConfigManager.getConfig();
        count = Math.min(count, 5); // Máximo 5 ejemplos
        String[] examples = new String[count];

        for (int i = 0; i < count; i++) {
            examples[i] = String.format(config.defaultBackpackNamePattern, i + 1);
        }

        return examples;
    }

    /**
     * Estima el impacto de una configuración en los límites del servidor
     */
    public static String estimateConfigurationImpact(int defaultCount, int vipEnabled) {
        BackpackConfig config = ConfigManager.getConfig();
        int maxLimit = config.maxBackpacksPerPlayer;
        int vipBackpacks = VipBackpackManager.getMaxPossibleVipBackpacks();

        if (vipEnabled == 0) {
            vipBackpacks = 0; // Si VIP está deshabilitado
        }

        int totalReserved = defaultCount + vipBackpacks;
        int remainingForNormal = maxLimit - totalReserved;

        StringBuilder impact = new StringBuilder();
        impact.append("Configuration Impact Analysis:\n");
        impact.append("- Default backpacks per player: ").append(defaultCount).append("\n");
        impact.append("- Max VIP backpacks per player: ").append(vipBackpacks).append("\n");
        impact.append("- Total reserved slots: ").append(totalReserved).append("/").append(maxLimit).append("\n");
        impact.append("- Remaining for manual/normal: ").append(Math.max(0, remainingForNormal)).append("\n");

        if (remainingForNormal < 0) {
            impact.append("⚠ WARNING: Configuration exceeds player limit!\n");
        } else if (remainingForNormal < 5) {
            impact.append("⚠ CAUTION: Very little space for additional backpacks\n");
        } else {
            impact.append("✓ Configuration looks good\n");
        }

        return impact.toString();
    }
}