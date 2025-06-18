package es.minemon.backpacks;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Gestor de permisos con LuckPerms CORREGIDO
 * Detección mejorada para LuckPerms-Fabric-5.4.140
 * ACTUALIZADO: Incluye permisos VIP
 */
public class LuckPermsManager {
    private static LuckPerms luckPerms;
    private static boolean isLuckPermsAvailable = false;
    private static boolean initializationAttempted = false;

    // Permisos básicos
    public static final String ADMIN_PERMISSION = "backpack.admin";
    public static final String USE_PERMISSION = "backpack.use";
    public static final String RENAME_PERMISSION = "backpack.rename";
    public static final String CHANGE_ICON_PERMISSION = "backpack.changeicon";
    public static final String VIEW_OWN_PERMISSION = "backpack.view.own";
    public static final String VIEW_STATS_PERMISSION = "backpack.stats";

    // Permisos administrativos específicos
    public static final String ADMIN_VIEW_PERMISSION = "backpack.admin.view";
    public static final String ADMIN_EDIT_PERMISSION = "backpack.admin.edit";
    public static final String ADMIN_GIVE_PERMISSION = "backpack.admin.give";
    public static final String ADMIN_REMOVE_PERMISSION = "backpack.admin.remove";
    public static final String ADMIN_RENAME_PERMISSION = "backpack.admin.rename";
    public static final String ADMIN_SYNC_PERMISSION = "backpack.admin.sync";
    public static final String ADMIN_BACKUP_PERMISSION = "backpack.admin.backup";
    public static final String ADMIN_CONFIG_PERMISSION = "backpack.admin.config";

    // NUEVOS: Permisos VIP
    public static final String VIP_CRISTAL_PERMISSION = "backpack.cristal";
    public static final String VIP_RUBY_PERMISSION = "backpack.ruby";
    public static final String VIP_ESMERALDA_PERMISSION = "backpack.esmeralda";
    public static final String VIP_PERLA_PERMISSION = "backpack.perla";
    public static final String VIP_PLATINO_PERMISSION = "backpack.platino";

    /**
     * Inicializa LuckPerms con detección mejorada
     */
    public static void initialize() {
        if (initializationAttempted) {
            return; // Evitar múltiples intentos
        }

        initializationAttempted = true;

        try {
            // MÉTODO 1: Verificar si LuckPerms está cargado como mod
            boolean luckPermsModLoaded = FabricLoader.getInstance().isModLoaded("luckperms");
            BackpacksMod.LOGGER.info("LuckPerms mod detection: " + luckPermsModLoaded);

            // MÉTODO 2: Verificar si la clase está disponible
            boolean luckPermsClassAvailable = false;
            try {
                Class.forName("net.luckperms.api.LuckPermsProvider");
                luckPermsClassAvailable = true;
                BackpacksMod.LOGGER.info("LuckPerms API class found: true");
            } catch (ClassNotFoundException e) {
                BackpacksMod.LOGGER.info("LuckPerms API class found: false");
            }

            // MÉTODO 3: Intentar obtener la instancia directamente
            if (luckPermsModLoaded || luckPermsClassAvailable) {
                try {
                    luckPerms = LuckPermsProvider.get();
                    isLuckPermsAvailable = true;
                    BackpacksMod.LOGGER.info("LuckPerms integration ENABLED successfully");
                    BackpacksMod.LOGGER.info("LuckPerms version: " + luckPerms.getPluginMetadata().getVersion());

                    // Registrar permisos por defecto
                    registerDefaultPermissions();

                    return;
                } catch (Exception e) {
                    BackpacksMod.LOGGER.warn("Failed to get LuckPerms instance: " + e.getMessage());
                }
            }

            // Si llegamos aquí, LuckPerms no está disponible
            BackpacksMod.LOGGER.info("LuckPerms not detected, using fallback permission system");
            BackpacksMod.LOGGER.info("Install LuckPerms-Fabric for granular permission control");
            isLuckPermsAvailable = false;

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error during LuckPerms initialization: " + e.getMessage(), e);
            isLuckPermsAvailable = false;
        }
    }

