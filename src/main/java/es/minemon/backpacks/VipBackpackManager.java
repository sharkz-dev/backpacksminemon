package es.minemon.backpacks;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

import java.util.*;

/**
 * Gestor de mochilas VIP basadas en permisos - COMPLETAMENTE CUSTOMIZABLE
 * NUEVO: Configuración completa desde config, iconos customizables, nombres personalizados
 */
public class VipBackpackManager {

    // Mapeo de permisos a configuraciones VIP
    public static final Map<String, String> VIP_PERMISSION_TO_CONFIG = Map.of(
            "backpack.cristal", "cristal",
            "backpack.ruby", "ruby",
            "backpack.esmeralda", "esmeralda",
            "backpack.perla", "perla",
            "backpack.platino", "platino"
    );

    public static class VipRank {
        private final String id;
        private final String displayName;
        private final int backpackCount;
        private final int slotsPerBackpack;
        private final String primaryColor;
        private final String secondaryColor;
        private final ItemStack defaultIcon;
        private final String namePattern;
        private final boolean enabled;

        public VipRank(String id, BackpackConfig.VipRankConfig config) {
            this.id = id;
            this.displayName = config.displayName;
            this.backpackCount = config.backpackCount;
            this.slotsPerBackpack = config.slotsPerBackpack;
            this.primaryColor = config.primaryColor;
            this.secondaryColor = config.secondaryColor;
            this.namePattern = config.namePattern;
            this.enabled = config.enabled;
            this.defaultIcon = parseItemFromString(config.defaultIcon);
        }

        private ItemStack parseItemFromString(String itemString) {
            try {
                Identifier identifier = Identifier.tryParse(itemString);
                if (identifier != null && Registries.ITEM.containsId(identifier)) {
                    return new ItemStack(Registries.ITEM.get(identifier));
                }
            } catch (Exception e) {
                BackpacksMod.LOGGER.warn("Could not parse item '" + itemString + "', using default chest");
            }
            return new ItemStack(Items.CHEST);
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public int getBackpackCount() { return backpackCount; }
        public int getSlotsPerBackpack() { return slotsPerBackpack; }
        public String getPrimaryColor() { return primaryColor; }
        public String getSecondaryColor() { return secondaryColor; }
        public ItemStack getDefaultIcon() { return defaultIcon.copy(); }
        public String getNamePattern() { return namePattern; }
        public boolean isEnabled() { return enabled; }

        public String generateBackpackName(int number) {
            return String.format(namePattern, displayName, number);
        }
    }

    /**
     * Obtiene los rangos VIP actuales desde la configuración
     */
    public static Map<String, VipRank> getCurrentVipRanks() {
        BackpackConfig config = ConfigManager.getConfig();
        Map<String, VipRank> ranks = new LinkedHashMap<>();

        if (config.cristalConfig.enabled) {
            ranks.put("backpack.cristal", new VipRank("cristal", config.cristalConfig));
        }
        if (config.rubyConfig.enabled) {
            ranks.put("backpack.ruby", new VipRank("ruby", config.rubyConfig));
        }
        if (config.esmeraldaConfig.enabled) {
            ranks.put("backpack.esmeralda", new VipRank("esmeralda", config.esmeraldaConfig));
        }
        if (config.perlaConfig.enabled) {
            ranks.put("backpack.perla", new VipRank("perla", config.perlaConfig));
        }
        if (config.platinoConfig.enabled) {
            ranks.put("backpack.platino", new VipRank("platino", config.platinoConfig));
        }

        return ranks;
    }

