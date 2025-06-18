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
import java.util.Map;

public class VipCommands {

    // Lista de rangos VIP para autocompletado
    private static final List<String> VIP_RANKS = List.of("cristal", "ruby", "esmeralda", "perla", "platino");

    // Proveedor de sugerencias para rangos VIP
    private static final SuggestionProvider<ServerCommandSource> VIP_RANK_SUGGESTION_PROVIDER = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        for (String rank : VIP_RANKS) {
            if (rank.startsWith(remaining)) {
                builder.suggest(rank);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {

        String vipCommand = ConfigManager.getConfig().mainCommand + "-vip";

        dispatcher.register(CommandManager.literal(vipCommand)
                .requires(source -> {
                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) return false;
                    return LuckPermsManager.canModifyConfig(player);
                })

                .then(CommandManager.literal("info")
                        .executes(VipCommands::showVipSystemInfo))

                .then(CommandManager.literal("check")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(VipCommands::checkPlayerVipStatus)))

                .then(CommandManager.literal("sync")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(VipCommands::syncPlayerVip)))

                .then(CommandManager.literal("grant")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("rank", StringArgumentType.string())
                                        .suggests(VIP_RANK_SUGGESTION_PROVIDER)
                                        .executes(VipCommands::grantVipRank))))

                .then(CommandManager.literal("revoke")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("rank", StringArgumentType.string())
                                        .suggests(VIP_RANK_SUGGESTION_PROVIDER)
                                        .executes(VipCommands::revokeVipRank))))

                .then(CommandManager.literal("list")
                        .executes(VipCommands::listVipRanks))

                .then(CommandManager.literal("stats")
                        .executes(VipCommands::showVipStats)));
    }

    private static int showVipSystemInfo(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        StringBuilder info = new StringBuilder();
        info.append("§6=== VIP Backpack System Information ===\n\n");
        info.append("§eVIP Ranks Available:\n");

        // CORREGIDO: Usar getCurrentVipRanks() en lugar de VIP_RANKS estático
        Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
        for (Map.Entry<String, VipBackpackManager.VipRank> entry : currentRanks.entrySet()) {
            VipBackpackManager.VipRank rank = entry.getValue();
            info.append("  §7• §f").append(rank.getDisplayName()).append(" §7(").append(entry.getKey()).append(")\n");
            info.append("    §7Backpacks: §a").append(rank.getBackpackCount()).append("\n");
            info.append("    §7Color: ").append(rank.getPrimaryColor()).append("████ ").append(rank.getSecondaryColor()).append("████§7\n");
        }

        info.append("\n§eSystem Features:\n");
        info.append("  §7• Automatic backpack creation based on permissions\n");
        info.append("  §7• Items preserved when permissions are revoked\n");
        info.append("  §7• Unique IDs for VIP backpacks (100000-999999)\n");
        info.append("  §7• Visual distinction in backpack menu\n");
        info.append("  §7• Real-time permission synchronization\n\n");

        info.append("§eUsage:\n");
        info.append("  §7• Grant VIP permissions through LuckPerms\n");
        info.append("  §7• VIP backpacks appear automatically\n");
        info.append("  §7• Use §e/").append(ConfigManager.getConfig().mainCommand).append("-vip §7for management\n\n");

        if (LuckPermsManager.isLuckPermsAvailable()) {
            info.append("§aLuckPerms Integration: §7Active\n");
            info.append("§7Example: §e/lp user <player> permission set backpack.cristal true");
        } else {
            info.append("§cLuckPerms Integration: §7Not Available\n");
            info.append("§7VIP permissions fallback to OP-level access");
        }

        admin.sendMessage(Text.literal(info.toString()), false);
        return 1;
    }

    private static int checkPlayerVipStatus(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String vipDiagnostic = VipBackpackManager.getVipDiagnosticInfo(targetPlayer);

            admin.sendMessage(Text.literal(""), false);
            admin.sendMessage(Text.literal("§6=== VIP Status for " + targetPlayer.getName().getString() + " ==="), false);

            String[] lines = vipDiagnostic.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    admin.sendMessage(Text.literal("§7" + line), false);
                }
            }

            // Mostrar estadísticas adicionales
            Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(targetPlayer);
            int vipCount = 0;
            int normalCount = 0;

            // CORREGIDO: Usar getCurrentVipRanks() para verificar VIP backpacks
            Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
            for (MongoBackpackManager.BackpackData backpack : visibleBackpacks.values()) {
                String name = backpack.getName();
                boolean isVip = false;
                for (VipBackpackManager.VipRank rank : currentRanks.values()) {
                    if (name.toLowerCase().startsWith(rank.getId().toLowerCase())) {
                        isVip = true;
                        break;
                    }
                }
                if (isVip) {
                    vipCount++;
                } else {
                    normalCount++;
                }
            }

            admin.sendMessage(Text.literal(""), false);
            admin.sendMessage(Text.literal("§eSummary:"), false);
            admin.sendMessage(Text.literal("§7• Normal backpacks: §a" + normalCount), false);
            admin.sendMessage(Text.literal("§7• VIP backpacks: §6" + vipCount), false);
            admin.sendMessage(Text.literal("§7• Total visible: §b" + visibleBackpacks.size()), false);
            admin.sendMessage(Text.literal(""), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError checking VIP status: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int syncPlayerVip(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");

            admin.sendMessage(Text.literal("§eSynchronizing VIP backpacks for " + targetPlayer.getName().getString() + "..."), false);

            // Forzar actualización de mochilas VIP
            VipBackpackManager.updatePlayerVipBackpacks(targetPlayer);

            // Guardar cambios
            BackpackManager.forcePlayerSave(targetPlayer.getUuid())
                    .thenRun(() -> {
                        admin.sendMessage(Text.literal("§aVIP synchronization completed for " + targetPlayer.getName().getString()), false);

                        // Mostrar estado actualizado
                        Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(targetPlayer);
                        int vipCount = 0;

                        // CORREGIDO: Usar getCurrentVipRanks()
                        Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
                        for (MongoBackpackManager.BackpackData backpack : visibleBackpacks.values()) {
                            String name = backpack.getName();
                            for (VipBackpackManager.VipRank rank : currentRanks.values()) {
                                if (name.toLowerCase().startsWith(rank.getId().toLowerCase())) {
                                    vipCount++;
                                    break;
                                }
                            }
                        }
                        admin.sendMessage(Text.literal("§7Player now has §6" + vipCount + " §7VIP backpacks visible"), false);
                    })
                    .exceptionally(throwable -> {
                        admin.sendMessage(Text.literal("§cError during VIP synchronization: " + throwable.getMessage()), false);
                        return null;
                    });

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError synchronizing VIP: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int grantVipRank(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String rankName = StringArgumentType.getString(context, "rank").toLowerCase();

            // Verificar que el rango existe
            String permission = "backpack." + rankName;
            Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
            VipBackpackManager.VipRank rank = currentRanks.get(permission);

            if (rank == null) {
                admin.sendMessage(Text.literal("§cInvalid VIP rank: " + rankName), false);
                admin.sendMessage(Text.literal("§7Available ranks: " + String.join(", ", VIP_RANKS)), false);
                return 0;
            }

            if (!LuckPermsManager.isLuckPermsAvailable()) {
                admin.sendMessage(Text.literal("§cLuckPerms is required for VIP rank management"), false);
                admin.sendMessage(Text.literal("§7Use LuckPerms commands directly: §e/lp user " + targetPlayer.getName().getString() + " permission set " + permission + " true"), false);
                return 0;
            }

            // Verificar si ya tiene el permiso
            if (LuckPermsManager.hasPermission(targetPlayer, permission)) {
                admin.sendMessage(Text.literal("§e" + targetPlayer.getName().getString() + " already has VIP " + rank.getDisplayName() + " rank"), false);
                return 0;
            }

            admin.sendMessage(Text.literal("§6Granting VIP " + rank.getDisplayName() + " to " + targetPlayer.getName().getString() + "..."), false);
            admin.sendMessage(Text.literal("§7Use LuckPerms to grant the permission:"), false);
            admin.sendMessage(Text.literal("§e/lp user " + targetPlayer.getName().getString() + " permission set " + permission + " true"), false);
            admin.sendMessage(Text.literal("§7Then use §e/" + ConfigManager.getConfig().mainCommand + "-vip sync " + targetPlayer.getName().getString() + " §7to apply changes"), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError granting VIP rank: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int revokeVipRank(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
            String rankName = StringArgumentType.getString(context, "rank").toLowerCase();

            // Verificar que el rango existe
            String permission = "backpack." + rankName;
            Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
            VipBackpackManager.VipRank rank = currentRanks.get(permission);

            if (rank == null) {
                admin.sendMessage(Text.literal("§cInvalid VIP rank: " + rankName), false);
                admin.sendMessage(Text.literal("§7Available ranks: " + String.join(", ", VIP_RANKS)), false);
                return 0;
            }

            if (!LuckPermsManager.isLuckPermsAvailable()) {
                admin.sendMessage(Text.literal("§cLuckPerms is required for VIP rank management"), false);
                admin.sendMessage(Text.literal("§7Use LuckPerms commands directly: §e/lp user " + targetPlayer.getName().getString() + " permission unset " + permission), false);
                return 0;
            }

            // Verificar si tiene el permiso
            if (!LuckPermsManager.hasPermission(targetPlayer, permission)) {
                admin.sendMessage(Text.literal("§e" + targetPlayer.getName().getString() + " doesn't have VIP " + rank.getDisplayName() + " rank"), false);
                return 0;
            }

            admin.sendMessage(Text.literal("§6Revoking VIP " + rank.getDisplayName() + " from " + targetPlayer.getName().getString() + "..."), false);
            admin.sendMessage(Text.literal("§7Use LuckPerms to revoke the permission:"), false);
            admin.sendMessage(Text.literal("§e/lp user " + targetPlayer.getName().getString() + " permission unset " + permission), false);
            admin.sendMessage(Text.literal("§7Then use §e/" + ConfigManager.getConfig().mainCommand + "-vip sync " + targetPlayer.getName().getString() + " §7to apply changes"), false);
            admin.sendMessage(Text.literal("§a§lIMPORTANT: §7VIP backpack items will be preserved but hidden"), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError revoking VIP rank: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int listVipRanks(CommandContext<ServerCommandSource> context) {
        StringBuilder message = new StringBuilder();
        message.append("§6=== VIP Backpack Ranks ===\n\n");

        // CORREGIDO: Usar getCurrentVipRanks()
        Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
        for (Map.Entry<String, VipBackpackManager.VipRank> entry : currentRanks.entrySet()) {
            VipBackpackManager.VipRank rank = entry.getValue();
            String permission = entry.getKey();

            message.append("§e").append(rank.getDisplayName()).append(" VIP\n");
            message.append("  §7Permission: §f").append(permission).append("\n");
            message.append("  §7Backpacks: §a").append(rank.getBackpackCount()).append(" exclusive backpacks\n");
            message.append("  §7Colors: ").append(rank.getPrimaryColor()).append("████ ").append(rank.getSecondaryColor()).append("████\n");
            message.append("  §7Grant: §e/lp user <player> permission set ").append(permission).append(" true\n");
            message.append("  §7Revoke: §e/lp user <player> permission unset ").append(permission).append("\n\n");
        }

        message.append("§eFeatures:\n");
        message.append("  §7• VIP backpacks have unique names (Cristal01, Ruby02, etc.)\n");
        message.append("  §7• Items are preserved when permissions are revoked\n");
        message.append("  §7• VIP backpacks appear at the end of the backpack list\n");
        message.append("  §7• Visual distinction with special colors and lore\n\n");

        message.append("§eManagement:\n");
        message.append("  §7• §e/").append(ConfigManager.getConfig().mainCommand).append("-vip check <player> §7- Check VIP status\n");
        message.append("  §7• §e/").append(ConfigManager.getConfig().mainCommand).append("-vip sync <player> §7- Synchronize VIP backpacks\n");
        message.append("  §7• §e/").append(ConfigManager.getConfig().mainCommand).append("-vip stats §7- View VIP statistics");

        context.getSource().sendFeedback(() -> Text.literal(message.toString()), false);
        return 1;
    }

    private static int showVipStats(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            LanguageManager.sendFeedback(context.getSource(), "errorPlayerOnly", false);
            return 0;
        }

        StringBuilder stats = new StringBuilder();
        stats.append("§6=== VIP System Statistics ===\n\n");

        // Estadísticas del sistema
        Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
        stats.append("§eSystem Information:\n");
        stats.append("  §7• Total VIP ranks: §a").append(currentRanks.size()).append("\n");
        stats.append("  §7• LuckPerms integration: §a").append(LuckPermsManager.isLuckPermsAvailable() ? "Active" : "Inactive").append("\n");
        stats.append("  §7• Permission system: §a").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");

        // Estadísticas de rangos
        stats.append("\n§eRank Distribution:\n");
        int totalVipBackpacks = 0;
        for (VipBackpackManager.VipRank rank : currentRanks.values()) {
            totalVipBackpacks += rank.getBackpackCount();
            stats.append("  §7• ").append(rank.getDisplayName()).append(": §6").append(rank.getBackpackCount()).append(" §7backpacks each\n");
        }
        stats.append("  §7• Total possible VIP backpacks per player: §6").append(totalVipBackpacks).append("\n");

        // Información técnica
        stats.append("\n§eTechnical Details:\n");
        stats.append("  §7• VIP backpack ID range: §a100000-999999\n");
        stats.append("  §7• Naming pattern: §aRankName##\n");
        stats.append("  §7• Items preserved on permission loss: §aYes\n");
        stats.append("  §7• Real-time synchronization: §aEnabled\n");

        // Estado del servidor
        if (BackpacksMod.getServer() != null) {
            int onlinePlayers = BackpacksMod.getServer().getCurrentPlayerCount();
            stats.append("\n§eServer Status:\n");
            stats.append("  §7• Online players: §a").append(onlinePlayers).append("\n");
            stats.append("  §7• VIP system: §aOperational\n");
            stats.append("  §7• Database: §a").append(BackpacksMod.getMongoManager() != null ? "Connected" : "Disconnected").append("\n");
        }

        admin.sendMessage(Text.literal(stats.toString()), false);
        return 1;
    }
}