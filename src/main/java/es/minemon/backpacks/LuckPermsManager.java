package es.minemon.backpacks;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Gestor de permisos con LuckPerms optimizado
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

    // Permisos administrativos
    public static final String ADMIN_VIEW_PERMISSION = "backpack.admin.view";
    public static final String ADMIN_EDIT_PERMISSION = "backpack.admin.edit";
    public static final String ADMIN_GIVE_PERMISSION = "backpack.admin.give";
    public static final String ADMIN_REMOVE_PERMISSION = "backpack.admin.remove";
    public static final String ADMIN_RENAME_PERMISSION = "backpack.admin.rename";
    public static final String ADMIN_SYNC_PERMISSION = "backpack.admin.sync";
    public static final String ADMIN_BACKUP_PERMISSION = "backpack.admin.backup";
    public static final String ADMIN_CONFIG_PERMISSION = "backpack.admin.config";

    // Permisos VIP
    public static final String VIP_CRISTAL_PERMISSION = "backpack.cristal";
    public static final String VIP_RUBY_PERMISSION = "backpack.ruby";
    public static final String VIP_ESMERALDA_PERMISSION = "backpack.esmeralda";
    public static final String VIP_PERLA_PERMISSION = "backpack.perla";
    public static final String VIP_PLATINO_PERMISSION = "backpack.platino";

    public static void initialize() {
        if (initializationAttempted) {
            return;
        }

        initializationAttempted = true;

        try {
            // Detectar LuckPerms
            boolean luckPermsModLoaded = FabricLoader.getInstance().isModLoaded("luckperms");

            boolean luckPermsClassAvailable = false;
            try {
                Class.forName("net.luckperms.api.LuckPermsProvider");
                luckPermsClassAvailable = true;
            } catch (ClassNotFoundException e) {
                // No disponible
            }

            // Intentar obtener instancia
            if (luckPermsModLoaded || luckPermsClassAvailable) {
                try {
                    luckPerms = LuckPermsProvider.get();
                    isLuckPermsAvailable = true;
                    BackpacksMod.LOGGER.info("LuckPerms integrado exitosamente");
                    return;
                } catch (Exception e) {
                    BackpacksMod.LOGGER.warn("Error obteniendo LuckPerms: " + e.getMessage());
                }
            }

            // Fallback
            BackpacksMod.LOGGER.info("LuckPerms no detectado, usando sistema OP");
            isLuckPermsAvailable = false;

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error inicializando permisos", e);
            isLuckPermsAvailable = false;
        }
    }

    public static void forceReinitialization() {
        initializationAttempted = false;
        luckPerms = null;
        isLuckPermsAvailable = false;
        initialize();
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
                try {
                    user = luckPerms.getUserManager().loadUser(player.getUuid()).get(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    return fallbackPermissionCheck(player, permission);
                }
            }

            if (user == null) {
                return fallbackPermissionCheck(player, permission);
            }

            return user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions())
                    .checkPermission(permission).asBoolean();

        } catch (Exception e) {
            return fallbackPermissionCheck(player, permission);
        }
    }

    /**
     * Sistema de permisos de respaldo
     */
    private static boolean fallbackPermissionCheck(ServerPlayerEntity player, String permission) {
        // Permisos básicos para todos
        if (permission.equals(USE_PERMISSION) ||
                permission.equals(VIEW_OWN_PERMISSION) ||
                permission.equals(RENAME_PERMISSION) ||
                permission.equals(CHANGE_ICON_PERMISSION) ||
                permission.equals(VIEW_STATS_PERMISSION)) {
            return true;
        }

        // VIP y admin requieren OP
        if (permission.startsWith("backpack.admin") ||
                permission.equals(VIP_CRISTAL_PERMISSION) ||
                permission.equals(VIP_RUBY_PERMISSION) ||
                permission.equals(VIP_ESMERALDA_PERMISSION) ||
                permission.equals(VIP_PERLA_PERMISSION) ||
                permission.equals(VIP_PLATINO_PERMISSION)) {
            return player.hasPermissionLevel(ConfigManager.getConfig().adminPermissionLevel);
        }

        return false;
    }

    // Métodos de verificación simplificados
    public static boolean isAdmin(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_PERMISSION);
    }

    public static boolean canUseBackpacks(ServerPlayerEntity player) {
        return hasPermission(player, USE_PERMISSION);
    }

    public static boolean canRename(ServerPlayerEntity player) {
        return hasPermission(player, RENAME_PERMISSION);
    }

    public static boolean canChangeIcon(ServerPlayerEntity player) {
        return hasPermission(player, CHANGE_ICON_PERMISSION);
    }

    public static boolean canViewStats(ServerPlayerEntity player) {
        return hasPermission(player, VIEW_STATS_PERMISSION);
    }

    public static boolean canViewOthers(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_VIEW_PERMISSION);
    }

    public static boolean canEditOthers(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_EDIT_PERMISSION);
    }

    public static boolean canGiveBackpacks(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_GIVE_PERMISSION);
    }

    public static boolean canRemoveBackpacks(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_REMOVE_PERMISSION);
    }

    public static boolean canAdminRename(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_RENAME_PERMISSION);
    }

    public static boolean canSync(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_SYNC_PERMISSION);
    }

    public static boolean canManageBackups(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_BACKUP_PERMISSION);
    }

    public static boolean canModifyConfig(ServerPlayerEntity player) {
        return hasPermission(player, ADMIN_CONFIG_PERMISSION);
    }

    // Métodos VIP
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

    public static String getPermissionSystemInfo() {
        if (isLuckPermsAvailable && luckPerms != null) {
            try {
                return String.format("LuckPerms v%s", luckPerms.getPluginMetadata().getVersion());
            } catch (Exception e) {
                return "LuckPerms (versión desconocida)";
            }
        } else {
            return "Sistema OP (LuckPerms no disponible)";
        }
    }

    public static boolean isLuckPermsAvailable() {
        return isLuckPermsAvailable && luckPerms != null;
    }

    public static LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public static void sendNoPermissionMessage(ServerPlayerEntity player, String permission) {
        if (isLuckPermsAvailable()) {
            LanguageManager.sendMessage(player, "errorNoPermissionLuckPerms", permission);
        } else {
            LanguageManager.sendMessage(player, "errorNoPermissionFallback");
        }
    }

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

    public static String getPlayerPermissionInfo(ServerPlayerEntity target) {
        if (!isLuckPermsAvailable()) {
            boolean isOp = target.hasPermissionLevel(ConfigManager.getConfig().adminPermissionLevel);
            return String.format("Sistema OP - Nivel: %d, Es OP: %s",
                    getPlayerPermissionLevel(target), isOp ? "Sí" : "No");
        }

        try {
            User user = luckPerms.getUserManager().getUser(target.getUuid());
            if (user == null) {
                try {
                    user = luckPerms.getUserManager().loadUser(target.getUuid()).get(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    return "Usuario no encontrado en LuckPerms";
                }
            }

            if (user == null) {
                return "Usuario no encontrado";
            }

            StringBuilder info = new StringBuilder();
            info.append("Usuario LuckPerms: ").append(user.getUsername()).append("\n");
            info.append("Grupo primario: ").append(user.getPrimaryGroup()).append("\n");
            info.append("Permisos principales:\n");

            String[] permissions = {
                    USE_PERMISSION, RENAME_PERMISSION, ADMIN_PERMISSION,
                    VIP_CRISTAL_PERMISSION, VIP_RUBY_PERMISSION, VIP_ESMERALDA_PERMISSION
            };

            for (String perm : permissions) {
                boolean has = user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions())
                        .checkPermission(perm).asBoolean();
                info.append("  ").append(perm).append(": ").append(has ? "✓" : "✗").append("\n");
            }

            return info.toString();

        } catch (Exception e) {
            return "Error obteniendo información: " + e.getMessage();
        }
    }

    private static int getPlayerPermissionLevel(ServerPlayerEntity player) {
        try {
            for (int level = 4; level >= 0; level--) {
                if (player.hasPermissionLevel(level)) {
                    return level;
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void onPlayerPermissionChange(ServerPlayerEntity player) {
        try {
            VipBackpackManager.onPermissionChange(player);
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error procesando cambio de permisos", e);
        }
    }
}