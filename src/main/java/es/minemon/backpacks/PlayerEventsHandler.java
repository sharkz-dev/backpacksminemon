package es.minemon.backpacks;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;

public class PlayerEventsHandler {

    // Trackers optimizados para evitar procesamiento múltiple
    private static final Set<UUID> vipUpdateInProgress = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> defaultBackpacksProcessed = ConcurrentHashMap.newKeySet();

    public static void register() {
        // Evento de conexión optimizado
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID playerId = player.getUuid();

            CompletableFuture.runAsync(() -> {
                try {
                    // Espera inicial para estabilización
                    Thread.sleep(1000);

                    // Cargar datos del jugador
                    BackpackManager.onPlayerJoin(player);

                    // Mochilas por defecto (solo una vez)
                    if (!defaultBackpacksProcessed.contains(playerId)) {
                        defaultBackpacksProcessed.add(playerId);

                        Thread.sleep(500);
                        if (BackpacksMod.getServer() != null) {
                            BackpacksMod.getServer().execute(() -> {
                                try {
                                    DefaultBackpackManager.giveDefaultBackpacks(player);
                                } catch (Exception e) {
                                    // Error silencioso
                                }
                            });
                        }
                    }

                    // Actualización VIP (solo una vez)
                    if (!vipUpdateInProgress.contains(playerId)) {
                        vipUpdateInProgress.add(playerId);

                        Thread.sleep(1000); // Esperar LuckPerms
                        if (BackpacksMod.getServer() != null) {
                            BackpacksMod.getServer().execute(() -> {
                                try {
                                    VipBackpackManager.updatePlayerVipBackpacks(player);
                                } catch (Exception e) {
                                    // Error silencioso
                                } finally {
                                    // Limpiar después de 30 segundos
                                    CompletableFuture.runAsync(() -> {
                                        try {
                                            Thread.sleep(30000);
                                        } catch (InterruptedException ie) {
                                            Thread.currentThread().interrupt();
                                        } finally {
                                            vipUpdateInProgress.remove(playerId);
                                        }
                                    });
                                }
                            });
                        }
                    }

                } catch (Exception e) {
                    // Limpiar en caso de error
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
                    // Limpiar trackers inmediatamente
                    vipUpdateInProgress.remove(playerId);
                    defaultBackpacksProcessed.remove(playerId);

                    // Limpiar sincronización
                    BackpackSyncManager.cleanupDisconnectedPlayer(playerId);

                    // Guardar datos
                    BackpackManager.onPlayerLeave(player);
                } catch (Exception e) {
                    // Error silencioso
                }
            });
        });
    }

    // Métodos de utilidad simplificados
    public static void forceVipUpdate(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        vipUpdateInProgress.remove(playerId);

        CompletableFuture.runAsync(() -> {
            if (!vipUpdateInProgress.contains(playerId)) {
                vipUpdateInProgress.add(playerId);
                try {
                    VipBackpackManager.updatePlayerVipBackpacks(player);
                } catch (Exception e) {
                    // Error silencioso
                } finally {
                    vipUpdateInProgress.remove(playerId);
                }
            }
        });
    }

    public static void forceGiveDefaultBackpacks(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        defaultBackpacksProcessed.remove(playerId);

        CompletableFuture.runAsync(() -> {
            if (!defaultBackpacksProcessed.contains(playerId)) {
                defaultBackpacksProcessed.add(playerId);
                try {
                    DefaultBackpackManager.giveDefaultBackpacks(player);
                } finally {
                    // Permitir dar más tarde después de 10 segundos
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        } finally {
                            defaultBackpacksProcessed.remove(playerId);
                        }
                    });
                }
            }
        });
    }

    // Métodos de estado simplificados
    public static boolean isVipUpdateInProgress(UUID playerId) {
        return vipUpdateInProgress.contains(playerId);
    }

    public static boolean hasReceivedDefaultBackpacks(UUID playerId) {
        return defaultBackpacksProcessed.contains(playerId);
    }

    public static void clearAllPlayerStates(UUID playerId) {
        vipUpdateInProgress.remove(playerId);
        defaultBackpacksProcessed.remove(playerId);
    }
}