    /**
     * Fuerza la re-detección de LuckPerms (para uso en comandos)
     */
    public static void forceReinitialization() {
        initializationAttempted = false;
        luckPerms = null;
        isLuckPermsAvailable = false;
        initialize();
    }

    /**
     * Registra permisos por defecto para grupos
     */
    private static void registerDefaultPermissions() {
        if (!isLuckPermsAvailable) return;

        try {
            BackpacksMod.LOGGER.info("LuckPerms permissions available:");
            BackpacksMod.LOGGER.info("  User permissions:");
            BackpacksMod.LOGGER.info("    - " + USE_PERMISSION + " (use backpacks)");
            BackpacksMod.LOGGER.info("    - " + VIEW_OWN_PERMISSION + " (view own backpacks)");
            BackpacksMod.LOGGER.info("    - " + RENAME_PERMISSION + " (rename own backpacks)");
            BackpacksMod.LOGGER.info("    - " + CHANGE_ICON_PERMISSION + " (change backpack icons)");
            BackpacksMod.LOGGER.info("    - " + VIEW_STATS_PERMISSION + " (view own stats)");
            BackpacksMod.LOGGER.info("  VIP permissions:");
            BackpacksMod.LOGGER.info("    - " + VIP_CRISTAL_PERMISSION + " (5 VIP backpacks)");
            BackpacksMod.LOGGER.info("    - " + VIP_RUBY_PERMISSION + " (10 VIP backpacks)");
            BackpacksMod.LOGGER.info("    - " + VIP_ESMERALDA_PERMISSION + " (15 VIP backpacks)");
            BackpacksMod.LOGGER.info("    - " + VIP_PERLA_PERMISSION + " (20 VIP backpacks)");
            BackpacksMod.LOGGER.info("    - " + VIP_PLATINO_PERMISSION + " (30 VIP backpacks)");
            BackpacksMod.LOGGER.info("  Admin permissions:");
            BackpacksMod.LOGGER.info("    - " + ADMIN_PERMISSION + " (main admin permission)");
            BackpacksMod.LOGGER.info("    - " + ADMIN_VIEW_PERMISSION + " (view other players' backpacks)");
            BackpacksMod.LOGGER.info("    - " + ADMIN_EDIT_PERMISSION + " (edit other players' backpacks)");
            BackpacksMod.LOGGER.info("    - " + ADMIN_GIVE_PERMISSION + " (give backpacks to players)");
            BackpacksMod.LOGGER.info("    - " + ADMIN_REMOVE_PERMISSION + " (remove backpacks)");
            BackpacksMod.LOGGER.info("    - " + ADMIN_RENAME_PERMISSION + " (rename any backpack)");
            BackpacksMod.LOGGER.info("    - " + ADMIN_SYNC_PERMISSION + " (sync player data)");
            BackpacksMod.LOGGER.info("    - " + ADMIN_BACKUP_PERMISSION + " (manage backups)");
            BackpacksMod.LOGGER.info("    - " + ADMIN_CONFIG_PERMISSION + " (modify configuration)");
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error registering default permissions", e);
        }
    }