    /**
     * Actualiza las mochilas VIP de un jugador basándose en sus permisos actuales
     * CORREGIDO: Verifica límites antes de crear mochilas y usa configuración customizable
     */
    public static void updatePlayerVipBackpacks(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        try {
            // Verificar límite máximo antes de proceder
            MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(playerId);
            int currentBackpackCount = playerBackpacks.getAllBackpacks().size();
            int maxBackpacksAllowed = ConfigManager.getConfig().maxBackpacksPerPlayer;

            // Obtener rangos VIP actuales desde configuración
            Map<String, VipRank> currentRanks = getCurrentVipRanks();

            // Obtener permisos VIP actuales del jugador
            Set<String> currentVipPermissions = getCurrentVipPermissions(player, currentRanks.keySet());

            // Calcular cuántas mochilas VIP necesitaríamos crear
            int requiredVipBackpacks = calculateRequiredVipBackpacks(currentVipPermissions, currentRanks);
            int existingVipBackpackCount = countExistingVipBackpacks(playerId);
            int newVipBackpacksNeeded = Math.max(0, requiredVipBackpacks - existingVipBackpackCount);

            // Verificar si excederíamos el límite
            if (currentBackpackCount + newVipBackpacksNeeded > maxBackpacksAllowed) {
                BackpacksMod.LOGGER.warn("Player " + player.getName().getString() +
                        " would exceed maximum backpack limit (" + maxBackpacksAllowed + ") with VIP backpacks. " +
                        "Current: " + currentBackpackCount + ", VIP needed: " + newVipBackpacksNeeded);

                // Enviar mensaje al jugador explicando la situación
                LanguageManager.sendMessage(player, "vipBackpackLimitReached",
                        maxBackpacksAllowed, currentBackpackCount, newVipBackpacksNeeded);
                return;
            }

            // Procesar cada rango VIP (solo si tenemos espacio)
            for (Map.Entry<String, VipRank> entry : currentRanks.entrySet()) {
                String permission = entry.getKey();
                VipRank rank = entry.getValue();

                if (currentVipPermissions.contains(permission)) {
                    // El jugador tiene el permiso, asegurar que tenga las mochilas (respetando límites)
                    ensureVipBackpacks(playerId, rank, maxBackpacksAllowed);
                } else {
                    // El jugador no tiene el permiso, ocultar las mochilas
                    hideVipBackpacks(playerId, rank);
                }
            }

            BackpacksMod.LOGGER.info("Updated VIP backpacks for player: " + player.getName().getString());

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error updating VIP backpacks for player " + player.getName().getString(), e);
        }
    }

    /**
     * Calcula cuántas mochilas VIP necesita un jugador basado en sus permisos
     */
    private static int calculateRequiredVipBackpacks(Set<String> vipPermissions, Map<String, VipRank> ranks) {
        int total = 0;
        for (String permission : vipPermissions) {
            VipRank rank = ranks.get(permission);
            if (rank != null) {
                total += rank.getBackpackCount();
            }
        }
        return total;
    }

