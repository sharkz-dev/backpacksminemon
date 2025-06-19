// CORREGIDO: BackupManager.java
package es.minemon.backpacks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.collection.DefaultedList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BackupManager {
    private final MongoBackpackManager mongoManager;
    private final Gson gson;
    private final Path backupPath;
    private final Path emergencyPath;

    private final AtomicInteger tickCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<UUID, Long> lastPlayerActivity = new ConcurrentHashMap<>();
    private final Set<UUID> playersWithChanges = ConcurrentHashMap.newKeySet();

    // CORREGIDO: Pool de hilos con límites estrictos y timeouts
    private final ExecutorService backupExecutor = new ThreadPoolExecutor(
            1, // Solo un hilo core
            2, // Max 2 hilos
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10), // Cola pequeña
            r -> {
                Thread t = new Thread(r, "Backup-Worker-" + System.nanoTime());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    BackpacksMod.LOGGER.error("Uncaught exception in backup thread: " + thread.getName(), ex);
                });
                return t;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy() // Descartar backups antiguos si está lleno
    );

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean backupInProgress = new AtomicBoolean(false);

    // Timeouts más agresivos
    private static final long BACKUP_TIMEOUT = 30000; // 30 segundos max por backup
    private static final long EMERGENCY_BACKUP_TIMEOUT = 10000; // 10 segundos para emergencia

    public BackupManager(MongoBackpackManager mongoManager) {
        this.mongoManager = mongoManager;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        BackpackConfig config = ConfigManager.getConfig();
        this.backupPath = Paths.get(config.backupDirectory);
        this.emergencyPath = Paths.get(config.emergencyBackupDirectory);

        // Crear directorios si no existen
        try {
            Files.createDirectories(backupPath);
            Files.createDirectories(emergencyPath);
            BackpacksMod.LOGGER.info("Backup system initialized with strict timeouts");
        } catch (IOException e) {
            BackpacksMod.LOGGER.error("Error creating backup directories", e);
        }
    }

    public void tick() {
        if (isShuttingDown.get()) return;

        int currentTick = tickCounter.incrementAndGet();
        int backupInterval = ConfigManager.getBackupIntervalTicks();

        // Backup automático con verificación de estado
        if (currentTick >= backupInterval) {
            tickCounter.set(0);
            if (!backupInProgress.get()) {
                performScheduledBackup();
            }
        }

        // Auto-guardado optimizado menos frecuente
        int autoSaveInterval = ConfigManager.getConfig().autoSaveIntervalSeconds * 20;
        if (currentTick % (autoSaveInterval * 2) == 0) { // Menos frecuente
            saveActivePlayersChanges();
        }

        // Limpieza de tracking cada 5 minutos
        if (currentTick % (20 * 60 * 5) == 0) {
            cleanupOldActivity();
        }
    }

    public void markPlayerActivity(UUID playerId) {
        if (isShuttingDown.get()) return;

        lastPlayerActivity.put(playerId, System.currentTimeMillis());
        playersWithChanges.add(playerId);
    }

    // CORREGIDO: Backup programado con timeout y control de concurrencia
    private void performScheduledBackup() {
        if (!ConfigManager.isFeatureEnabled("backup") || isShuttingDown.get()) {
            return;
        }

        if (!backupInProgress.compareAndSet(false, true)) {
            BackpacksMod.LOGGER.warn("Backup already in progress, skipping scheduled backup");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Guardar datos sucios primero con timeout
                CompletableFuture<Void> saveTask = CompletableFuture.runAsync(() -> {
                    mongoManager.saveAllDirtyBackpacks();
                }, backupExecutor);

                saveTask.get(10, TimeUnit.SECONDS); // Timeout para guardado

                // Crear backup completo con timeout
                CompletableFuture<Void> backupTask = CompletableFuture.runAsync(() -> {
                    try {
                        createFullBackup();
                    } catch (IOException e) {
                        BackpacksMod.LOGGER.error("Error creating full backup", e);
                    }
                }, backupExecutor);

                backupTask.get(BACKUP_TIMEOUT, TimeUnit.MILLISECONDS);

                // Limpiar backups antiguos rápidamente
                cleanOldBackups();

            } catch (TimeoutException e) {
                BackpacksMod.LOGGER.error("Backup timeout - backup may be incomplete");
            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error during scheduled backup", e);
            } finally {
                backupInProgress.set(false);
            }
        }, backupExecutor);
    }

    // CORREGIDO: Guardado más eficiente con límites
    private void saveActivePlayersChanges() {
        if (playersWithChanges.isEmpty() || isShuttingDown.get()) return;

        // Limitar número de jugadores a procesar por vez
        Set<UUID> toSave = new HashSet<>();
        int maxToProcess = Math.min(10, playersWithChanges.size());

        Iterator<UUID> iterator = playersWithChanges.iterator();
        for (int i = 0; i < maxToProcess && iterator.hasNext(); i++) {
            toSave.add(iterator.next());
            iterator.remove();
        }

        if (!toSave.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                try {
                    mongoManager.saveAllDirtyBackpacks();
                } catch (Exception e) {
                    BackpacksMod.LOGGER.warn("Error in incremental save: " + e.getMessage());
                }
            }, backupExecutor).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
                BackpacksMod.LOGGER.warn("Save timeout during incremental save");
                return null;
            });
        }
    }

    // CORREGIDO: Backup de emergencia ultra-rápido
    public void performEmergencyBackup() {
        if (!ConfigManager.getConfig().createEmergencyBackup || isShuttingDown.get()) {
            return;
        }

        try {
            BackpacksMod.LOGGER.info("Creating emergency backup...");

            CompletableFuture<Void> emergencyTask = CompletableFuture.runAsync(() -> {
                try {
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                    String filename = "emergency_backup_" + timestamp + ".json";
                    Path emergencyFile = emergencyPath.resolve(filename);

                    // Solo obtener datos del cache - NO cargar desde MongoDB
                    Map<UUID, MongoBackpackManager.PlayerBackpacks> cachedData = mongoManager.getAllCachedBackpacks();

                    JsonObject emergencyData = new JsonObject();
                    emergencyData.addProperty("timestamp", System.currentTimeMillis());
                    emergencyData.addProperty("server_shutdown", true);
                    emergencyData.addProperty("server_id", ConfigManager.getConfig().serverId);
                    emergencyData.addProperty("total_players", cachedData.size());
                    emergencyData.addProperty("emergency", true);

                    // Serialización mínima para velocidad
                    JsonObject playersData = new JsonObject();
                    for (Map.Entry<UUID, MongoBackpackManager.PlayerBackpacks> entry : cachedData.entrySet()) {
                        try {
                            JsonObject playerData = serializePlayerBackpacksMinimal(entry.getValue());
                            playersData.add(entry.getKey().toString(), playerData);
                        } catch (Exception e) {
                            // Skip problematic players
                        }
                    }

                    emergencyData.add("players", playersData);

                    // Escribir archivo de emergencia
                    try (FileWriter writer = new FileWriter(emergencyFile.toFile())) {
                        gson.toJson(emergencyData, writer);
                    }

                    BackpacksMod.LOGGER.info("Emergency backup saved: " + emergencyFile);

                } catch (Exception e) {
                    BackpacksMod.LOGGER.error("Error during emergency backup", e);
                }
            }, backupExecutor);

            // Timeout muy corto para emergencia
            emergencyTask.get(EMERGENCY_BACKUP_TIMEOUT, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            BackpacksMod.LOGGER.error("Emergency backup timeout - server may lose some data");
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error during emergency backup", e);
        }
    }

    // NUEVO: Serialización mínima para backup de emergencia
    private JsonObject serializePlayerBackpacksMinimal(MongoBackpackManager.PlayerBackpacks playerBackpacks) {
        JsonObject playerData = new JsonObject();
        JsonObject backpacksData = new JsonObject();

        try {
            for (Map.Entry<Integer, MongoBackpackManager.BackpackData> entry : playerBackpacks.getAllBackpacks().entrySet()) {
                JsonObject backpackData = new JsonObject();
                MongoBackpackManager.BackpackData backpack = entry.getValue();

                backpackData.addProperty("name", backpack.getName());
                backpackData.addProperty("slots", backpack.getInventory().size());

                // Solo contar items, no serializar completamente
                int itemCount = 0;
                for (ItemStack stack : backpack.getInventory()) {
                    if (!stack.isEmpty()) {
                        itemCount++;
                    }
                }
                backpackData.addProperty("item_count", itemCount);
                backpackData.addProperty("emergency_backup", true);

                backpacksData.add(String.valueOf(entry.getKey()), backpackData);
            }

            playerData.add("backpacks", backpacksData);
            playerData.addProperty("last_activity", lastPlayerActivity.getOrDefault(UUID.randomUUID(), System.currentTimeMillis()));
            return playerData;
        } catch (Exception e) {
            // Return minimal data
            JsonObject minimal = new JsonObject();
            minimal.addProperty("error", "serialization_failed");
            return minimal;
        }
    }

    // CORREGIDO: Creación de backup completo con timeout
    private void createFullBackup() throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String filename = "backup_" + timestamp + ".json";
        Path backupFile = backupPath.resolve(filename);

        // Solo datos del cache para evitar bloqueos de MongoDB
        Map<UUID, MongoBackpackManager.PlayerBackpacks> allData = mongoManager.getAllCachedBackpacks();

        JsonObject backupData = new JsonObject();
        backupData.addProperty("timestamp", System.currentTimeMillis());
        backupData.addProperty("backup_type", "scheduled");
        backupData.addProperty("server_id", ConfigManager.getConfig().serverId);
        backupData.addProperty("total_players", allData.size());
        backupData.addProperty("config_version", "3.1.0");

        JsonObject playersData = new JsonObject();
        for (Map.Entry<UUID, MongoBackpackManager.PlayerBackpacks> entry : allData.entrySet()) {
            try {
                JsonObject playerData = serializePlayerBackpacks(entry.getValue());
                playersData.add(entry.getKey().toString(), playerData);
            } catch (Exception e) {
                BackpacksMod.LOGGER.warn("Skipping player " + entry.getKey() + " due to serialization error");
                // Skip problematic players instead of failing entire backup
            }
        }

        backupData.add("players", playersData);

        try (FileWriter writer = new FileWriter(backupFile.toFile())) {
            gson.toJson(backupData, writer);
        }

        BackpacksMod.LOGGER.info("Full backup created: " + backupFile);
    }

    // CORREGIDO: Serialización optimizada con timeout
    private JsonObject serializePlayerBackpacks(MongoBackpackManager.PlayerBackpacks playerBackpacks) {
        JsonObject playerData = new JsonObject();
        JsonObject backpacksData = new JsonObject();

        try {
            for (Map.Entry<Integer, MongoBackpackManager.BackpackData> entry : playerBackpacks.getAllBackpacks().entrySet()) {
                try {
                    JsonObject backpackData = new JsonObject();
                    MongoBackpackManager.BackpackData backpack = entry.getValue();

                    backpackData.addProperty("name", backpack.getName());
                    backpackData.addProperty("slots", backpack.getInventory().size());

                    // Serializar solo items no vacíos con limit
                    JsonObject itemsData = new JsonObject();
                    DefaultedList<ItemStack> inventory = backpack.getInventory();
                    int itemsSerialized = 0;
                    int maxItemsToSerialize = 100; // Límite para evitar bloqueos

                    for (int i = 0; i < inventory.size() && itemsSerialized < maxItemsToSerialize; i++) {
                        ItemStack stack = inventory.get(i);
                        if (stack.isEmpty()) continue;

                        JsonObject itemData = new JsonObject();
                        MinecraftServer server = BackpacksMod.getServer();
                        if (server != null) {
                            try {
                                NbtCompound nbt = new NbtCompound();
                                stack.encode(server.getRegistryManager(), nbt);

                                itemData.addProperty("nbt", nbt.toString());
                                itemData.addProperty("slot", i);
                                itemData.addProperty("count", stack.getCount());
                                itemData.addProperty("item_id", stack.getItem().toString());
                                itemsData.add(String.valueOf(i), itemData);
                                itemsSerialized++;
                            } catch (Exception e) {
                                // Skip problematic items
                            }
                        }
                    }

                    backpackData.add("items", itemsData);
                    backpackData.addProperty("items_serialized", itemsSerialized);
                    backpacksData.add(String.valueOf(entry.getKey()), backpackData);

                } catch (Exception e) {
                    BackpacksMod.LOGGER.warn("Error serializing backpack " + entry.getKey());
                    // Skip problematic backpack
                }
            }

            playerData.add("backpacks", backpacksData);
            playerData.addProperty("last_activity", lastPlayerActivity.getOrDefault(UUID.randomUUID(), System.currentTimeMillis()));
            return playerData;

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error serializing player backpacks", e);
            JsonObject errorData = new JsonObject();
            errorData.addProperty("error", "serialization_failed");
            return errorData;
        }
    }

    // CORREGIDO: Limpieza más rápida
    private void cleanOldBackups() {
        try {
            BackpackConfig config = ConfigManager.getConfig();

            File[] backupFiles = backupPath.toFile().listFiles((dir, name) ->
                    name.startsWith("backup_") && name.endsWith(".json"));

            if (backupFiles != null && backupFiles.length > config.maxBackupFiles) {
                Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));

                int toDelete = backupFiles.length - config.maxBackupFiles;
                for (int i = 0; i < toDelete; i++) {
                    if (backupFiles[i].delete()) {
                        BackpacksMod.LOGGER.debug("Deleted old backup: " + backupFiles[i].getName());
                    }
                }
            }

        } catch (Exception e) {
            BackpacksMod.LOGGER.warn("Error cleaning old backups: " + e.getMessage());
        }
    }

    // NUEVO: Limpieza de actividad antigua
    private void cleanupOldActivity() {
        if (isShuttingDown.get()) return;

        try {
            long cutoff = System.currentTimeMillis() - (30 * 60 * 1000); // 30 minutos
            lastPlayerActivity.entrySet().removeIf(entry -> entry.getValue() < cutoff);

            // Limpiar jugadores con cambios que ya no están activos
            playersWithChanges.removeIf(playerId -> {
                Long lastActivity = lastPlayerActivity.get(playerId);
                return lastActivity == null || lastActivity < cutoff;
            });

        } catch (Exception e) {
            BackpacksMod.LOGGER.warn("Error cleaning old activity: " + e.getMessage());
        }
    }

    // CORREGIDO: Backup manual con timeout
    public void createManualBackup(String reason) {
        if (isShuttingDown.get()) {
            BackpacksMod.LOGGER.warn("Cannot create manual backup during shutdown");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                String filename = "manual_backup_" + timestamp + ".json";
                Path backupFile = backupPath.resolve(filename);

                Map<UUID, MongoBackpackManager.PlayerBackpacks> allData = mongoManager.getAllCachedBackpacks();

                JsonObject backupData = new JsonObject();
                backupData.addProperty("timestamp", System.currentTimeMillis());
                backupData.addProperty("backup_type", "manual");
                backupData.addProperty("reason", reason);
                backupData.addProperty("server_id", ConfigManager.getConfig().serverId);
                backupData.addProperty("total_players", allData.size());

                JsonObject playersData = new JsonObject();
                for (Map.Entry<UUID, MongoBackpackManager.PlayerBackpacks> entry : allData.entrySet()) {
                    try {
                        JsonObject playerData = serializePlayerBackpacks(entry.getValue());
                        playersData.add(entry.getKey().toString(), playerData);
                    } catch (Exception e) {
                        // Skip problematic players
                    }
                }

                backupData.add("players", playersData);

                try (FileWriter writer = new FileWriter(backupFile.toFile())) {
                    gson.toJson(backupData, writer);
                }

                BackpacksMod.LOGGER.info("Manual backup created: " + backupFile + " (Reason: " + reason + ")");

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error creating manual backup", e);
            }
        }, backupExecutor).orTimeout(BACKUP_TIMEOUT, TimeUnit.MILLISECONDS).exceptionally(ex -> {
            BackpacksMod.LOGGER.error("Manual backup timeout");
            return null;
        });
    }

    public List<String> getAvailableBackups() {
        List<String> backups = new ArrayList<>();

        try {
            File[] backupFiles = backupPath.toFile().listFiles((dir, name) ->
                    name.endsWith(".json"));

            if (backupFiles != null) {
                Arrays.sort(backupFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                for (File file : backupFiles) {
                    backups.add(file.getName());
                }
            }
        } catch (Exception e) {
            BackpacksMod.LOGGER.warn("Error listing backups: " + e.getMessage());
        }

        return backups;
    }

    // CORREGIDO: Shutdown seguro con timeout
    public void shutdown() {
        BackpacksMod.LOGGER.info("Shutting down BackupManager...");
        isShuttingDown.set(true);

        try {
            // Esperar que termine el backup actual
            long waitStart = System.currentTimeMillis();
            while (backupInProgress.get() && (System.currentTimeMillis() - waitStart) < 5000) {
                Thread.sleep(100);
            }

            // Shutdown executor con timeout
            backupExecutor.shutdown();
            if (!backupExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                List<Runnable> pending = backupExecutor.shutdownNow();
                if (!pending.isEmpty()) {
                    BackpacksMod.LOGGER.warn("Cancelled " + pending.size() + " pending backup tasks");
                }
            }

            // Limpiar datos
            lastPlayerActivity.clear();
            playersWithChanges.clear();

            BackpacksMod.LOGGER.info("BackupManager shut down successfully");

        } catch (InterruptedException e) {
            backupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            BackpacksMod.LOGGER.warn("Interrupted during backup shutdown");
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error during backup shutdown", e);
        }
    }

    // NUEVO: Método para verificar estado del sistema
    public boolean isHealthy() {
        return !isShuttingDown.get() &&
                !backupExecutor.isShutdown() &&
                !backupInProgress.get();
    }

    // NUEVO: Estadísticas del backup manager
    public String getBackupStats() {
        try {
            File[] backupFiles = backupPath.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            int backupCount = backupFiles != null ? backupFiles.length : 0;

            return String.format("Backups: %d, Active players: %d, Pending changes: %d, Status: %s",
                    backupCount,
                    lastPlayerActivity.size(),
                    playersWithChanges.size(),
                    isHealthy() ? "Healthy" : "Issues");
        } catch (Exception e) {
            return "Error getting stats: " + e.getMessage();
        }
    }

    // NUEVO: Forzar limpieza de memoria
    public void forceCleanup() {
        if (!isShuttingDown.get()) {
            cleanupOldActivity();
            cleanOldBackups();

            // Sugerir GC si hay mucha actividad
            if (lastPlayerActivity.size() > 100) {
                System.gc();
            }
        }
    }
}