    /**
     * Verifica si un jugador tiene un permiso específico
     */
    public static boolean hasPermission(ServerPlayerEntity player, String permission) {
        if (!isLuckPermsAvailable || luckPerms == null) {
            return fallbackPermissionCheck(player, permission);
        }

        try {
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) {
                // Intentar cargar el usuario
                try {
                    user = luckPerms.getUserManager().loadUser(player.getUuid()).get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    BackpacksMod.LOGGER.warn("Could not load LuckPerms user for " + player.getName().getString());
                    return fallbackPermissionCheck(player, permission);
                }
            }

            if (user == null) {
                return fallbackPermissionCheck(player, permission);
            }

            // Verificar el permiso con el contexto del jugador
            return user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions())
                    .checkPermission(permission).asBoolean();

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error checking LuckPerms permission for " + player.getName().getString() + ": " + e.getMessage());
            return fallbackPermissionCheck(player, permission);
        }
    }

    /**
     * Sistema de permisos de respaldo cuando LuckPerms no está disponible
     */
    private static boolean fallbackPermissionCheck(ServerPlayerEntity player, String permission) {
        // Permisos básicos para todos los jugadores
        if (permission.equals(USE_PERMISSION) ||
                permission.equals(VIEW_OWN_PERMISSION) ||
                permission.equals(RENAME_PERMISSION) ||
                permission.equals(CHANGE_ICON_PERMISSION) ||
                permission.equals(VIEW_STATS_PERMISSION)) {
            return true;
        }

        // Permisos VIP en modo fallback: solo para OPs
        if (permission.equals(VIP_CRISTAL_PERMISSION) ||
                permission.equals(VIP_RUBY_PERMISSION) ||
                permission.equals(VIP_ESMERALDA_PERMISSION) ||
                permission.equals(VIP_PERLA_PERMISSION) ||
                permission.equals(VIP_PLATINO_PERMISSION)) {
            return player.hasPermissionLevel(ConfigManager.getConfig().adminPermissionLevel);
        }

        // Permisos administrativos requieren nivel de OP
        if (permission.startsWith("backpack.admin")) {
            return player.hasPermissionLevel(ConfigManager.getConfig().adminPermissionLevel);
        }

        return false;
    }

    /**
     * Verifica si un jugador es administrador
     */
    public static boolean isAdmin(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_PERMISSION);
    }

    /**
     * Verifica si un jugador puede usar mochilas
     */
    public static boolean canUseBackpacks(ServerPlayerEntity player) {
        return hasPermission(player, USE_PERMISSION);
    }

    /**
     * Verifica si un jugador puede renombrar sus mochilas
     */
    public static boolean canRename(ServerPlayerEntity player) {
        return hasPermission(player, RENAME_PERMISSION);
    }

    /**
     * Verifica si un jugador puede cambiar iconos de mochilas
     */
    public static boolean canChangeIcon(ServerPlayerEntity player) {
        return hasPermission(player, CHANGE_ICON_PERMISSION);
    }

    /**
     * Verifica si un jugador puede ver estadísticas
     */
    public static boolean canViewStats(ServerPlayerEntity player) {
        return hasPermission(player, VIEW_STATS_PERMISSION);
    }

    /**
     * Verifica si un jugador puede ver mochilas de otros jugadores
     */
    public static boolean canViewOthers(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_VIEW_PERMISSION);
    }

    /**
     * Verifica si un jugador puede editar mochilas de otros jugadores
     */
    public static boolean canEditOthers(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_EDIT_PERMISSION);
    }

    /**
     * Verifica si un jugador puede dar mochilas a otros
     */
    public static boolean canGiveBackpacks(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_GIVE_PERMISSION);
    }

    /**
     * Verifica si un jugador puede remover mochilas
     */
    public static boolean canRemoveBackpacks(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_REMOVE_PERMISSION);
    }

    /**
     * Verifica si un jugador puede renombrar mochilas de otros
     */
    public static boolean canAdminRename(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_RENAME_PERMISSION);
    }

    /**
     * Verifica si un jugador puede sincronizar datos
     */
    public static boolean canSync(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_SYNC_PERMISSION);
    }

    /**
     * Verifica si un jugador puede gestionar backups
     */
    public static boolean canManageBackups(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_BACKUP_PERMISSION);
    }

    /**
     * Verifica si un jugador puede modificar la configuración
     */
    public static boolean canModifyConfig(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_CONFIG_PERMISSION);
    }

    // NUEVOS: Métodos para verificar permisos VIP
    public static boolean hasVipCristal(ServerPlayerEntity player) {
        return hasPermission(player, VIP_CRISTAL_PERMISSION);
    }

    public static boolean hasVipRuby(ServerPlayerEntity player) {
        return hasPermission(player, VIP_RUBY_PERMISSION);
    }

    public static boolean hasVipEsmeralda(ServerPlayerEntity player) {
        return hasPermission(player, VIP_ESMERALDA_PERMISSION);
    }

    public static boolean hasVipPerla(ServerPlayerEntity player) {
        return hasPermission(player, VIP_PERLA_PERMISSION);
    }

    public static boolean hasVipPlatino(ServerPlayerEntity player) {
        return hasPermission(player, VIP_PLATINO_PERMISSION);
    }

    /**
     * Obtiene información sobre el estado de LuckPerms
     */
    public static String getPermissionSystemInfo() {
        if (isLuckPermsAvailable && luckPerms != null) {
            try {
                return String.format("LuckPerms v%s (Active)", luckPerms.getPluginMetadata().getVersion());
            } catch (Exception e) {
                return "LuckPerms (Active, version unknown)";
            }
        } else {
            return "Fallback system (LuckPerms not available)";
        }
    }

    /**
     * Verifica si LuckPerms está disponible
     */
    public static boolean isLuckPermsAvailable() {
        return isLuckPermsAvailable && luckPerms != null;
    }

    /**
     * Obtiene el objeto LuckPerms (para uso avanzado)
     */
    public static LuckPerms getLuckPerms() {
        return luckPerms;
    }

    /**
     * Envía mensaje de error de permisos personalizado
     */
    public static void sendNoPermissionMessage(ServerPlayerEntity player, String permission) {
        if (isLuckPermsAvailable()) {
            LanguageManager.sendMessage(player, "errorNoPermissionLuckPerms", permission);
        } else {
            LanguageManager.sendMessage(player, "errorNoPermissionFallback");
        }
    }

    /**
     * Comando para recargar permisos (para admins)
     */
    public static boolean reloadPermissions(ServerPlayerEntity admin) {
        if (!canModifyConfig(admin)) {
            sendNoPermissionMessage(admin, ADMIN_CONFIG_PERMISSION);
            return false;
        }

        try {
            forceReinitialization();
            LanguageManager.sendMessage(admin, "permissionsReloaded", getPermissionSystemInfo());
            return true;
        } catch (Exception e) {
            LanguageManager.sendMessage(admin, "errorReloadingPermissions", e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene información detallada de permisos para un jugador (para debug de admins)
     */
    public static String getPlayerPermissionInfo(ServerPlayerEntity target) {
        if (!isLuckPermsAvailable()) {
            boolean isOp = target.hasPermissionLevel(ConfigManager.getConfig().adminPermissionLevel);
            int permissionLevel = getPlayerPermissionLevel(target);
            return String.format("Fallback permissions - Permission Level: %d, Is OP: %s",
                    permissionLevel, isOp ? "Yes" : "No");
        }

        try {
            User user = luckPerms.getUserManager().getUser(target.getUuid());
            if (user == null) {
                // Intentar cargar el usuario
                try {
                    user = luckPerms.getUserManager().loadUser(target.getUuid()).get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    return "User not found in LuckPerms (could not load)";
                }
            }

            if (user == null) {
                return "User not found in LuckPerms";
            }

            StringBuilder info = new StringBuilder();
            info.append("LuckPerms User: ").append(user.getUsername()).append("\n");
            info.append("Primary Group: ").append(user.getPrimaryGroup()).append("\n");
            info.append("Backpack Permissions:\n");

            String[] permissions = {
                    USE_PERMISSION, VIEW_OWN_PERMISSION, RENAME_PERMISSION,
                    CHANGE_ICON_PERMISSION, VIEW_STATS_PERMISSION, ADMIN_PERMISSION,
                    VIP_CRISTAL_PERMISSION, VIP_RUBY_PERMISSION, VIP_ESMERALDA_PERMISSION,
                    VIP_PERLA_PERMISSION, VIP_PLATINO_PERMISSION
            };

            for (String perm : permissions) {
                boolean has = user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions())
                        .checkPermission(perm).asBoolean();
                info.append("  ").append(perm).append(": ").append(has ? "✓" : "✗").append("\n");
            }

            return info.toString();

        } catch (Exception e) {
            return "Error retrieving permission info: " + e.getMessage();
        }
    }

    /**
     * Método auxiliar para obtener nivel de permisos del jugador
     */
    private static int getPlayerPermissionLevel(ServerPlayerEntity player) {
        try {
            if (player.getServer() != null && player.getServer().getPlayerManager() != null) {
                for (int level = 4; level >= 0; level--) {
                    if (player.hasPermissionLevel(level)) {
                        return level;
                    }
                }
            }
            return 0;
        } catch (Exception e) {
            BackpacksMod.LOGGER.warn("Error getting permission level for player " + player.getName().getString(), e);
            return 0;
        }
    }

    /**
     * Método para verificar si un jugador es OP
     */
    public static boolean isPlayerOp(ServerPlayerEntity player) {
        try {
            return player.hasPermissionLevel(ConfigManager.getConfig().adminPermissionLevel);
        } catch (Exception e) {
            BackpacksMod.LOGGER.warn("Error checking OP status for player " + player.getName().getString(), e);
            return false;
        }
    }

    /**
     * Obtiene información básica de permisos para mostrar en comandos
     */
    public static String getBasicPermissionInfo(ServerPlayerEntity player) {
        StringBuilder info = new StringBuilder();

        info.append("Permission System: ").append(getPermissionSystemInfo()).append("\n");

        if (isLuckPermsAvailable()) {
            info.append("LuckPerms Status: Active\n");
            try {
                User user = luckPerms.getUserManager().getUser(player.getUuid());
                if (user != null) {
                    info.append("Primary Group: ").append(user.getPrimaryGroup()).append("\n");
                } else {
                    info.append("User not loaded in LuckPerms\n");
                }
            } catch (Exception e) {
                info.append("LuckPerms Error: ").append(e.getMessage()).append("\n");
            }
        } else {
            info.append("Fallback System Active\n");
            info.append("Permission Level: ").append(getPlayerPermissionLevel(player)).append("\n");
            info.append("Is OP: ").append(isPlayerOp(player) ? "Yes" : "No").append("\n");
        }

        return info.toString();
    }

    /**
     * Método de diagnóstico para verificar la instalación de LuckPerms
     */
    public static String getDiagnosticInfo() {
        StringBuilder diag = new StringBuilder();
        diag.append("=== LuckPerms Diagnostic Information ===\n");

        // Verificar mod
        boolean modLoaded = FabricLoader.getInstance().isModLoaded("luckperms");
        diag.append("Mod loaded: ").append(modLoaded).append("\n");

        // Verificar clase
        boolean classAvailable = false;
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            classAvailable = true;
        } catch (ClassNotFoundException e) {
            // Clase no disponible
        }
        diag.append("API class available: ").append(classAvailable).append("\n");

        // Verificar instancia
        diag.append("Instance available: ").append(luckPerms != null).append("\n");
        diag.append("Integration active: ").append(isLuckPermsAvailable).append("\n");
        diag.append("Initialization attempted: ").append(initializationAttempted).append("\n");

        if (luckPerms != null) {
            try {
                diag.append("Version: ").append(luckPerms.getPluginMetadata().getVersion()).append("\n");
            } catch (Exception e) {
                diag.append("Version: Error getting version - ").append(e.getMessage()).append("\n");
            }
        }

        // Información de mods cargados
        diag.append("\nLoaded mods containing 'luck':\n");
        FabricLoader.getInstance().getAllMods().forEach(modContainer -> {
            String modId = modContainer.getMetadata().getId();
            if (modId.toLowerCase().contains("luck")) {
                diag.append("- ").append(modId).append(" v").append(modContainer.getMetadata().getVersion()).append("\n");
            }
        });

        return diag.toString();
    }

    /**
     * NUEVO: Notifica cuando cambian los permisos de un jugador
     * Este método debe ser llamado externamente cuando se detecten cambios de permisos
     */
    public static void onPlayerPermissionChange(ServerPlayerEntity player) {
        try {
            // Actualizar mochilas VIP basándose en los nuevos permisos
            VipBackpackManager.onPermissionChange(player);

            BackpacksMod.LOGGER.debug("Permission change processed for player: " + player.getName().getString());
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error processing permission change for player " + player.getName().getString(), e);
        }
    }
}