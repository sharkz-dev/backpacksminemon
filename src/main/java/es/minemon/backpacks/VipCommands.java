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

    private static final List<String> VIP_RANKS = List.of("cristal", "ruby", "esmeralda", "perla", "platino");

    private static final SuggestionProvider<ServerCommandSource> VIP_RANK_SUGGESTIONS = (context, builder) -> {
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

                .then(CommandManager.literal("check")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(VipCommands::checkPlayerVipStatus)))

                .then(CommandManager.literal("sync")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(VipCommands::syncPlayerVip)))

                .then(CommandManager.literal("info")
                        .then(CommandManager.argument("rank", StringArgumentType.string())
                                .suggests(VIP_RANK_SUGGESTIONS)
                                .executes(VipCommands::showRankInfo))));
    }

    private static int checkPlayerVipStatus(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");

            admin.sendMessage(Text.literal("§6=== Estado VIP de " + targetPlayer.getName().getString() + " ==="), false);

            Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
            Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(targetPlayer);

            int vipCount = 0;
            int normalCount = 0;

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

            // Mostrar permisos VIP
            for (Map.Entry<String, VipBackpackManager.VipRank> entry : currentRanks.entrySet()) {
                String permission = entry.getKey();
                VipBackpackManager.VipRank rank = entry.getValue();
                boolean hasPermission = LuckPermsManager.hasPermission(targetPlayer, permission);

                String status = hasPermission ? "§a✓ ACTIVO" : "§c✗ INACTIVO";
                admin.sendMessage(Text.literal("§7" + rank.getDisplayName() + ": " + status), false);
            }

            admin.sendMessage(Text.literal(""), false);
            admin.sendMessage(Text.literal("§eResumen:"), false);
            admin.sendMessage(Text.literal("§7• Mochilas normales: §a" + normalCount), false);
            admin.sendMessage(Text.literal("§7• Mochilas VIP: §6" + vipCount), false);
            admin.sendMessage(Text.literal("§7• Total visibles: §b" + visibleBackpacks.size()), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int syncPlayerVip(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity admin)) {
            return 0;
        }

        try {
            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");

            admin.sendMessage(Text.literal("§eSincronizando VIP para " + targetPlayer.getName().getString() + "..."), false);

            VipBackpackManager.updatePlayerVipBackpacks(targetPlayer);

            BackpackManager.forcePlayerSave(targetPlayer.getUuid())
                    .thenRun(() -> {
                        admin.sendMessage(Text.literal("§aSincronización VIP completada"), false);

                        Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(targetPlayer);
                        int vipCount = 0;

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
                        admin.sendMessage(Text.literal("§7Mochilas VIP visibles: §6" + vipCount), false);
                    })
                    .exceptionally(throwable -> {
                        admin.sendMessage(Text.literal("§cError en sincronización: " + throwable.getMessage()), false);
                        return null;
                    });

            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }

    private static int showRankInfo(CommandContext<ServerCommandSource> context) {
        try {
            String rankName = StringArgumentType.getString(context, "rank").toLowerCase();
            String permission = "backpack." + rankName;

            Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
            VipBackpackManager.VipRank rank = currentRanks.get(permission);

            if (rank == null) {
                context.getSource().sendFeedback(() ->
                                Text.literal("§cRango VIP desconocido: " + rankName),
                        false);
                return 0;
            }

            StringBuilder info = new StringBuilder();
            info.append("§6=== VIP ").append(rank.getDisplayName()).append(" ===\n");
            info.append("§ePermiso: §f").append(permission).append("\n");
            info.append("§eMochilas: §a").append(rank.getBackpackCount()).append("\n");
            info.append("§eSlots cada una: §a").append(rank.getSlotsPerBackpack()).append("\n");
            info.append("§ePatrón de nombre: §f").append(rank.getNamePattern()).append("\n");
            info.append("§eIcono por defecto: §f").append(rank.getDefaultIcon().getItem().toString()).append("\n");
            info.append("§eColores: ").append(rank.getPrimaryColor()).append(" / ").append(rank.getSecondaryColor()).append("\n\n");

            if (LuckPermsManager.isLuckPermsAvailable()) {
                info.append("§aOtorgar: §e/lp user <jugador> permission set ").append(permission).append(" true\n");
                info.append("§cRevocar: §e/lp user <jugador> permission unset ").append(permission);
            } else {
                info.append("§cLuckPerms requerido para gestión automática");
            }

            context.getSource().sendFeedback(() -> Text.literal(info.toString()), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() ->
                            Text.literal("§cError: " + e.getMessage()),
                    false);
            return 0;
        }
    }
}