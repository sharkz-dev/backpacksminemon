package es.minemon.backpacks;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sistema de sincronización para mochilas compartidas
 * Versión 3.0.0 - Limpio sin debug ni funcionalidad offline
 */
public class BackpackSyncManager {

    private static final Map<String, Set<BackpackObserver>> activeObservers = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> backpackVersions = new ConcurrentHashMap<>();
    private static final Map<String, BackpackSnapshot> lastSnapshots = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService syncScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "BackpackSync-RealTime");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService syncExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "BackpackSync-Operations");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, ScheduledFuture<?>> pendingSyncs = new ConcurrentHashMap<>();
    private static final long SYNC_DEBOUNCE_MS = 50;

    private static ScheduledFuture<?> continuousMonitor;

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

        public DefaultedList<ItemStack> getInventory() { return inventory; }
        public long getVersion() { return version; }
        public long getTimestamp() { return timestamp; }
    }

    public static class PlayerObserver implements BackpackObserver {
        private final ServerPlayerEntity player;
        private final int backpackId;
        private final String observerId;

        public PlayerObserver(ServerPlayerEntity player, int backpackId) {
            this.player = player;
            this.backpackId = backpackId;
            this.observerId = "player_" + player.getUuid() + "_" + backpackId;
        }

        @Override
        public String getObserverId() { return observerId; }

        @Override
        public ObserverType getType() { return ObserverType.PLAYER; }

        @Override
        public UUID getPlayerId() { return player.getUuid(); }

        @Override
        public void onInventoryChanged(DefaultedList<ItemStack> newInventory, long version) {
            if (!isValid()) return;

            try {
                if (player.currentScreenHandler instanceof BackpackScreenHandler) {
                    BackpackScreenHandler handler = (BackpackScreenHandler) player.currentScreenHandler;
                    if (handler.getBackpackId() == backpackId) {
                        BackpacksMod.getServer().execute(() -> {
                            handler.syncInventory(newInventory);
                        });
                    }
                }
            } catch (Exception e) {
                // Error handling sin logging
            }
        }

        @Override
        public boolean isValid() {
            return player != null && !player.isDisconnected() &&
                    player.currentScreenHandler instanceof BackpackScreenHandler;
        }
    }

    public static class AdminObserver implements BackpackObserver {
        private final ServerPlayerEntity admin;
        private final UUID targetPlayerId;
        private final int backpackId;
        private final String observerId;

        public AdminObserver(ServerPlayerEntity admin, UUID targetPlayerId, int backpackId) {
            this.admin = admin;
            this.targetPlayerId = targetPlayerId;
            this.backpackId = backpackId;
            this.observerId = "admin_" + admin.getUuid() + "_" + targetPlayerId + "_" + backpackId;
        }

        @Override
        public String getObserverId() { return observerId; }

        @Override
        public ObserverType getType() { return ObserverType.ADMIN; }

        @Override
        public UUID getPlayerId() { return admin.getUuid(); }

        @Override
        public void onInventoryChanged(DefaultedList<ItemStack> newInventory, long version) {
            if (!isValid()) return;

            try {
                if (admin.currentScreenHandler instanceof BackpackAdminEditScreenHandler) {
                    BackpackAdminEditScreenHandler handler = (BackpackAdminEditScreenHandler) admin.currentScreenHandler;
                    if (handler.getTargetPlayerId().equals(targetPlayerId) && handler.getBackpackId() == backpackId) {
                        BackpacksMod.getServer().execute(() -> {
                            handler.syncInventory(newInventory);
                        });
                    }
                }
            } catch (Exception e) {
                // Error handling sin logging
            }
        }

        @Override
        public boolean isValid() {
            return admin != null && !admin.isDisconnected() &&
                    admin.currentScreenHandler instanceof BackpackAdminEditScreenHandler;
        }
    }

    public static void registerPlayerObserver(ServerPlayerEntity player, int backpackId) {
        String key = player.getUuid() + ":" + backpackId;
        PlayerObserver observer = new PlayerObserver(player, backpackId);

        activeObservers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(observer);

        backpackVersions.computeIfAbsent(key, k -> new AtomicLong(1));
        updateSnapshotIfNeeded(key, player.getUuid(), backpackId);

        // Forzar sincronización si hay otros observadores
        syncExecutor.execute(() -> {
            try {
                Thread.sleep(100);
                performImmediateSync(key, player.getUuid(), backpackId, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void registerAdminObserver(ServerPlayerEntity admin, UUID targetPlayerId, int backpackId) {
        String key = targetPlayerId + ":" + backpackId;
        AdminObserver observer = new AdminObserver(admin, targetPlayerId, backpackId);

        activeObservers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(observer);

        backpackVersions.computeIfAbsent(key, k -> new AtomicLong(1));
        updateSnapshotIfNeeded(key, targetPlayerId, backpackId);

        // Forzar sincronización si hay otros observadores
        syncExecutor.execute(() -> {
            try {
                Thread.sleep(100);
                performImmediateSync(key, targetPlayerId, backpackId, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void unregisterObserver(String observerId) {
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
        for (Map.Entry<String, Set<BackpackObserver>> entry : activeObservers.entrySet()) {
            entry.getValue().removeIf(obs -> obs.getPlayerId().equals(playerId));

            if (entry.getValue().isEmpty()) {
                activeObservers.remove(entry.getKey());

                String key = entry.getKey();
                backpackVersions.remove(key);
                lastSnapshots.remove(key);

                ScheduledFuture<?> pendingSync = pendingSyncs.remove(key);
                if (pendingSync != null) {
                    pendingSync.cancel(false);
                }
            }
        }
    }

    public static void notifyInventoryChange(UUID targetPlayerId, int backpackId, UUID changedBy) {
        String key = targetPlayerId + ":" + backpackId;

        ScheduledFuture<?> pendingSync = pendingSyncs.get(key);
        if (pendingSync != null && !pendingSync.isDone()) {
            pendingSync.cancel(false);
        }

        ScheduledFuture<?> newSync = syncScheduler.schedule(() -> {
            performImmediateSync(key, targetPlayerId, backpackId, changedBy);
        }, SYNC_DEBOUNCE_MS, TimeUnit.MILLISECONDS);

        pendingSyncs.put(key, newSync);
    }

    private static void performImmediateSync(String key, UUID targetPlayerId, int backpackId, UUID changedBy) {
        syncExecutor.execute(() -> {
            try {
                MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayerId, backpackId);
                if (backpack == null) {
                    return;
                }

                DefaultedList<ItemStack> currentInventory = backpack.getInventory();

                BackpackSnapshot lastSnapshot = lastSnapshots.get(key);
                if (lastSnapshot != null && !lastSnapshot.isDifferent(currentInventory)) {
                    return;
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

                if (!invalidObservers.isEmpty()) {
                    observers.removeAll(invalidObservers);
                }

                if (!validObservers.isEmpty()) {
                    DefaultedList<ItemStack> syncInventory = copyInventory(currentInventory);

                    for (BackpackObserver observer : validObservers) {
                        try {
                            observer.onInventoryChanged(syncInventory, newVersion);
                        } catch (Exception e) {
                            // Error handling sin logging
                        }
                    }
                }

            } catch (Exception e) {
                // Error handling sin logging
            } finally {
                pendingSyncs.remove(key);
            }
        });
    }

    private static void updateSnapshotIfNeeded(String key, UUID targetPlayerId, int backpackId) {
        if (!lastSnapshots.containsKey(key)) {
            try {
                MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayerId, backpackId);
                if (backpack != null) {
                    long version = backpackVersions.get(key).get();
                    lastSnapshots.put(key, new BackpackSnapshot(backpack.getInventory(), version));
                }
            } catch (Exception e) {
                // Error handling sin logging
            }
        }
    }

    private static void startContinuousMonitoring() {
        if (continuousMonitor != null && !continuousMonitor.isDone()) {
            return;
        }

        continuousMonitor = syncScheduler.scheduleAtFixedRate(() -> {
            try {
                for (Map.Entry<String, Set<BackpackObserver>> entry : activeObservers.entrySet()) {
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
                            if (lastSnapshot == null || lastSnapshot.isDifferent(backpack.getInventory())) {
                                notifyInventoryChange(targetPlayerId, backpackId, null);
                            }
                        }
                    } catch (Exception e) {
                        // Continuar con la siguiente entrada
                    }
                }

                cleanupObservers();

            } catch (Exception e) {
                // Error handling sin logging
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    private static void cleanupObservers() {
        for (Iterator<Map.Entry<String, Set<BackpackObserver>>> it = activeObservers.entrySet().iterator(); it.hasNext();) {
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
    }

    private static DefaultedList<ItemStack> copyInventory(DefaultedList<ItemStack> original) {
        DefaultedList<ItemStack> copy = DefaultedList.ofSize(original.size(), ItemStack.EMPTY);
        for (int i = 0; i < original.size(); i++) {
            copy.set(i, original.get(i).copy());
        }
        return copy;
    }

    public static void initialize() {
        activeObservers.clear();
        backpackVersions.clear();
        lastSnapshots.clear();
        pendingSyncs.clear();

        startContinuousMonitoring();
    }

    public static void shutdown() {
        try {
            if (continuousMonitor != null) {
                continuousMonitor.cancel(true);
            }

            for (ScheduledFuture<?> future : pendingSyncs.values()) {
                future.cancel(true);
            }

            syncScheduler.shutdown();
            syncExecutor.shutdown();

            if (!syncScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                syncScheduler.shutdownNow();
            }

            if (!syncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                syncExecutor.shutdownNow();
            }

            activeObservers.clear();
            backpackVersions.clear();
            lastSnapshots.clear();
            pendingSyncs.clear();

        } catch (InterruptedException e) {
            syncScheduler.shutdownNow();
            syncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void cleanupDisconnectedPlayer(UUID playerId) {
        unregisterPlayerObservers(playerId);
    }
}