package es.minemon.backpacks;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public class PermissionCommands {

    // Definir todos los permisos en una lista para el autocompletado
    private static final List<String> ALL_PERMISSIONS = List.of(
            LuckPermsManager.USE_PERMISSION,
            LuckPermsManager.VIEW_OWN_PERMISSION,
            LuckPermsManager.RENAME_PERMISSION,
            LuckPermsManager.CHANGE_ICON_PERMISSION,
            LuckPermsManager.VIEW_STATS_PERMISSION,
            LuckPermsManager.ADMIN_PERMISSION,
            LuckPermsManager.ADMIN_VIEW_PERMISSION,
            LuckPermsManager.ADMIN_EDIT_PERMISSION,
            LuckPermsManager.ADMIN_GIVE_PERMISSION,
            LuckPermsManager.ADMIN_REMOVE_PERMISSION,
            LuckPermsManager.ADMIN_RENAME_PERMISSION,
            LuckPermsManager.ADMIN_SYNC_PERMISSION,
            LuckPermsManager.ADMIN_BACKUP_PERMISSION,
            LuckPermsManager.ADMIN_CONFIG_PERMISSION
    );

    // Crear el proveedor de sugerencias
    private static final SuggestionProvider<ServerCommandSource> PERMISSION_SUGGESTION_PROVIDER = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        for (String permission : ALL_PERMISSIONS) {
            if (permission.toLowerCase().startsWith(remaining)) {
                builder.suggest(permission);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {

        String permCommand = ConfigManager.getConfig().mainCommand + "-perms";

        dispatcher.register(CommandManager.literal(permCommand)
                .requires(source -> {
                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                    return LuckPermsManager.canModifyConfig(player);
                })

                .then(CommandManager.literal("info")
                        .executes(PermissionCommands::showPermissionInfo))

                .then(CommandManager.literal("reload")
                        .executes(PermissionCommands::reloadPermissions))

                .then(CommandManager.literal("check")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(PermissionCommands::checkPlayerPermissions)))

                .then(CommandManager.literal("list")
                        .executes(PermissionCommands::listAllPermissions))

                .then(CommandManager.literal("test")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("permission", StringArgumentType.string())
                                        .suggests(PERMISSION_SUGGESTION_PROVIDER)
                                        .executes(PermissionCommands::testSpecificPermission))))

                .then(CommandManager.literal("guide")
                        .executes(PermissionCommands::showLuckPermsGuide))

                .then(CommandManager.literal("setup")
                        .executes(PermissionCommands::showRecommendedSetup))

                .then(CommandManager.literal("stats")
                        .executes(PermissionCommands::showPermissionStats))

                // NUEVO: Comando de diagnóstico para problemas de LuckPerms
                .then(CommandManager.literal("diagnose")
                        .executes(PermissionCommands::showDiagnosticInfo)));

        dispatcher.register(CommandManager.literal("backpack-permissions")
                .requires(source -> {
                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                    return LuckPermsManager.canModifyConfig(player);
                })
                .executes(PermissionCommands::showQuickPermissionHelp));
    }

    private static int showPermissionInfo(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        StringBuilder message = new StringBuilder();
        message.append("§6=== BackpacksMod Permission System ===\n");
        message.append("§eSystem Status:\n");
        message.append("  §7• Type: §a").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");
        message.append("  §7• LuckPerms Available: §a").append(LuckPermsManager.isLuckPermsAvailable() ? "Yes" : "No").append("\n");
        message.append("  §7• Fallback Level: §a").append(ConfigManager.getConfig().adminPermissionLevel).append("\n\n");

        if (LuckPermsManager.isLuckPermsAvailable()) {
            message.append("§a✓ LuckPerms is active and functioning properly\n");
            message.append("§7Use LuckPerms commands to manage permissions granularly\n\n");
        } else {
            message.append("§c⚠ LuckPerms not available - using OP-based fallback\n");
            message.append("§7Install LuckPerms for advanced permission management\n");
            message.append("§7Use §e/").append(ConfigManager.getConfig().mainCommand).append("-perms diagnose §7for troubleshooting\n\n");
        }

        message.append("§ePermission Statistics:\n");
        message.append("  §7• Total permission nodes: §a").append(getTotalPermissionNodes()).append("\n");
        message.append("  §7• User permissions: §a").append(getUserPermissionCount()).append("\n");
        message.append("  §7• Admin permissions: §a").append(getAdminPermissionCount()).append("\n\n");
        message.append("§7Use §a/").append(ConfigManager.getConfig().mainCommand).append("-perms §7for more options");

        context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);
        return 1;
    }

    private static int reloadPermissions(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }
        return LuckPermsManager.reloadPermissions(admin) ? 1 : 0;
    }

    private static int checkPlayerPermissions(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String permissionInfo = LuckPermsManager.getPlayerPermissionInfo(targetPlayer);

            admin.sendMessage(Text.literal(""), false);
            admin.sendMessage(Text.literal("§6=== Permission Check for " + targetPlayer.getName().getString() + " ==="), false);
            admin.sendMessage(Text.literal(permissionInfo), false);
            admin.sendMessage(Text.literal("§eBackpack Permissions:"), false);
            checkAndDisplayPermission(admin, targetPlayer, LuckPermsManager.USE_PERMISSION);
            checkAndDisplayPermission(admin, targetPlayer, LuckPermsManager.RENAME_PERMISSION);
            checkAndDisplayPermission(admin, targetPlayer, LuckPermsManager.CHANGE_ICON_PERMISSION);
            checkAndDisplayPermission(admin, targetPlayer, LuckPermsManager.ADMIN_PERMISSION);
            checkAndDisplayPermission(admin, targetPlayer, LuckPermsManager.ADMIN_VIEW_PERMISSION);
            checkAndDisplayPermission(admin, targetPlayer, LuckPermsManager.ADMIN_EDIT_PERMISSION);
            admin.sendMessage(Text.literal(""), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError checking permissions: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static void checkAndDisplayPermission(ServerPlayerEntity admin, ServerPlayerEntity target, String permission) {
        boolean hasPermission = LuckPermsManager.hasPermission(target, permission);
        String status = hasPermission ? "§a✓" : "§c✗";
        admin.sendMessage(Text.literal("  " + status + " §7" + permission), false);
    }

    private static int listAllPermissions(CommandContext<ServerCommandSource> context) {
        StringBuilder message = new StringBuilder();
        message.append("§6=== All BackpacksMod Permissions ===\n\n");
        message.append("§eUser Permissions:\n");
        message.append("§7  • ").append(LuckPermsManager.USE_PERMISSION).append(" - Use backpacks\n");
        message.append("§7  • ").append(LuckPermsManager.VIEW_OWN_PERMISSION).append(" - View own backpacks\n");
        message.append("§7  • ").append(LuckPermsManager.RENAME_PERMISSION).append(" - Rename own backpacks\n");
        message.append("§7  • ").append(LuckPermsManager.CHANGE_ICON_PERMISSION).append(" - Change backpack icons\n");
        message.append("§7  • ").append(LuckPermsManager.VIEW_STATS_PERMISSION).append(" - View own statistics\n\n");
        message.append("§cAdmin Permissions:\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_PERMISSION).append(" - Main admin permission\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_VIEW_PERMISSION).append(" - View others' backpacks\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_EDIT_PERMISSION).append(" - Edit others' backpacks\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_GIVE_PERMISSION).append(" - Give backpacks to players\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_REMOVE_PERMISSION).append(" - Remove backpacks\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_RENAME_PERMISSION).append(" - Rename any backpack\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_SYNC_PERMISSION).append(" - Sync player data\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_BACKUP_PERMISSION).append(" - Manage backups\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_CONFIG_PERMISSION).append(" - Modify configuration\n\n");

        if (LuckPermsManager.isLuckPermsAvailable()) {
            message.append("§aTo grant permissions, use:\n");
            message.append("§7/lp user <player> permission set <permission> true\n");
            message.append("§7/lp group <group> permission set <permission> true\n\n");
        } else {
            message.append("§cLuckPerms not available. Admin permissions require OP level ")
                    .append(ConfigManager.getConfig().adminPermissionLevel).append("\n\n");
        }

        message.append("§7Use §a/").append(ConfigManager.getConfig().mainCommand).append("-perms guide §7for setup help");
        context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);
        return 1;
    }

    private static int testSpecificPermission(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String permission = StringArgumentType.getString(context, "permission");
            boolean hasPermission = LuckPermsManager.hasPermission(targetPlayer, permission);
            String result = hasPermission ? "§aHAS" : "§cDOES NOT HAVE";
            String message = String.format("§7Player §e%s %s §7permission §e%s",
                    targetPlayer.getName().getString(), result, permission);
            context.getSource().sendFeedback(() -> Text.literal(message), false);
            return hasPermission ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError testing permission: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int showLuckPermsGuide(CommandContext<ServerCommandSource> context) {
        StringBuilder guide = new StringBuilder();
        guide.append("§6=== LuckPerms Setup Guide for BackpacksMod ===\n\n");

        if (!LuckPermsManager.isLuckPermsAvailable()) {
            guide.append("§c⚠ LuckPerms is not installed!\n");
            guide.append("§71. Download LuckPerms-Fabric from: https://luckperms.net/download\n");
            guide.append("§72. Place LuckPerms-Fabric-5.4.140.jar in your mods folder\n");
            guide.append("§73. Restart the server\n");
            guide.append("§74. Run §e/").append(ConfigManager.getConfig().mainCommand).append("-perms diagnose §7to verify installation\n\n");
        } else {
            guide.append("§a✓ LuckPerms is installed and active\n\n");
        }

        guide.append("§eRecommended Permission Setup:\n\n");
        guide.append("§31. Create groups:\n");
        guide.append("§7/lp creategroup backpack_user\n");
        guide.append("§7/lp creategroup backpack_admin\n\n");
        guide.append("§32. Grant basic permissions to users:\n");
        guide.append("§7/lp group backpack_user permission set ").append(LuckPermsManager.USE_PERMISSION).append(" true\n");
        guide.append("§7/lp group backpack_user permission set ").append(LuckPermsManager.VIEW_OWN_PERMISSION).append(" true\n");
        guide.append("§7/lp group backpack_user permission set ").append(LuckPermsManager.RENAME_PERMISSION).append(" true\n");
        guide.append("§7/lp group backpack_user permission set ").append(LuckPermsManager.CHANGE_ICON_PERMISSION).append(" true\n");
        guide.append("§7/lp group backpack_user permission set ").append(LuckPermsManager.VIEW_STATS_PERMISSION).append(" true\n\n");
        guide.append("§33. Grant admin permissions:\n");
        guide.append("§7/lp group backpack_admin permission set ").append(LuckPermsManager.ADMIN_PERMISSION).append(" true\n");
        guide.append("§7/lp group backpack_admin permission set ").append(LuckPermsManager.ADMIN_VIEW_PERMISSION).append(" true\n");
        guide.append("§7/lp group backpack_admin permission set ").append(LuckPermsManager.ADMIN_EDIT_PERMISSION).append(" true\n");
        guide.append("§7/lp group backpack_admin permission set ").append(LuckPermsManager.ADMIN_GIVE_PERMISSION).append(" true\n");
        guide.append("§7/lp group backpack_admin permission set ").append(LuckPermsManager.ADMIN_REMOVE_PERMISSION).append(" true\n\n");
        guide.append("§34. Assign players to groups:\n");
        guide.append("§7/lp user <player> parent add backpack_user\n");
        guide.append("§7/lp user <admin> parent add backpack_admin\n\n");
        guide.append("§35. Make admins inherit user permissions:\n");
        guide.append("§7/lp group backpack_admin parent add backpack_user\n\n");
        guide.append("§eAdvanced Options:\n");
        guide.append("§7• Use contexts for world-specific permissions\n");
        guide.append("§7• Set permission expiry dates\n");
        guide.append("§7• Use meta for custom backpack limits\n\n");
        guide.append("§aFor more help: §7https://luckperms.net/wiki");
        context.getSource().sendFeedback(() -> Text.literal(guide.toString()), false);
        return 1;
    }

    private static int showRecommendedSetup(CommandContext<ServerCommandSource> context) {
        StringBuilder setup = new StringBuilder();
        setup.append("§6=== Recommended Server Setup ===\n\n");
        setup.append("§eFor Public Servers:\n");
        setup.append("§71. All players get basic backpack access\n");
        setup.append("§72. Trusted players can rename and change icons\n");
        setup.append("§73. Moderators can view other players' backpacks\n");
        setup.append("§74. Admins have full access\n\n");
        setup.append("§bCommands for Public Server:\n");
        setup.append("§7/lp group default permission set ").append(LuckPermsManager.USE_PERMISSION).append(" true\n");
        setup.append("§7/lp group default permission set ").append(LuckPermsManager.VIEW_OWN_PERMISSION).append(" true\n");
        setup.append("§7/lp group trusted permission set ").append(LuckPermsManager.RENAME_PERMISSION).append(" true\n");
        setup.append("§7/lp group trusted permission set ").append(LuckPermsManager.CHANGE_ICON_PERMISSION).append(" true\n");
        setup.append("§7/lp group mod permission set ").append(LuckPermsManager.ADMIN_VIEW_PERMISSION).append(" true\n");
        setup.append("§7/lp group admin permission set ").append(LuckPermsManager.ADMIN_PERMISSION).append(" true\n\n");
        setup.append("§eFor Private Servers:\n");
        setup.append("§71. All players get full user permissions\n");
        setup.append("§72. Admins get administrative access\n\n");
        setup.append("§bCommands for Private Server:\n");
        setup.append("§7/lp group default permission set backpack.* true\n");
        setup.append("§7/lp group admin permission set backpack.admin.* true\n\n");
        setup.append("§eFor Creative Servers:\n");
        setup.append("§71. Everyone gets admin permissions for testing\n");
        setup.append("§7/lp group default permission set backpack.admin.* true\n\n");
        setup.append("§cSecurity Notes:\n");
        setup.append("§7• Never give ").append(LuckPermsManager.ADMIN_CONFIG_PERMISSION).append(" to non-admins\n");
        setup.append("§7• ").append(LuckPermsManager.ADMIN_EDIT_PERMISSION).append(" allows editing other players' items\n");
        setup.append("§7• ").append(LuckPermsManager.ADMIN_REMOVE_PERMISSION).append(" can delete backpacks permanently\n\n");
        setup.append("§7Test permissions with: §a/").append(ConfigManager.getConfig().mainCommand).append("-perms test <player> <permission>");
        context.getSource().sendFeedback(() -> Text.literal(setup.toString()), false);
        return 1;
    }

    private static int showPermissionStats(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }
        StringBuilder stats = new StringBuilder();
        stats.append("§6=== Permission System Statistics ===\n\n");
        stats.append("§eSystem Information:\n");
        stats.append("  §7• Permission System: §a").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");
        stats.append("  §7• LuckPerms Available: §a").append(LuckPermsManager.isLuckPermsAvailable() ? "Yes" : "No").append("\n");
        stats.append("  §7• Fallback OP Level: §a").append(ConfigManager.getConfig().adminPermissionLevel).append("\n");
        stats.append("  §7• Language Support: §a").append(LanguageManager.getLanguageInfo()).append("\n\n");
        stats.append("§ePermission Nodes:\n");
        stats.append("  §7• Total Nodes: §a").append(getTotalPermissionNodes()).append("\n");
        stats.append("  §7• User Permissions: §a").append(getUserPermissionCount()).append("\n");
        stats.append("  §7• Admin Permissions: §a").append(getAdminPermissionCount()).append("\n\n");

        if (LuckPermsManager.isLuckPermsAvailable()) {
            stats.append("§aLuckPerms Integration:\n");
            stats.append("  §7• Status: §aActive and functioning\n");
            stats.append("  §7• Permission checks: §aGranular control available\n");
            stats.append("  §7• Group support: §aFull LuckPerms compatibility\n");
            stats.append("  §7• Context support: §aWorld and server contexts\n\n");
        } else {
            stats.append("§cFallback System:\n");
            stats.append("  §7• Status: §cUsing OP-based permissions\n");
            stats.append("  §7• User permissions: §aGranted to all players\n");
            stats.append("  §7• Admin permissions: §cRequire OP level ").append(ConfigManager.getConfig().adminPermissionLevel).append("\n");
            stats.append("  §7• Recommendation: §eInstall LuckPerms for better control\n\n");
        }

        stats.append("§eCommand Usage:\n");
        stats.append("  §7• Main Command: §a/").append(ConfigManager.getConfig().mainCommand).append("\n");
        stats.append("  §7• Player Command: §a/").append(ConfigManager.getConfig().playerCommand).append("\n");
        stats.append("  §7• Permission Command: §a/").append(ConfigManager.getConfig().mainCommand).append("-perms\n");
        stats.append("  §7• Config Command: §a/").append(ConfigManager.getConfig().mainCommand).append("-config\n\n");
        stats.append("§7Use §a/").append(ConfigManager.getConfig().mainCommand).append("-perms guide §7for setup instructions");
        admin.sendMessage(Text.literal(stats.toString()), false);
        return 1;
    }

    // NUEVO: Comando de diagnóstico para problemas de LuckPerms
    private static int showDiagnosticInfo(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("§6=== LuckPerms Diagnostic Information ===\n\n");

        String diagInfo = LuckPermsManager.getDiagnosticInfo();
        diagnostic.append(diagInfo);

        diagnostic.append("\n§eTroubleshooting Steps:\n");

        if (!LuckPermsManager.isLuckPermsAvailable()) {
            diagnostic.append("§c1. LuckPerms Not Detected:\n");
            diagnostic.append("  §7• Download LuckPerms-Fabric-5.4.140.jar\n");
            diagnostic.append("  §7• Place in your server's mods/ folder\n");
            diagnostic.append("  §7• Restart the server completely\n");
            diagnostic.append("  §7• Check server logs for LuckPerms startup messages\n\n");

            diagnostic.append("§c2. If LuckPerms is installed but not detected:\n");
            diagnostic.append("  §7• Check that you're using LuckPerms-Fabric (not Bukkit/Sponge)\n");
            diagnostic.append("  §7• Verify Minecraft version compatibility\n");
            diagnostic.append("  §7• Check for conflicting permission mods\n");
            diagnostic.append("  §7• Run §e/lp info §7to test LuckPerms directly\n\n");

            diagnostic.append("§c3. Common Issues:\n");
            diagnostic.append("  §7• Wrong LuckPerms version for Fabric\n");
            diagnostic.append("  §7• Missing Fabric API dependency\n");
            diagnostic.append("  §7• Server not fully started when checking\n");
            diagnostic.append("  §7• Permissions cached from previous sessions\n\n");
        } else {
            diagnostic.append("§a✓ LuckPerms is working correctly!\n");
            diagnostic.append("§7• All BackpacksMod permissions are available\n");
            diagnostic.append("§7• Use §e/lp §7commands to manage permissions\n");
            diagnostic.append("§7• Use §e/").append(ConfigManager.getConfig().mainCommand).append("-perms guide §7for setup help\n\n");
        }

        diagnostic.append("§eUseful Commands:\n");
        diagnostic.append("  §7• §e/lp info §7- Check LuckPerms status\n");
        diagnostic.append("  §7• §e/").append(ConfigManager.getConfig().mainCommand).append("-perms reload §7- Reload permission system\n");
        diagnostic.append("  §7• §e/").append(ConfigManager.getConfig().mainCommand).append("-perms test <player> <permission> §7- Test permissions\n");
        diagnostic.append("  §7• §e/").append(ConfigManager.getConfig().mainCommand).append("-perms guide §7- Setup instructions\n\n");

        diagnostic.append("§7If problems persist, check:\n");
        diagnostic.append("§7• Server console for error messages\n");
        diagnostic.append("§7• LuckPerms documentation at luckperms.net\n");
        diagnostic.append("§7• BackpacksMod GitHub issues");

        admin.sendMessage(Text.literal(diagnostic.toString()), false);
        return 1;
    }

    private static int showQuickPermissionHelp(CommandContext<ServerCommandSource> context) {
        StringBuilder help = new StringBuilder();
        help.append("§6=== Quick Permission Reference ===\n\n");
        help.append("§eBasic User Commands:\n");
        help.append("§7/").append(ConfigManager.getConfig().playerCommand).append(" §f- Open backpacks (").append(LuckPermsManager.USE_PERMISSION).append(")\n");
        help.append("§7/rename-backpack <id> <name> §f- Rename backpack (").append(LuckPermsManager.RENAME_PERMISSION).append(")\n\n");
        help.append("§cAdmin Commands:\n");
        help.append("§7/").append(ConfigManager.getConfig().mainCommand).append(" give <player> <name> <slots> §f- Give backpack\n");
        help.append("§7/").append(ConfigManager.getConfig().mainCommand).append(" admin view <player> §f- View player's backpacks\n");
        help.append("§7/").append(ConfigManager.getConfig().mainCommand).append(" remove <player> <id> §f- Remove backpack\n\n");
        help.append("§ePermission Management:\n");
        help.append("§7/").append(ConfigManager.getConfig().mainCommand).append("-perms info §f- System information\n");
        help.append("§7/").append(ConfigManager.getConfig().mainCommand).append("-perms check <player> §f- Check player permissions\n");
        help.append("§7/").append(ConfigManager.getConfig().mainCommand).append("-perms list §f- List all permissions\n");
        help.append("§7/").append(ConfigManager.getConfig().mainCommand).append("-perms diagnose §f- Troubleshoot LuckPerms\n\n");

        if (LuckPermsManager.isLuckPermsAvailable()) {
            help.append("§aLuckPerms Quick Commands:\n");
            help.append("§7/lp user <player> permission set ").append(LuckPermsManager.USE_PERMISSION).append(" true\n");
            help.append("§7/lp user <player> permission set ").append(LuckPermsManager.ADMIN_PERMISSION).append(" true\n\n");
        } else {
            help.append("§c⚠ LuckPerms not detected!\n");
            help.append("§7Use §e/").append(ConfigManager.getConfig().mainCommand).append("-perms diagnose §7for troubleshooting\n\n");
        }

        help.append("§7For detailed setup: §a/").append(ConfigManager.getConfig().mainCommand).append("-perms guide");
        context.getSource().sendFeedback(() -> Text.literal(help.toString()), false);
        return 1;
    }

    private static int getTotalPermissionNodes() { return 14; }
    private static int getUserPermissionCount() { return 5; }
    private static int getAdminPermissionCount() { return 9; }
}