    /**
     * Cuenta las mochilas VIP existentes de un jugador
     */
    private static int countExistingVipBackpacks(UUID playerId) {
        MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(playerId);
        int count = 0;
        for (MongoBackpackManager.BackpackData backpack : playerBackpacks.getAllBackpacks().values()) {
            if (isVipBackpackName(backpack.getName())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Obtiene los permisos VIP actuales del jugador
     */
    private static Set<String> getCurrentVipPermissions(ServerPlayerEntity player, Set<String> availablePermissions) {
        Set<String> vipPermissions = new HashSet<>();

        for (String permission : availablePermissions) {
            if (LuckPermsManager.hasPermission(player, permission)) {
                vipPermissions.add(permission);
            }
        }

        return vipPermissions;
    }

    /**
     * Obtiene las mochilas VIP existentes del jugador
     */
    private static Set<String> getExistingVipBackpacks(UUID playerId) {
        Set<String> vipBackpacks = new HashSet<>();

        MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(playerId);
        Map<Integer, MongoBackpackManager.BackpackData> allBackpacks = playerBackpacks.getAllBackpacks();

        for (MongoBackpackManager.BackpackData backpack : allBackpacks.values()) {
            String name = backpack.getName();
            if (isVipBackpackName(name)) {
                vipBackpacks.add(name);
            }
        }

        return vipBackpacks;
    }

    /**
     * Verifica si un nombre de mochila es de tipo VIP (basado en configuración actual)
     */
    private static boolean isVipBackpackName(String name) {
        Map<String, VipRank> currentRanks = getCurrentVipRanks();
        for (VipRank rank : currentRanks.values()) {
            if (name.toLowerCase().startsWith(rank.getId().toLowerCase()) ||
                    name.toLowerCase().contains(rank.getDisplayName().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Asegura que el jugador tenga todas las mochilas VIP para un rango específico
     * CORREGIDO: Respeta el límite máximo de mochilas y usa configuración customizable
     */
    private static void ensureVipBackpacks(UUID playerId, VipRank rank, int maxBackpacksAllowed) {
        MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(playerId);

        for (int i = 1; i <= rank.getBackpackCount(); i++) {
            // Verificar límite antes de crear cada mochila
            if (playerBackpacks.getAllBackpacks().size() >= maxBackpacksAllowed) {
                BackpacksMod.LOGGER.warn("Reached maximum backpack limit for player " + playerId +
                        " while creating VIP " + rank.getDisplayName() + " backpacks");
                break;
            }

            String backpackName = rank.generateBackpackName(i);

            // Verificar si ya existe una mochila con este nombre
            boolean exists = playerBackpacks.getAllBackpacks().values().stream()
                    .anyMatch(backpack -> backpack.getName().equals(backpackName));

            if (!exists) {
                // Crear nueva mochila VIP solo si no excedemos el límite
                try {
                    createVipBackpack(playerId, backpackName, rank);
                } catch (Exception e) {
                    BackpacksMod.LOGGER.error("Failed to create VIP backpack " + backpackName +
                            " for player " + playerId + ": " + e.getMessage());
                    break; // Salir del bucle si hay error
                }
            } else {
                // Marcar mochila como visible (si estaba oculta) y actualizar icono si es necesario
                markVipBackpackVisible(playerId, backpackName, rank);
            }
        }
    }

    /**
     * Crea una nueva mochila VIP con verificación de límites y configuración customizable
     */
    private static void createVipBackpack(UUID playerId, String name, VipRank rank) {
        try {
            // Verificar límite una vez más antes de crear
            MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(playerId);
            if (playerBackpacks.getAllBackpacks().size() >= ConfigManager.getConfig().maxBackpacksPerPlayer) {
                throw new RuntimeException("Maximum backpack limit reached");
            }

            // Generar ID único para mochila VIP (usar hash del nombre para consistencia)
            int vipId = generateVipBackpackId(name);

            // Verificar que el ID no esté en uso
            int attempts = 0;
            while (BackpackManager.getBackpack(playerId, vipId) != null && attempts < 1000) {
                vipId++;
                attempts++;
            }

            if (attempts >= 1000) {
                throw new RuntimeException("Could not find available ID for VIP backpack");
            }

            // Crear la mochila con slots customizables
            BackpackManager.addBackpack(playerId, vipId, name, rank.getSlotsPerBackpack());

            // Establecer icono customizable
            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(playerId, vipId);
            if (backpack != null) {
                backpack.setIcon(rank.getDefaultIcon());
                BackpackManager.markBackpackDirty(playerId);
            }

            BackpacksMod.LOGGER.debug("Created VIP backpack: " + name + " (ID: " + vipId +
                    ", Slots: " + rank.getSlotsPerBackpack() + ") for player: " + playerId);

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error creating VIP backpack " + name + " for player " + playerId, e);
            throw e; // Re-lanzar la excepción para que el llamador la maneje
        }
    }

    /**
     * Genera un ID único para una mochila VIP basado en su nombre
     */
    private static int generateVipBackpackId(String name) {
        // Usar hash del nombre + offset para evitar conflictos con mochilas normales
        int hash = Math.abs(name.hashCode());
        return 100000 + (hash % 900000); // IDs VIP van de 100000 a 999999
    }

    /**
     * Marca una mochila VIP como visible y actualiza su icono si es necesario
     */
    private static void markVipBackpackVisible(UUID playerId, String backpackName, VipRank rank) {
        // Buscar la mochila y actualizar su icono si es necesario
        MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(playerId);
        for (Map.Entry<Integer, MongoBackpackManager.BackpackData> entry : playerBackpacks.getAllBackpacks().entrySet()) {
            MongoBackpackManager.BackpackData backpack = entry.getValue();
            if (backpack.getName().equals(backpackName)) {
                // Actualizar icono al icono VIP configurado
                ItemStack currentIcon = backpack.getIcon();
                ItemStack expectedIcon = rank.getDefaultIcon();

                if (!ItemStack.areItemsEqual(currentIcon, expectedIcon)) {
                    backpack.setIcon(expectedIcon);
                    BackpackManager.markBackpackDirty(playerId);
                    BackpacksMod.LOGGER.debug("Updated VIP backpack icon for: " + backpackName);
                }
                break;
            }
        }

        BackpacksMod.LOGGER.debug("VIP backpack " + backpackName + " is now visible for player " + playerId);
    }

    /**
     * Oculta las mochilas VIP de un rango específico (no las elimina, solo las oculta)
     */
    private static void hideVipBackpacks(UUID playerId, VipRank rank) {
        // En lugar de eliminar físicamente, marcamos como ocultas
        // Los items permanecen intactos
        BackpacksMod.LOGGER.debug("Hiding VIP backpacks for rank " + rank.getDisplayName() + " for player " + playerId);
        // Nota: La implementación física de "ocultar" se hace en el método de filtrado
    }

    /**
     * Filtra las mochilas que debe ver un jugador basándose en sus permisos actuales
     */
    public static Map<Integer, MongoBackpackManager.BackpackData> getVisibleBackpacks(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(playerId);
        Map<Integer, MongoBackpackManager.BackpackData> allBackpacks = playerBackpacks.getAllBackpacks();
        Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = new LinkedHashMap<>();

        // Obtener configuración VIP actual
        Map<String, VipRank> currentRanks = getCurrentVipRanks();

        // Obtener permisos VIP actuales
        Set<String> currentVipPermissions = getCurrentVipPermissions(player, currentRanks.keySet());
        Set<String> allowedVipRanks = new HashSet<>();
        for (String permission : currentVipPermissions) {
            VipRank rank = currentRanks.get(permission);
            if (rank != null) {
                allowedVipRanks.add(rank.getId());
            }
        }

        // Separar mochilas normales y VIP
        Map<Integer, MongoBackpackManager.BackpackData> normalBackpacks = new LinkedHashMap<>();
        Map<Integer, MongoBackpackManager.BackpackData> vipBackpacks = new LinkedHashMap<>();

        for (Map.Entry<Integer, MongoBackpackManager.BackpackData> entry : allBackpacks.entrySet()) {
            MongoBackpackManager.BackpackData backpack = entry.getValue();
            String name = backpack.getName();

            if (isVipBackpackName(name)) {
                // Es mochila VIP, verificar si el jugador tiene el permiso
                boolean hasPermission = false;
                for (String rankId : allowedVipRanks) {
                    if (name.toLowerCase().startsWith(rankId.toLowerCase()) ||
                            name.toLowerCase().contains(rankId.toLowerCase())) {
                        hasPermission = true;
                        break;
                    }
                }

                if (hasPermission) {
                    vipBackpacks.put(entry.getKey(), backpack);
                }
            } else {
                // Es mochila normal, siempre visible
                normalBackpacks.put(entry.getKey(), backpack);
            }
        }

        // Combinar: primero mochilas normales, luego VIP
        visibleBackpacks.putAll(normalBackpacks);
        visibleBackpacks.putAll(vipBackpacks);

        return visibleBackpacks;
    }

    /**
     * Verifica si un jugador puede acceder a una mochila específica
     */
    public static boolean canAccessBackpack(ServerPlayerEntity player, int backpackId) {
        MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(player.getUuid(), backpackId);
        if (backpack == null) {
            return false;
        }

        String name = backpack.getName();
        if (!isVipBackpackName(name)) {
            return true; // Mochila normal, siempre accesible
        }

        // Es mochila VIP, verificar permisos con configuración actual
        Map<String, VipRank> currentRanks = getCurrentVipRanks();
        Set<String> currentVipPermissions = getCurrentVipPermissions(player, currentRanks.keySet());

        for (String permission : currentVipPermissions) {
            VipRank rank = currentRanks.get(permission);
            if (rank != null && (name.toLowerCase().startsWith(rank.getId().toLowerCase()) ||
                    name.toLowerCase().contains(rank.getDisplayName().toLowerCase()))) {
                return true;
            }
        }

        return false; // No tiene permiso para esta mochila VIP
    }

    /**
     * Obtiene información de diagnóstico de mochilas VIP para un jugador
     */
    public static String getVipDiagnosticInfo(ServerPlayerEntity player) {
        StringBuilder info = new StringBuilder();
        info.append("=== VIP Backpacks Diagnostic ===\n");

        Map<String, VipRank> currentRanks = getCurrentVipRanks();
        Set<String> vipPermissions = getCurrentVipPermissions(player, currentRanks.keySet());
        info.append("Current VIP permissions: ").append(vipPermissions).append("\n");

        Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = getVisibleBackpacks(player);
        int vipCount = 0;
        int normalCount = 0;

        for (MongoBackpackManager.BackpackData backpack : visibleBackpacks.values()) {
            if (isVipBackpackName(backpack.getName())) {
                vipCount++;
            } else {
                normalCount++;
            }
        }

        info.append("Visible backpacks - Normal: ").append(normalCount).append(", VIP: ").append(vipCount).append("\n");

        // Información de límites
        MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(player.getUuid());
        int totalBackpacks = playerBackpacks.getAllBackpacks().size();
        int maxAllowed = ConfigManager.getConfig().maxBackpacksPerPlayer;
        info.append("Total backpacks: ").append(totalBackpacks).append("/").append(maxAllowed).append("\n");

        for (Map.Entry<String, VipRank> entry : currentRanks.entrySet()) {
            String permission = entry.getKey();
            VipRank rank = entry.getValue();
            boolean hasPermission = LuckPermsManager.hasPermission(player, permission);
            info.append(rank.getDisplayName()).append(" (").append(permission).append("): ");
            info.append(hasPermission ? "✓ ACTIVE" : "✗ INACTIVE").append(" - ");
            info.append(rank.getBackpackCount()).append(" backpacks (").append(rank.getSlotsPerBackpack()).append(" slots each)\n");
        }

        return info.toString();
    }

    /**
     * Sincroniza las mochilas VIP cuando cambian los permisos
     */
    public static void onPermissionChange(ServerPlayerEntity player) {
        // Actualizar mochilas VIP basándose en los nuevos permisos
        updatePlayerVipBackpacks(player);

        // Forzar guardado para persistir cambios
        BackpackManager.forcePlayerSave(player.getUuid());

        BackpacksMod.LOGGER.info("VIP backpacks synchronized for player: " + player.getName().getString());
    }

    /**
     * Método de utilidad para obtener el total máximo de mochilas VIP posibles
     */
    public static int getMaxPossibleVipBackpacks() {
        return ConfigManager.getConfig().getMaxPossibleVipBackpacks();
    }

    /**
     * Verifica si la configuración actual de VIP es compatible con el límite de mochilas
     */
    public static boolean isVipConfigurationValid() {
        return ConfigManager.getConfig().isVipConfigurationValid();
    }

    /**
     * NUEVO: Obtiene información detallada de las configuraciones VIP actuales
     */
    public static String getVipConfigurationSummary() {
        Map<String, VipRank> currentRanks = getCurrentVipRanks();
        StringBuilder summary = new StringBuilder();

        summary.append("§6=== VIP Configuration Summary ===\n");
        summary.append("§eEnabled VIP Ranks: §a").append(currentRanks.size()).append("/5\n\n");

        for (Map.Entry<String, VipRank> entry : currentRanks.entrySet()) {
            VipRank rank = entry.getValue();
            summary.append("§e").append(rank.getDisplayName()).append(":\n");
            summary.append("  §7Permission: §f").append(entry.getKey()).append("\n");
            summary.append("  §7Backpacks: §a").append(rank.getBackpackCount()).append("\n");
            summary.append("  §7Slots each: §a").append(rank.getSlotsPerBackpack()).append("\n");
            summary.append("  §7Name pattern: §f").append(rank.getNamePattern()).append("\n");
            summary.append("  §7Default icon: §f").append(rank.getDefaultIcon().getItem().toString()).append("\n");
            summary.append("  §7Colors: ").append(rank.getPrimaryColor()).append(" / ").append(rank.getSecondaryColor()).append("\n\n");
        }

        int totalVip = getMaxPossibleVipBackpacks();
        int maxTotal = ConfigManager.getConfig().maxBackpacksPerPlayer;
        boolean compatible = isVipConfigurationValid();

        summary.append("§eSystem Status:\n");
        summary.append("  §7Total VIP backpacks: §a").append(totalVip).append("\n");
        summary.append("  §7Max player limit: §a").append(maxTotal).append("\n");
        summary.append("  §7Configuration: ").append(compatible ? "§a✓ Valid" : "§c✗ Issues").append("\n");

        if (!compatible) {
            summary.append("\n§cIssues detected:\n");
            summary.append("  §7VIP backpacks + defaults may exceed player limit\n");
            summary.append("  §7Consider increasing maxBackpacksPerPlayer\n");
        }

        return summary.toString();
    }
}