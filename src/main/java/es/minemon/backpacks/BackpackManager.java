package es.minemon.backpacks;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper optimizado para el MongoBackpackManager
 * Versión 3.1.0 - Completamente internacionalizado
 */
public class BackpackManager {

    /**
     * Obtiene los backpacks de un jugador
     */
    public static MongoBackpackManager.PlayerBackpacks getPlayerBackpacks(UUID playerId) {
        return BackpacksMod.getMongoManager().getPlayerBackpacks(playerId);
    }

    /**
     * Añade una mochila
     */
    public static void addBackpack(UUID playerId, int id, String name, int slots) {
        BackpacksMod.getMongoManager().addBackpack(playerId, id, name, slots);

        if (BackpacksMod.getBackupManager() != null) {
            BackpacksMod.getBackupManager().markPlayerActivity(playerId);
        }
    }

    public static void addBackpack(UUID playerId, int id, String name) {
        int defaultSlots = ConfigManager.getConfig().backpackSlots;
        addBackpack(playerId, id, name, defaultSlots);
    }

    /**
     * Obtiene el siguiente ID disponible
     */
    public static int getNextAvailableId(UUID playerId) {
        MongoBackpackManager.PlayerBackpacks backpacks = getPlayerBackpacks(playerId);
        Map<Integer, MongoBackpackManager.BackpackData> allBackpacks = backpacks.getAllBackpacks();

        int nextId = 0;
        while (allBackpacks.containsKey(nextId)) {
            nextId++;
        }
        return nextId;
    }

    /**
     * Remueve una mochila
     */
    public static void removeBackpack(UUID playerId, int id) {
        BackpacksMod.getMongoManager().removeBackpack(playerId, id);

        if (BackpacksMod.getBackupManager() != null) {
            BackpacksMod.getBackupManager().markPlayerActivity(playerId);
        }
    }

    /**
     * Obtiene una mochila específica
     */
    public static MongoBackpackManager.BackpackData getBackpack(UUID playerId, int id) {
        return BackpacksMod.getMongoManager().getBackpack(playerId, id);
    }

    /**
     * Marca una mochila como dirty
     */
    public static void markBackpackDirty(UUID playerId) {
        BackpacksMod.getMongoManager().markBackpackDirty(playerId);

        if (BackpacksMod.getBackupManager() != null) {
            BackpacksMod.getBackupManager().markPlayerActivity(playerId);
        }
    }

    /**
     * Guarda los datos de un jugador
     */
    public static void savePlayerData(UUID playerId) {
        MongoBackpackManager.PlayerBackpacks backpacks = BackpacksMod.getMongoManager().getPlayerBackpacks(playerId);
        BackpacksMod.getMongoManager().savePlayerBackpacks(playerId, backpacks);
    }

    /**
     * Carga datos de forma asíncrona
     */
    public static CompletableFuture<Void> loadPlayerDataAsync(UUID playerId) {
        return BackpacksMod.getMongoManager().loadPlayerBackpacks(playerId)
                .thenAccept(backpacks -> {
                    // Los datos ya están cargados en el cache
                });
    }

    /**
     * Verifica si hay cambios no guardados
     */
    public static boolean hasUnsavedChanges(UUID playerId) {
        return BackpacksMod.getMongoManager().hasPendingWrites();
    }

    /**
     * Fuerza el guardado
     */
    public static CompletableFuture<Void> forcePlayerSave(UUID playerId) {
        MongoBackpackManager.PlayerBackpacks backpacks = BackpacksMod.getMongoManager().getPlayerBackpacks(playerId);
        return BackpacksMod.getMongoManager().savePlayerBackpacks(playerId, backpacks);
    }

    /**
     * Manejo de conexión de jugador - ACTUALIZADO con mensajes internacionalizados
     */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        // Invalidar cache para datos frescos
        BackpacksMod.getMongoManager().invalidatePlayerCache(playerId);

