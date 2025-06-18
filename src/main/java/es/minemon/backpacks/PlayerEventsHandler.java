package es.minemon.backpacks;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;

public class PlayerEventsHandler {

    // NUEVO: Tracker para evitar múltiples updates VIP por jugador
    private static final Set<UUID> vipUpdateInProgress = ConcurrentHashMap.newKeySet();

    // NUEVO: Tracker para evitar dar mochilas por defecto múltiples veces
    private static final Set<UUID> defaultBackpacksProcessed = ConcurrentHashMap.newKeySet();

    public static void register() {
        // Evento de conexión optimizado con soporte VIP y mochilas por defecto
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID playerId = player.getUuid();

            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500);

                    // Cargar datos del jugador
                    BackpackManager.onPlayerJoin(player);

                    // NUEVO: Dar mochilas por defecto a nuevos jugadores
                    Thread.sleep(1000); // Esperar un poco para que se carguen los datos

                    if (BackpacksMod.getServer() != null && !defaultBackpacksProcessed.contains(playerId)) {
                        defaultBackpacksProcessed.add(playerId);

                        BackpacksMod.getServer().execute(() -> {
                            try {
                                DefaultBackpackManager.giveDefaultBackpacks(player);
                            } catch (Exception e) {
                                BackpacksMod.LOGGER.warn("Error giving default backpacks to player: " + player.getName().getString() + " - " + e.getMessage());
                            }
                        });
                    }

                    // CORREGIDO: Actualizar mochilas VIP solo una vez y con protección
                    Thread.sleep(1500); // Esperar un poco más para que LuckPerms esté completamente cargado

                    if (BackpacksMod.getServer() != null && !vipUpdateInProgress.contains(playerId)) {
                        vipUpdateInProgress.add(playerId);

                        BackpacksMod.getServer().execute(() -> {
                            try {
                                VipBackpackManager.updatePlayerVipBackpacks(player);
                                BackpacksMod.LOGGER.info("VIP backpacks synchronized for joining player: " + player.getName().getString());
                            } catch (Exception e) {
                                BackpacksMod.LOGGER.warn("Error updating VIP backpacks for joining player: " + player.getName().getString() + " - " + e.getMessage());
                                // No relanzar la excepción, solo log
                            } finally {
                                // Remover del set después de un tiempo para permitir futuros updates si es necesario
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        Thread.sleep(10000); // 10 segundos
                                        vipUpdateInProgress.remove(playerId);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        vipUpdateInProgress.remove(playerId);
                                    }
                                });
                            }
                        });
                    }

                } catch (Exception e) {
                    // Error handling sin logging excesivo
                    vipUpdateInProgress.remove(playerId);
                    defaultBackpacksProcessed.remove(playerId);
                }
            });
        });

        // Evento de desconexión optimizado
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID playerId = player.getUuid();

            CompletableFuture.runAsync(() -> {
                try {
                    // Limpiar trackers
                    vipUpdateInProgress.remove(playerId);
                    defaultBackpacksProcessed.remove(playerId);

                    BackpackSyncManager.cleanupDisconnectedPlayer(playerId);
                    BackpackManager.onPlayerLeave(player);
                } catch (Exception e) {
                    // Error handling sin logging excesivo
                }
            });
        });
    }

    /**
     * NUEVO: Método para forzar actualización VIP (solo para uso administrativo)
     */
    public static void forceVipUpdate(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        // Remover de la protección para permitir update forzado
        vipUpdateInProgress.remove(playerId);

        CompletableFuture.runAsync(() -> {
            if (!vipUpdateInProgress.contains(playerId)) {
                vipUpdateInProgress.add(playerId);

                try {
                    VipBackpackManager.updatePlayerVipBackpacks(player);
                    BackpacksMod.LOGGER.info("Forced VIP update for player: " + player.getName().getString());
                } catch (Exception e) {
                    BackpacksMod.LOGGER.error("Error in forced VIP update for player: " + player.getName().getString(), e);
                } finally {
                    vipUpdateInProgress.remove(playerId);
                }
            }
        });
    }

    /**
     * NUEVO: Método para forzar dar mochilas por defecto (para uso administrativo)
     */
    public static void forceGiveDefaultBackpacks(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        // Remover de la protección para permitir dar mochilas forzadamente
        defaultBackpacksProcessed.remove(playerId);

        CompletableFuture.runAsync(() -> {
            if (!defaultBackpacksProcessed.contains(playerId)) {
                defaultBackpacksProcessed.add(playerId);

                try {
                    DefaultBackpackManager.giveDefaultBackpacks(player);
                    BackpacksMod.LOGGER.info("Forced default backpacks given to player: " + player.getName().getString());
                } catch (Exception e) {
                    BackpacksMod.LOGGER.error("Error giving forced default backpacks to player: " + player.getName().getString(), e);
                } finally {
                    // Permitir dar más tarde si es necesario
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(5000); // 5 segundos
                            defaultBackpacksProcessed.remove(playerId);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            defaultBackpacksProcessed.remove(playerId);
                        }
                    });
                }
            }
        });
    }

    /**
     * NUEVO: Verifica si un jugador tiene un update VIP en progreso
     */
    public static boolean isVipUpdateInProgress(UUID playerId) {
        return vipUpdateInProgress.contains(playerId);
    }

    /**
     * NUEVO: Verifica si un jugador ya recibió mochilas por defecto
     */
    public static boolean hasReceivedDefaultBackpacks(UUID playerId) {
        return defaultBackpacksProcessed.contains(playerId);
    }

    /**
     * NUEVO: Limpia el estado de update VIP para un jugador (para casos de error)
     */
    public static void clearVipUpdateState(UUID playerId) {
        vipUpdateInProgress.remove(playerId);
    }

    /**
     * NUEVO: Limpia el estado de mochilas por defecto para un jugador
     */
    public static void clearDefaultBackpackState(UUID playerId) {
        defaultBackpacksProcessed.remove(playerId);
    }

    /**
     * NUEVO: Limpia todos los estados para un jugador
     */
    public static void clearAllPlayerStates(UUID playerId) {
        vipUpdateInProgress.remove(playerId);
        defaultBackpacksProcessed.remove(playerId);
    }

    /**
     * NUEVO: Obtiene estadísticas de los trackers
     */
    public static String getTrackerStats() {
        return String.format("VIP updates in progress: %d, Default backpacks processed: %d",
                vipUpdateInProgress.size(), defaultBackpacksProcessed.size());
    }
}