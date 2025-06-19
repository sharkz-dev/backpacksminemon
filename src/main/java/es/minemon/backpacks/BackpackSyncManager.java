// CORREGIDO: BackpackSyncManager.java
package es.minemon.backpacks;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CORREGIDO: Sistema de sincronización con límites estrictos y timeouts
 */
public class BackpackSyncManager {

    private static final ConcurrentHashMap<String, Set<BackpackObserver>> activeObservers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> backpackVersions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, BackpackSnapshot> lastSnapshots = new ConcurrentHashMap<>();

    // CORREGIDO: Scheduler con límites estrictos y timeouts
    private static final ScheduledExecutorService syncScheduler = new ScheduledThreadPoolExecutor(
            2, // Core threads reducido
            r -> {
                Thread t = new Thread(r, "BackpackSync-RealTime-" + System.nanoTime());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    BackpacksMod.LOGGER.error("Uncaught exception in sync thread: " + thread.getName(), ex);
                });
                return t;
            }
    );

    private static final ExecutorService syncExecutor = new ThreadPoolExecutor(
            2, // Core threads
            4, // Max threads (reducido)
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50), // Cola limitada
            r -> {
                Thread t = new Thread(r, "BackpackSync-Operations-" + System.nanoTime());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    BackpacksMod.LOGGER.error("Uncaught exception in sync operations: " + thread.getName(), ex);
                });
                return t;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy() // Descartar tareas antiguas si está lleno
    );

    private static final ConcurrentHashMap<String, ScheduledFuture<?>> pendingSyncs = new ConcurrentHashMap<>();
    private static final long SYNC_DEBOUNCE_MS = 100; // Aumentado para reducir carga
    private static final long MAX_SNAPSHOT_AGE = 30000; // 30 segundos max

    private static ScheduledFuture<?> continuousMonitor;
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    public interface BackpackObserver {
        String getObserverId();

        ObserverType getType();

        UUID getPlayerId();

        void onInventoryChanged(DefaultedList<ItemStack> newInventory, long version);

        boolean isValid();
    }

    public enum ObserverType {
        PLAYER,
        ADMIN
    }

    private static class BackpackSnapshot {
        private final DefaultedList<ItemStack> inventory;
        private final long timestamp;
        private final long version;

        public BackpackSnapshot(DefaultedList<ItemStack> inventory, long version) {
            this.inventory = copyInventory(inventory);
            this.timestamp = System.currentTimeMillis();
            this.version = version;
        }

        public boolean isDifferent(DefaultedList<ItemStack> other) {
            if (inventory.size() != other.size()) return true;

            for (int i = 0; i < inventory.size(); i++) {
                if (!ItemStack.areEqual(inventory.get(i), other.get(i))) {
                    return true;
                }
            }
            return false;
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > MAX_SNAPSHOT_AGE;
        }

        public DefaultedList<ItemStack> getInventory() {
            return inventory;
        }

        public long getVersion() {
            return version;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    // CORREGIDO: PlayerObserver con verificaciones de estado mejoradas
    public static class PlayerObserver implements BackpackObserver {
        private final ServerPlayerEntity player;
        private final int backpackId;
        private final String observerId;
        private volatile long lastUpdate = 0;

        public PlayerObserver(ServerPlayerEntity player, int backpackId) {
            this.player = player;
            this.backpackId = backpackId;
            this.observerId = "player_" + player.getUuid() + "_" + backpackId;
        }

        @Override
        public String getObserverId() {
            return observerId;
        }

        @Override
        public ObserverType getType() {
            return ObserverType.PLAYER;
        }

        @Override
        public UUID getPlayerId() {
            return player.getUuid();
        }

        @Override
        public void onInventoryChanged(DefaultedList<ItemStack> newInventory, long version) {
            if (!isValid() || isShuttingDown.get()) return;

            long now = System.currentTimeMillis();
            if (now - lastUpdate < 50) return; // Rate limiting

            lastUpdate = now;

            try {
                if (player.currentScreenHandler instanceof BackpackScreenHandler) {
                    BackpackScreenHandler handler = (BackpackScreenHandler) player.currentScreenHandler;
                    if (handler.getBackpackId() == backpackId) {
                        // Ejecutar en el hilo principal del servidor con timeout
                        CompletableFuture.runAsync(() -> {
                            if (BackpacksMod.getServer() != null && !isShuttingDown.get()) {
                                BackpacksMod.getServer().execute(() -> {
                                    try {
                                        handler.syncInventory(newInventory);
                                    } catch (Exception e) {
                                        BackpacksMod.LOGGER.warn("Error syncing inventory for player " + player.getName().getString());
                                    }
                                });
                            }
                        }).orTimeout(2, TimeUnit.SECONDS).exceptionally(ex -> {
                            BackpacksMod.LOGGER.warn("Sync timeout for player " + player.getName().getString());
                            return null;
                        });
                    }
                }
            } catch (Exception e) {
                BackpacksMod.LOGGER.warn("Error in player inventory sync: " + e.getMessage());
            }
        }

        @Override
        public boolean isValid() {
            return player != null && !player.isDisconnected() &&
                    player.currentScreenHandler instanceof BackpackScreenHandler;
        }
    }

    // CORREGIDO: AdminObserver similar con verificaciones mejoradas
    public static class AdminObserver implements BackpackObserver {
        private final ServerPlayerEntity admin;
        private final UUID targetPlayerId;
        private final int backpackId;
        private final String observerId;
        private volatile long lastUpdate = 0;

        public AdminObserver(ServerPlayerEntity admin, UUID targetPlayerId, int backpackId) {
            this.admin = admin;
            this.targetPlayerId = targetPlayerId;
            this.backpackId = backpackId;
            this.observerId = "admin_" + admin.getUuid() + "_" + targetPlayerId + "_" + backpackId;
        }

        @Override
        public String getObserverId() {
            return observerId;
        }

        @Override
        public ObserverType getType() {
            return ObserverType.ADMIN;
        }

        @Override
        public UUID getPlayerId() {
            return admin.getUuid();
        }

        @Override
        public void onInventoryChanged(DefaultedList<ItemStack> newInventory, long version) {
            if (!isValid() || isShuttingDown.get()) return;

            long now = System.currentTimeMillis();
            if (now - lastUpdate < 50) return; // Rate limiting

            lastUpdate = now;

            try {
                if (admin.currentScreenHandler instanceof BackpackAdminEditScreenHandler) {
                    BackpackAdminEditScreenHandler handler = (BackpackAdminEditScreenHandler) admin.currentScreenHandler;
                    if (handler.getTargetPlayerId().equals(targetPlayerId) && handler.getBackpackId() == backpackId) {
                        CompletableFuture.runAsync(() -> {
                            if (BackpacksMod.getServer() != null && !isShuttingDown.get()) {
                                BackpacksMod.getServer().execute(() -> {
                                    try {
                                        handler.syncInventory(newInventory);
                                    } catch (Exception e) {
                                        BackpacksMod.LOGGER.warn("Error syncing admin inventory");
                                    }
                                });
                            }
                        }).orTimeout(2, TimeUnit.SECONDS).exceptionally(ex -> {
                            BackpacksMod.LOGGER.warn("Admin sync timeout");
                            return null;
                        });
                    }
                }
            } catch (Exception e) {
                BackpacksMod.LOGGER.warn("Error in admin inventory sync: " + e.getMessage());
            }
        }

        @Override
        public boolean isValid() {
            return admin != null && !admin.isDisconnected() &&
                    admin.currentScreenHandler instanceof BackpackAdminEditScreenHandler;
        }
    }

    // CORREGIDO: Registro de observadores con límites
    public static void registerPlayerObserver(ServerPlayerEntity player, int backpackId) {
        if (isShuttingDown.get()) return;

        String key = player.getUuid() + ":" + backpackId;

        // Verificar límite de observadores
        if (activeObservers.size() > 100) {
            BackpacksMod.LOGGER.warn("Too many observers, cleaning up before adding new one");
            cleanupObservers();
        }

        PlayerObserver observer = new PlayerObserver(player, backpackId);
        activeObservers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(observer);

        backpackVersions.computeIfAbsent(key, k -> new AtomicLong(1));
        updateSnapshotIfNeeded(key, player.getUuid(), backpackId);

        // Sincronización inicial más rápida
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50); // Reducido
                performImmediateSync(key, player.getUuid(), backpackId, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, syncExecutor);
    }

    public static void registerAdminObserver(ServerPlayerEntity admin, UUID targetPlayerId, int backpackId) {
        if (isShuttingDown.get()) return;

        String key = targetPlayerId + ":" + backpackId;

        if (activeObservers.size() > 100) {
            cleanupObservers();
        }

        AdminObserver observer = new AdminObserver(admin, targetPlayerId, backpackId);
        activeObservers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(observer);

        backpackVersions.computeIfAbsent(key, k -> new AtomicLong(1));
        updateSnapshotIfNeeded(key, targetPlayerId, backpackId);

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
                performImmediateSync(key, targetPlayerId, backpackId, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, syncExecutor);
    }

    // CORREGIDO: Notificación de cambios con rate limiting
    public static void notifyInventoryChange(UUID targetPlayerId, int backpackId, UUID changedBy) {
        if (isShuttingDown.get()) return;

        String key = targetPlayerId + ":" + backpackId;

        // Cancelar sync pendiente
        ScheduledFuture<?> pendingSync = pendingSyncs.get(key);
        if (pendingSync != null && !pendingSync.isDone()) {
            pendingSync.cancel(false);
        }

        // Programar nuevo sync con debounce
        try {
            ScheduledFuture<?> newSync = syncScheduler.schedule(() -> {
                performImmediateSync(key, targetPlayerId, backpackId, changedBy);
            }, SYNC_DEBOUNCE_MS, TimeUnit.MILLISECONDS);

            pendingSyncs.put(key, newSync);
        } catch (RejectedExecutionException e) {
            BackpacksMod.LOGGER.warn("Sync scheduler overloaded, skipping sync for " + key);
        }
    }

    // CORREGIDO: Sincronización inmediata con timeout y error handling mejorado
    private static void performImmediateSync(String key, UUID targetPlayerId, int backpackId, UUID changedBy) {
        if (isShuttingDown.get()) return;

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayerId, backpackId);
                    if (backpack == null) {
                        return;
                    }

                    DefaultedList<ItemStack> currentInventory = backpack.getInventory();

                    BackpackSnapshot lastSnapshot = lastSnapshots.get(key);
                    if (lastSnapshot != null && !lastSnapshot.isDifferent(currentInventory) && !lastSnapshot.isExpired()) {
                        return; // Sin cambios
                    }

                    long newVersion = backpackVersions.computeIfAbsent(key, k -> new AtomicLong(1)).incrementAndGet();
                    lastSnapshots.put(key, new BackpackSnapshot(currentInventory, newVersion));

                    Set<BackpackObserver> observers = activeObservers.get(key);
                    if (observers == null || observers.isEmpty()) {
                        return;
                    }

                    List<BackpackObserver> validObservers = new ArrayList<>();
                    List<BackpackObserver> invalidObservers = new ArrayList<>();

                    for (BackpackObserver observer : observers) {
                        if (observer.isValid()) {
                            validObservers.add(observer);
                        } else {
                            invalidObservers.add(observer);
                        }
                    }

                    // Limpiar observadores inválidos
                    if (!invalidObservers.isEmpty()) {
                        observers.removeAll(invalidObservers);
                    }

                    // Notificar observadores válidos
                    if (!validObservers.isEmpty()) {
                        DefaultedList<ItemStack> syncInventory = copyInventory(currentInventory);

                        for (BackpackObserver observer : validObservers) {
                            try {
                                observer.onInventoryChanged(syncInventory, newVersion);
                            } catch (Exception e) {
                                BackpacksMod.LOGGER.warn("Error notifying observer " + observer.getObserverId() + ": " + e.getMessage());
                            }
                        }
                    }

                } catch (Exception e) {
                    BackpacksMod.LOGGER.error("Error in immediate sync for " + key + ": " + e.getMessage());
                } finally {
                    pendingSyncs.remove(key);
                }
            }, syncExecutor).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
                BackpacksMod.LOGGER.warn("Sync timeout for " + key);
                pendingSyncs.remove(key);
                return null;
            });

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error starting sync for " + key + ": " + e.getMessage());
            pendingSyncs.remove(key);
        }
    }

    // CORREGIDO: Monitoreo continuo con límites estrictos
    private static void startContinuousMonitoring() {
        if (continuousMonitor != null && !continuousMonitor.isDone()) {
            return;
        }

        continuousMonitor = syncScheduler.scheduleAtFixedRate(() -> {
            if (isShuttingDown.get()) return;

            try {
                // Limitar verificaciones para evitar sobrecarga
                int maxChecks = Math.min(10, activeObservers.size());
                int checked = 0;

                for (Map.Entry<String, Set<BackpackObserver>> entry : activeObservers.entrySet()) {
                    if (checked >= maxChecks || isShuttingDown.get()) break;

                    if (entry.getValue().isEmpty()) continue;

                    String key = entry.getKey();
                    String[] parts = key.split(":");
                    if (parts.length != 2) continue;

                    try {
                        UUID targetPlayerId = UUID.fromString(parts[0]);
                        int backpackId = Integer.parseInt(parts[1]);

                        MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayerId, backpackId);
                        if (backpack != null) {
                            BackpackSnapshot lastSnapshot = lastSnapshots.get(key);
                            if (lastSnapshot == null || lastSnapshot.isDifferent(backpack.getInventory()) || lastSnapshot.isExpired()) {
                                notifyInventoryChange(targetPlayerId, backpackId, null);
                            }
                        }
                        checked++;
                    } catch (Exception e) {
                        // Continuar con la siguiente entrada
                    }
                }

                // Limpieza periódica
                if (Math.random() < 0.1) { // 10% de probabilidad
                    cleanupObservers();
                    cleanupOldSnapshots();
                }

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error in continuous monitoring: " + e.getMessage());
            }
        }, 200, 200, TimeUnit.MILLISECONDS); // Aumentado el intervalo
    }

    // NUEVO: Limpieza de snapshots antiguos
    private static void cleanupOldSnapshots() {
        try {
            Iterator<Map.Entry<String, BackpackSnapshot>> iterator = lastSnapshots.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, BackpackSnapshot> entry = iterator.next();
                if (entry.getValue().isExpired()) {
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            BackpacksMod.LOGGER.warn("Error cleaning up snapshots: " + e.getMessage());
        }
    }

    // CORREGIDO: Limpieza de observadores más agresiva
    private static void cleanupObservers() {
        try {
            for (Iterator<Map.Entry<String, Set<BackpackObserver>>> it = activeObservers.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Set<BackpackObserver>> entry = it.next();
                String key = entry.getKey();
                Set<BackpackObserver> observers = entry.getValue();

                Iterator<BackpackObserver> obsIt = observers.iterator();
                while (obsIt.hasNext()) {
                    BackpackObserver obs = obsIt.next();
                    if (!obs.isValid()) {
                        obsIt.remove();
                    }
                }

                if (observers.isEmpty()) {
                    it.remove();
                    backpackVersions.remove(key);
                    lastSnapshots.remove(key);

                    ScheduledFuture<?> pendingSync = pendingSyncs.remove(key);
                    if (pendingSync != null) {
                        pendingSync.cancel(false);
                    }
                }
            }
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error in cleanup: " + e.getMessage());
        }
    }

    // Resto de métodos con verificaciones de shutdown
    public static void unregisterObserver(String observerId) {
        if (isShuttingDown.get()) return;

        for (Map.Entry<String, Set<BackpackObserver>> entry : activeObservers.entrySet()) {
            String key = entry.getKey();
            Set<BackpackObserver> observers = entry.getValue();

            BackpackObserver toRemove = null;
            for (BackpackObserver obs : observers) {
                if (obs.getObserverId().equals(observerId)) {
                    toRemove = obs;
                    break;
                }
            }

            if (toRemove != null) {
                observers.remove(toRemove);

                if (observers.isEmpty()) {
                    activeObservers.remove(key);
                    backpackVersions.remove(key);
                    lastSnapshots.remove(key);

                    ScheduledFuture<?> pendingSync = pendingSyncs.remove(key);
                    if (pendingSync != null) {
                        pendingSync.cancel(false);
                    }
                }
                break;
            }
        }
    }

    public static void unregisterPlayerObservers(UUID playerId) {
        if (isShuttingDown.get()) return;

        for (Map.Entry<String, Set<BackpackObserver>> entry : activeObservers.entrySet()) {
            entry.getValue().removeIf(obs -> obs.getPlayerId().equals(playerId));

            if (entry.getValue().isEmpty()) {
                String key = entry.getKey();
                activeObservers.remove(key);
                backpackVersions.remove(key);
                lastSnapshots.remove(key);

                ScheduledFuture<?> pendingSync = pendingSyncs.remove(key);
                if (pendingSync != null) {
                    pendingSync.cancel(false);
                }
            }
        }
    }

    private static void updateSnapshotIfNeeded(String key, UUID targetPlayerId, int backpackId) {
        if (!lastSnapshots.containsKey(key) && !isShuttingDown.get()) {
            try {
                MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayerId, backpackId);
                if (backpack != null) {
                    long version = backpackVersions.get(key).get();
                    lastSnapshots.put(key, new BackpackSnapshot(backpack.getInventory(), version));
                }
            } catch (Exception e) {
                BackpacksMod.LOGGER.warn("Error updating snapshot: " + e.getMessage());
            }
        }
    }

    private static DefaultedList<ItemStack> copyInventory(DefaultedList<ItemStack> original) {
        DefaultedList<ItemStack> copy = DefaultedList.ofSize(original.size(), ItemStack.EMPTY);
        for (int i = 0; i < original.size(); i++) {
            copy.set(i, original.get(i).copy());
        }
        return copy;
    }

    // CORREGIDO: Inicialización con verificaciones
    public static void initialize() {
        if (isShuttingDown.get()) return;

        activeObservers.clear();
        backpackVersions.clear();
        lastSnapshots.clear();
        pendingSyncs.clear();

        startContinuousMonitoring();
        BackpacksMod.LOGGER.info("BackpackSyncManager initialized with performance optimizations");
    }

    // CORREGIDO: Shutdown seguro con timeouts
    public static void shutdown() {
        BackpacksMod.LOGGER.info("Shutting down BackpackSyncManager...");
        isShuttingDown.set(true);

        try {
            // Parar monitor continuo
            if (continuousMonitor != null) {
                continuousMonitor.cancel(true);
            }

            // Cancelar syncs pendientes
            for (ScheduledFuture<?> future : pendingSyncs.values()) {
                future.cancel(true);
            }

            // Shutdown schedulers con timeout
            syncScheduler.shutdown();
            syncExecutor.shutdown();

            if (!syncScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                syncScheduler.shutdownNow();
            }

            if (!syncExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                syncExecutor.shutdownNow();
            }

            // Limpiar estructuras
            activeObservers.clear();
            backpackVersions.clear();
            lastSnapshots.clear();
            pendingSyncs.clear();

            BackpacksMod.LOGGER.info("BackpackSyncManager shut down successfully");

        } catch (InterruptedException e) {
            syncScheduler.shutdownNow();
            syncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            BackpacksMod.LOGGER.warn("Interrupted during shutdown");
        }
    }

    public static void cleanupDisconnectedPlayer(UUID playerId) {
        if (!isShuttingDown.get()) {
            unregisterPlayerObservers(playerId);
        }
    }
}