        // Cargar datos de forma asíncrona
        loadPlayerDataAsync(playerId)
                .thenRun(() -> {
                    MongoBackpackManager.PlayerBackpacks backpacks = getPlayerBackpacks(playerId);
                    int backpackCount = backpacks.getAllBackpacks().size();

                    if (backpackCount > 0) {
                        // ACTUALIZADO: Usar sistema de idiomas
                        LanguageManager.sendMessage(player, "dataLoadedWithCount", backpackCount);
                    }
                })
                .exceptionally(throwable -> {
                    // ACTUALIZADO: Usar sistema de idiomas
                    LanguageManager.sendMessage(player, "errorLoadingRetry");
                    return null;
                });
    }

    /**
     * Manejo de desconexión
     */
    public static void onPlayerLeave(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        try {
            // Guardado asíncrono con timeout
            CompletableFuture<Void> saveTask = forcePlayerSave(playerId);

            // Esperar máximo 3 segundos
            saveTask.orTimeout(3, TimeUnit.SECONDS)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            // Backup de emergencia solo si falla el guardado
                            if (BackpacksMod.getBackupManager() != null) {
                                BackpacksMod.getBackupManager().createManualBackup(
                                        "Player disconnect save failed: " + player.getName().getString());
                            }
                        }
                    });

            // Marcar actividad para backup
            if (BackpacksMod.getBackupManager() != null && ConfigManager.getConfig().backupOnPlayerDisconnect) {
                BackpacksMod.getBackupManager().markPlayerActivity(playerId);
            }

        } catch (Exception e) {
            // Error handling sin logging innecesario
        }
    }

    /**
     * Obtiene estadísticas de jugador
     */
    public static BackpackStats getPlayerStats(UUID playerId) {
        MongoBackpackManager.PlayerBackpacks backpacks = getPlayerBackpacks(playerId);
        return new BackpackStats(backpacks);
    }

    /**
     * Sincroniza datos desde database
     */
    public static CompletableFuture<Void> syncPlayerDataFromDatabase(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                BackpacksMod.getMongoManager().invalidatePlayerCache(playerId);
                BackpacksMod.getMongoManager().loadPlayerBackpacks(playerId).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Verificación de integridad
     */
    public static boolean verifyPlayerDataIntegrity(UUID playerId) {
        try {
            MongoBackpackManager.PlayerBackpacks backpacks = getPlayerBackpacks(playerId);

            for (Map.Entry<Integer, MongoBackpackManager.BackpackData> entry : backpacks.getAllBackpacks().entrySet()) {
                MongoBackpackManager.BackpackData backpack = entry.getValue();

                if (backpack.getName() == null || backpack.getName().isEmpty()) {
                    return false;
                }

                if (backpack.getInventory() == null) {
                    return false;
                }

                if (backpack.getInventory().size() < 9 || backpack.getInventory().size() > 54) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Renombra mochila
     */
    public static boolean renameBackpack(UUID playerId, int backpackId, String newName) {
        try {
            MongoBackpackManager.BackpackData backpack = getBackpack(playerId, backpackId);
            if (backpack == null) {
                return false;
            }

            backpack.setName(newName);
            markBackpackDirty(playerId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cambia icono de mochila
     */
    public static boolean changeBackpackIcon(UUID playerId, int backpackId, ItemStack newIcon) {
        try {
            MongoBackpackManager.BackpackData backpack = getBackpack(playerId, backpackId);
            if (backpack == null) {
                return false;
            }

            backpack.setIcon(newIcon);
            markBackpackDirty(playerId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Actualiza el inventario de una mochila
     */
    public static void updateBackpackInventory(UUID playerId, int backpackId, DefaultedList<ItemStack> newInventory) {
        try {
            MongoBackpackManager.BackpackData backpack = getBackpack(playerId, backpackId);
            if (backpack != null) {
                DefaultedList<ItemStack> backpackInventory = backpack.getInventory();

                for (int i = 0; i < Math.min(newInventory.size(), backpackInventory.size()); i++) {
                    backpackInventory.set(i, newInventory.get(i).copy());
                }

                markBackpackDirty(playerId);
            }
        } catch (Exception e) {
            // Error handling sin logging
        }
    }

    /**
     * Limpia todos los caches
     */
    public static CompletableFuture<Void> clearAllCaches() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (BackpacksMod.getMongoManager() != null) {
                    BackpacksMod.getMongoManager().cleanupInactiveCache();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to clear caches", e);
            }
        });
    }

    public static class BackpackStats {
        private final int totalBackpacks;
        private final int totalItems;
        private final int emptySlots;

        public BackpackStats(MongoBackpackManager.PlayerBackpacks backpacks) {
            this.totalBackpacks = backpacks.getAllBackpacks().size();

            int items = 0;
            int empty = 0;

            for (MongoBackpackManager.BackpackData backpack : backpacks.getAllBackpacks().values()) {
                DefaultedList<ItemStack> inventory = backpack.getInventory();
                for (ItemStack stack : inventory) {
                    if (stack.isEmpty()) {
                        empty++;
                    } else {
                        items++;
                    }
                }
            }

            this.totalItems = items;
            this.emptySlots = empty;
        }

        public int getTotalBackpacks() { return totalBackpacks; }
        public int getTotalItems() { return totalItems; }
        public int getEmptySlots() { return emptySlots; }
        public int getTotalSlots() { return totalItems + emptySlots; }
        public double getUsagePercentage() {
            int total = getTotalSlots();
            return total > 0 ? (double) totalItems / total * 100 : 0;
        }
    }
}