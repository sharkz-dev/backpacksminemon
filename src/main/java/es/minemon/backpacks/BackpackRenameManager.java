package es.minemon.backpacks;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Maneja el sistema de renombrado optimizado de mochilas
 * Versión 3.1.0 - Completamente internacionalizado
 */
public class BackpackRenameManager {
    private static final ConcurrentHashMap<UUID, RenameData> pendingRenames = new ConcurrentHashMap<>();

    // Scheduler optimizado para limpieza
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Rename-Cleanup");
        t.setDaemon(true);
        return t;
    });

    private static class RenameData {
        final int backpackId;
        final long startTime;

        RenameData(int backpackId) {
            this.backpackId = backpackId;
            this.startTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > 30000; // 30 segundos
        }
    }

    public static void register() {
        // Interceptar mensajes de chat para renombrado
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            UUID playerId = sender.getUuid();

            if (!pendingRenames.containsKey(playerId)) {
                return true; // Permitir mensaje normal
            }

            RenameData renameData = pendingRenames.get(playerId);

            // Verificar expiración
            if (renameData.isExpired()) {
                pendingRenames.remove(playerId);
                // ACTUALIZADO: Usar sistema de idiomas
                LanguageManager.sendMessage(sender, "renameTimeout");
                return false;
            }

            String newName = message.getContent().getString();

            // Validación optimizada - ACTUALIZADA con sistema de idiomas
            if (newName.length() > 50) {
                LanguageManager.sendMessage(sender, "nameTooLong");
                LanguageManager.sendMessage(sender, "typeShorterName");
                return false;
            }

            if (newName.trim().isEmpty()) {
                LanguageManager.sendMessage(sender, "nameCannotBeEmpty");
                LanguageManager.sendMessage(sender, "typeNewName");
                return false;
            }

            // Procesamiento asíncrono del renombrado
            processRename(sender, renameData.backpackId, newName.trim());
            pendingRenames.remove(playerId);

            return false; // Bloquear mensaje de chat
        });

        // Iniciar limpieza automática cada 30 segundos
        scheduler.scheduleAtFixedRate(() -> {
            pendingRenames.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    UUID playerId = entry.getKey();

                    if (BackpacksMod.getServer() != null) {
                        ServerPlayerEntity player = BackpacksMod.getServer().getPlayerManager().getPlayer(playerId);
                        if (player != null) {
                            // ACTUALIZADO: Usar sistema de idiomas
                            LanguageManager.sendMessage(player, "renameTimeout");
                            player.sendMessage(MessageUtils.parseText("<#9a9a9a>Right click on a backpack to try again</>"), false);
                        }
                    }
                    return true;
                }
                return false;
            });
        }, 30, 30, TimeUnit.SECONDS);
    }

    // Procesamiento asíncrono optimizado - ACTUALIZADO
    private static void processRename(ServerPlayerEntity sender, int backpackId, String newName) {
        // Ejecutar en hilo separado para no bloquear
        scheduler.execute(() -> {
            try {
                boolean success = BackpackManager.renameBackpack(sender.getUuid(), backpackId, newName);

                // Volver al hilo principal para interactuar con el jugador
                if (BackpacksMod.getServer() != null) {
                    BackpacksMod.getServer().execute(() -> {
                        if (success) {
                            // ACTUALIZADO: Usar sistema de idiomas
                            LanguageManager.sendMessage(sender, "renameSuccessful", newName);

                            // Guardado y apertura de menú asíncrono
                            BackpackManager.forcePlayerSave(sender.getUuid())
                                    .thenRun(() -> BackpacksMod.getServer().execute(() -> {
                                        try {
                                            BackpackMenuScreenHandler.openBackpackMenu(sender);
                                            // ACTUALIZADO: Usar sistema de idiomas
                                            LanguageManager.sendMessage(sender, "menuUpdated");
                                        } catch (Exception e) {
                                            // ACTUALIZADO: Usar sistema de idiomas
                                            LanguageManager.sendMessage(sender, "useBackpacksCommand");
                                        }
                                    }))
                                    .exceptionally(throwable -> {
                                        BackpacksMod.getServer().execute(() ->
                                                // ACTUALIZADO: Usar sistema de idiomas
                                                LanguageManager.sendMessage(sender, "useBackpacksCommand"));
                                        return null;
                                    });
                        } else {
                            // ACTUALIZADO: Usar sistema de idiomas
                            LanguageManager.sendMessage(sender, "errorRenamingBackpack");
                            LanguageManager.sendMessage(sender, "tryAgainLater");
                        }
                    });
                }
            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error en renombrado asíncrono", e);
                if (BackpacksMod.getServer() != null) {
                    BackpacksMod.getServer().execute(() ->
                            // ACTUALIZADO: Usar sistema de idiomas
                            LanguageManager.sendMessage(sender, "errorRenamingBackpack"));
                }
            }
        });
    }

    public static void startRename(UUID playerId, int backpackId) {
        pendingRenames.put(playerId, new RenameData(backpackId));
    }

    public static void cancelRename(UUID playerId) {
        pendingRenames.remove(playerId);
    }

    public static boolean isRenaming(UUID playerId) {
        RenameData data = pendingRenames.get(playerId);
        if (data != null && data.isExpired()) {
            pendingRenames.remove(playerId);
            return false;
        }
        return data != null;
    }

    public static Integer getRenameBackpackId(UUID playerId) {
        RenameData data = pendingRenames.get(playerId);
        return data != null ? data.backpackId : null;
    }

    // Método para cerrar recursos al apagar el servidor
    public static void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}