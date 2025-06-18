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

    private static final List<String> CORE_PERMISSIONS = List.of(
            LuckPermsManager.USE_PERMISSION,
            LuckPermsManager.RENAME_PERMISSION,
            LuckPermsManager.CHANGE_ICON_PERMISSION,
            LuckPermsManager.ADMIN_PERMISSION,
            LuckPermsManager.ADMIN_VIEW_PERMISSION,
            LuckPermsManager.ADMIN_EDIT_PERMISSION,
            LuckPermsManager.VIP_CRISTAL_PERMISSION,
            LuckPermsManager.VIP_RUBY_PERMISSION,
            LuckPermsManager.VIP_ESMERALDA_PERMISSION
    );

    private static final SuggestionProvider<ServerCommandSource> PERMISSION_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        for (String permission : CORE_PERMISSIONS) {
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

                .then(CommandManager.literal("check")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(PermissionCommands::checkPlayerPermissions)))

                .then(CommandManager.literal("test")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("permission", StringArgumentType.string())
                                        .suggests(PERMISSION_SUGGESTIONS)
                                        .executes(PermissionCommands::testPermission)))));
    }

    private static int showPermissionInfo(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            return 0;
        }

        StringBuilder message = new StringBuilder();
        message.append("§6=== Sistema de Permisos ===\n");
        message.append("§eTipo: §a").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");
        message.append("§eLuckPerms: §a").append(LuckPermsManager.isLuckPermsAvailable() ? "Activo" : "No disponible").append("\n\n");

        message.append("§ePermisos de Usuario:\n");
        message.append("§7  • ").append(LuckPermsManager.USE_PERMISSION).append(" - Usar mochilas\n");
        message.append("§7  • ").append(LuckPermsManager.RENAME_PERMISSION).append(" - Renombrar\n");
        message.append("§7  • ").append(LuckPermsManager.CHANGE_ICON_PERMISSION).append(" - Cambiar iconos\n\n");

        message.append("§ePermisos de Admin:\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_PERMISSION).append(" - Admin principal\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_VIEW_PERMISSION).append(" - Ver mochilas ajenas\n");
        message.append("§7  • ").append(LuckPermsManager.ADMIN_EDIT_PERMISSION).append(" - Editar mochilas ajenas\n\n");

        message.append("§ePermisos VIP:\n");
        message.append("§7  • ").append(LuckPermsManager.VIP_CRISTAL_PERMISSION).append(" - VIP Cristal\n");
        message.append("§7  • ").append(LuckPermsManager.VIP_RUBY_PERMISSION).append(" - VIP Ruby\n");
        message.append("§7  • ").append(LuckPermsManager.VIP_ESMERALDA_PERMISSION).append(" - VIP Esmeralda\n\n");

        if (LuckPermsManager.isLuckPermsAvailable()) {
            message.append("§aUsar: §7/lp user <jugador> permission set <permiso> true");
        } else {
            message.append("§cLuckPerms no disponible - usando sistema OP");
        }

        admin.sendMessage(Text.literal(message.toString()), false);
        return 1;
    }

    private static int checkPlayerPermissions(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");

            admin.sendMessage(Text.literal("§6=== Permisos de " + targetPlayer.getName().getString() + " ==="), false);

            // Verificar permisos principales
            checkAndShowPermission(admin, targetPlayer, LuckPermsManager.USE_PERMISSION, "Usar mochilas");
            checkAndShowPermission(admin, targetPlayer, LuckPermsManager.RENAME_PERMISSION, "Renombrar");
            checkAndShowPermission(admin, targetPlayer, LuckPermsManager.ADMIN_PERMISSION, "Administrador");

            // Verificar VIP
            if (LuckPermsManager.hasVipCristal(targetPlayer)) {
                admin.sendMessage(Text.literal("  §a✓ §7VIP Cristal"), false);
            }
            if (LuckPermsManager.hasVipRuby(targetPlayer)) {
                admin.sendMessage(Text.literal("  §a✓ §7VIP Ruby"), false);
            }
            if (LuckPermsManager.hasVipEsmeralda(targetPlayer)) {
                admin.sendMessage(Text.literal("  §a✓ §7VIP Esmeralda"), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static void checkAndShowPermission(ServerPlayerEntity admin, ServerPlayerEntity target, String permission, String description) {
        boolean hasPermission = LuckPermsManager.hasPermission(target, permission);
        String status = hasPermission ? "§a✓" : "§c✗";
        admin.sendMessage(Text.literal("  " + status + " §7" + description), false);
    }

    private static int testPermission(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String permission = StringArgumentType.getString(context, "permission");
            boolean hasPermission = LuckPermsManager.hasPermission(targetPlayer, permission);

            String result = hasPermission ? "§aTIENE" : "§cNO TIENE";
            String message = String.format("§7%s %s §7el permiso §e%s",
                    targetPlayer.getName().getString(), result, permission);

            context.getSource().sendFeedback(() -> Text.literal(message), false);
            return hasPermission ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }
}