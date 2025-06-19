// CORREGIDO: MongoBackpackManager.java
package es.minemon.backpacks;

import com.mongodb.client.*;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.DataResult;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.collection.DefaultedList;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MongoBackpackManager {
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    // CORREGIDO: Cache thread-safe mejorado
    private final ConcurrentHashMap<UUID, PlayerBackpacks> localCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> pendingWrites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastSyncTime = new ConcurrentHashMap<>();

    // NUEVO: Control de operaciones concurrentes
    private final Semaphore mongoOperationsSemaphore = new Semaphore(10); // Max 10 operaciones simultáneas
    private final AtomicInteger activeOperations = new AtomicInteger(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    // CORREGIDO: Executor con límites estrictos y timeout
    private final ExecutorService mongoExecutor = new ThreadPoolExecutor(
            2, // Core threads
            8, // Max threads
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100), // Límite de cola
            r -> {
                Thread t = new Thread(r, "Mongo-Worker-" + System.nanoTime());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    BackpacksMod.LOGGER.error("Uncaught exception in mongo thread: " + thread.getName(), ex);
                });
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // Si está lleno, ejecutar en hilo llamador
    );

    // Timeouts más agresivos para prevenir bloqueos
    private static final long CACHE_TIMEOUT = 45000; // 45 segundos (reducido)
    private static final long OPERATION_TIMEOUT = 10000; // 10 segundos max por operación
    private static final int MAX_CACHE_SIZE = 500; // Reducido para menos memoria

    public MongoBackpackManager() {
        try {
            BackpackConfig config = ConfigManager.getConfig();

            mongoClient = MongoClients.create(config.mongoConnectionString);
            database = mongoClient.getDatabase(config.databaseName);
            collection = database.getCollection(config.collectionName);

            // Índices básicos
            collection.createIndex(new Document("_id", 1));

            BackpacksMod.LOGGER.info("MongoDB conectado con límites de concurrencia");
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error conectando a MongoDB", e);
            throw new RuntimeException("No se pudo conectar a MongoDB", e);
        }
    }

    // CORREGIDO: Operaciones con timeout y control de concurrencia
    public CompletableFuture<PlayerBackpacks> loadPlayerBackpacks(UUID playerId) {
        if (isShuttingDown.get()) {
            return CompletableFuture.completedFuture(getCachedOrEmpty(playerId));
        }

        return CompletableFuture.supplyAsync(() -> {
            if (!mongoOperationsSemaphore.tryAcquire()) {
                BackpacksMod.LOGGER.warn("Too many concurrent operations, using cache for: " + playerId);
                return getCachedOrEmpty(playerId);
            }

            activeOperations.incrementAndGet();
            try {
                // Verificar cache válido primero
                PlayerBackpacks cached = localCache.get(playerId);
                Long lastSync = lastSyncTime.get(playerId);

                if (cached != null && lastSync != null &&
                        (System.currentTimeMillis() - lastSync) < CACHE_TIMEOUT &&
                        !cached.isDirty()) {
                    return cached;
                }

                // Cargar desde MongoDB con timeout
                Future<PlayerBackpacks> loadTask = mongoExecutor.submit(() -> {
                    try {
                        Bson filter = Filters.eq("_id", playerId.toString());
                        Document doc = collection.find(filter).first();

                        PlayerBackpacks backpacks = new PlayerBackpacks();
                        if (doc != null) {
                            backpacks.fromDocument(doc);
                        }
                        return backpacks;
                    } catch (Exception e) {
                        BackpacksMod.LOGGER.warn("Error loading from MongoDB for " + playerId + ": " + e.getMessage());
                        throw e;
                    }
                });

                PlayerBackpacks result = loadTask.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
                updateCache(playerId, result);
                return result;

            } catch (TimeoutException e) {
                BackpacksMod.LOGGER.error("MongoDB load timeout for " + playerId + ", using cache");
                return getCachedOrEmpty(playerId);
            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error loading from MongoDB for " + playerId + ": " + e.getMessage());
                return getCachedOrEmpty(playerId);
            } finally {
                activeOperations.decrementAndGet();
                mongoOperationsSemaphore.release();
            }
        }, mongoExecutor);
    }

    // CORREGIDO: Guardado con timeout y manejo de errores mejorado
    public CompletableFuture<Void> savePlayerBackpacks(UUID playerId, PlayerBackpacks backpacks) {
        if (isShuttingDown.get()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            if (!mongoOperationsSemaphore.tryAcquire()) {
                BackpacksMod.LOGGER.warn("Too many concurrent save operations, skipping save for: " + playerId);
                return;
            }

            pendingWrites.put(playerId, true);
            activeOperations.incrementAndGet();

            try {
                Future<Void> saveTask = mongoExecutor.submit(() -> {
                    try {
                        Document doc = backpacks.toDocument();
                        doc.put("_id", playerId.toString());

                        Bson filter = Filters.eq("_id", playerId.toString());
                        ReplaceOptions options = new ReplaceOptions().upsert(true);

                        collection.replaceOne(filter, doc, options);
                        return null;
                    } catch (Exception e) {
                        BackpacksMod.LOGGER.error("Error saving to MongoDB for " + playerId + ": " + e.getMessage());
                        throw e;
                    }
                });

                saveTask.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);

                backpacks.markClean();
                backpacks.setLastSync(System.currentTimeMillis());
                updateCache(playerId, backpacks);

            } catch (TimeoutException e) {
                BackpacksMod.LOGGER.error("MongoDB save timeout for " + playerId);
            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error saving to MongoDB for " + playerId + ": " + e.getMessage());
            } finally {
                pendingWrites.remove(playerId);
                activeOperations.decrementAndGet();
                mongoOperationsSemaphore.release();
            }
        }, mongoExecutor);
    }

    private PlayerBackpacks getCachedOrEmpty(UUID playerId) {
        PlayerBackpacks cached = localCache.get(playerId);
        if (cached == null) {
            cached = new PlayerBackpacks();
            updateCache(playerId, cached);
        }
        return cached;
    }

    // CORREGIDO: Guardado masivo con timeout global
    public void saveAllDirtyBackpacks() {
        if (isShuttingDown.get()) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int maxConcurrent = Math.min(5, localCache.size()); // Límite de operaciones concurrentes

        int processed = 0;
        for (Map.Entry<UUID, PlayerBackpacks> entry : localCache.entrySet()) {
            if (entry.getValue().isDirty() && processed < maxConcurrent) {
                futures.add(savePlayerBackpacks(entry.getKey(), entry.getValue()));
                processed++;
            }
        }

        if (!futures.isEmpty()) {
            try {
                // Timeout total más agresivo
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(15, TimeUnit.SECONDS); // Reducido de 30 a 15 segundos
                BackpacksMod.LOGGER.info("Saved " + futures.size() + " dirty backpacks");
            } catch (TimeoutException e) {
                BackpacksMod.LOGGER.error("Global save timeout - some data may not be saved");
            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error in mass save operation", e);
            }
        }
    }

    // CORREGIDO: Actualización de cache con gestión de memoria agresiva
    private void updateCache(UUID playerId, PlayerBackpacks backpacks) {
        // Limpieza preventiva del cache
        if (localCache.size() >= MAX_CACHE_SIZE) {
            cleanupCacheAggressively();
        }

        localCache.put(playerId, backpacks);
        lastSyncTime.put(playerId, System.currentTimeMillis());
    }

    // NUEVO: Limpieza agresiva del cache
    private void cleanupCacheAggressively() {
        try {
            long now = System.currentTimeMillis();
            List<UUID> toRemove = new ArrayList<>();

            // Remover entradas antiguas y limpias
            for (Map.Entry<UUID, Long> entry : lastSyncTime.entrySet()) {
                UUID playerId = entry.getKey();
                long lastSync = entry.getValue();
                PlayerBackpacks backpacks = localCache.get(playerId);

                // Remover si es antiguo Y no está sucio
                if ((now - lastSync) > CACHE_TIMEOUT &&
                        (backpacks == null || !backpacks.isDirty())) {
                    toRemove.add(playerId);
                }
            }

            // Si aún hay demasiados, remover los más antiguos
            if (toRemove.size() < (localCache.size() - MAX_CACHE_SIZE + 10)) {
                List<Map.Entry<UUID, Long>> sortedByAge = new ArrayList<>(lastSyncTime.entrySet());
                sortedByAge.sort(Map.Entry.comparingByValue());

                int additionalToRemove = (localCache.size() - MAX_CACHE_SIZE + 10) - toRemove.size();
                for (int i = 0; i < Math.min(additionalToRemove, sortedByAge.size()); i++) {
                    UUID oldId = sortedByAge.get(i).getKey();
                    PlayerBackpacks backpacks = localCache.get(oldId);
                    if (backpacks == null || !backpacks.isDirty()) {
                        toRemove.add(oldId);
                    }
                }
            }

            // Ejecutar limpieza
            for (UUID playerId : toRemove) {
                localCache.remove(playerId);
                lastSyncTime.remove(playerId);
            }

            if (!toRemove.isEmpty()) {
                BackpacksMod.LOGGER.info("Cleaned " + toRemove.size() + " entries from cache");
            }

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error in aggressive cache cleanup", e);
        }
    }

    // CORREGIDO: Cierre seguro con timeout
    public void close() {
        BackpacksMod.LOGGER.info("Iniciando cierre de MongoDB...");
        isShuttingDown.set(true);

        try {
            // Esperar a que terminen las operaciones activas
            long waitStart = System.currentTimeMillis();
            while (activeOperations.get() > 0 &&
                    (System.currentTimeMillis() - waitStart) < 5000) {
                Thread.sleep(100);
            }

            // Guardado final rápido
            saveAllDirtyBackpacks();

            // Cerrar executor
            mongoExecutor.shutdown();
            if (!mongoExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                mongoExecutor.shutdownNow();
            }

            // Cerrar MongoDB
            mongoClient.close();

            // Limpiar caches
            localCache.clear();
            lastSyncTime.clear();
            pendingWrites.clear();

            BackpacksMod.LOGGER.info("MongoDB cerrado correctamente");
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error cerrando MongoDB", e);
            mongoExecutor.shutdownNow();
        }
    }

    // Resto de métodos sin cambios significativos pero con verificaciones de shutdown
    public PlayerBackpacks getPlayerBackpacks(UUID playerId) {
        if (isShuttingDown.get()) {
            return getCachedOrEmpty(playerId);
        }

        PlayerBackpacks backpacks = localCache.get(playerId);
        Long lastSync = lastSyncTime.get(playerId);

        boolean needsReload = backpacks == null || lastSync == null ||
                (System.currentTimeMillis() - lastSync) > CACHE_TIMEOUT;

        if (needsReload) {
            try {
                backpacks = loadPlayerBackpacks(playerId).get(2, TimeUnit.SECONDS); // Timeout reducido
            } catch (Exception e) {
                if (backpacks == null) {
                    backpacks = new PlayerBackpacks();
                    updateCache(playerId, backpacks);
                }
            }
        }

        return backpacks;
    }

    public boolean hasPendingWrites() {
        return !pendingWrites.isEmpty() || activeOperations.get() > 0;
    }

    public void invalidatePlayerCache(UUID playerId) {
        localCache.remove(playerId);
        lastSyncTime.remove(playerId);
    }

    public void cleanupInactiveCache() {
        if (isShuttingDown.get()) return;

        try {
            cleanupCacheAggressively();
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error in cleanup", e);
        }
    }

    // Métodos de gestión simplificados permanecen igual...
    public void addBackpack(UUID playerId, int id, String name, int slots) {
        if (isShuttingDown.get()) return;

        PlayerBackpacks backpacks = getPlayerBackpacks(playerId);
        try {
            backpacks.addBackpack(id, name, slots);
            savePlayerBackpacks(playerId, backpacks);
        } catch (IllegalStateException e) {
            throw new RuntimeException("Límite de mochilas alcanzado");
        }
    }

    public void removeBackpack(UUID playerId, int id) {
        if (isShuttingDown.get()) return;

        PlayerBackpacks backpacks = getPlayerBackpacks(playerId);
        backpacks.removeBackpack(id);
        savePlayerBackpacks(playerId, backpacks);
    }

    public BackpackData getBackpack(UUID playerId, int id) {
        PlayerBackpacks backpacks = getPlayerBackpacks(playerId);
        return backpacks.getBackpack(id);
    }

    public void markBackpackDirty(UUID playerId) {
        PlayerBackpacks backpacks = localCache.get(playerId);
        if (backpacks != null) {
            backpacks.markDirty();
        }
    }

    public Map<UUID, PlayerBackpacks> getAllCachedBackpacks() {
        return new HashMap<>(localCache);
    }

    // PlayerBackpacks class permanece igual...
    public static class PlayerBackpacks {
        private final Map<Integer, BackpackData> backpacks = new HashMap<>();
        private volatile boolean dirty = false;
        private volatile long lastSync = 0;

        public void addBackpack(int id, String name, int slots) {
            BackpackConfig config = ConfigManager.getConfig();
            if (backpacks.size() >= config.maxBackpacksPerPlayer) {
                throw new IllegalStateException("Límite de mochilas alcanzado");
            }
            backpacks.put(id, new BackpackData(name, slots));
            markDirty();
        }

        public void addBackpack(int id, String name) {
            addBackpack(id, name, ConfigManager.getConfig().backpackSlots);
        }

        public void removeBackpack(int id) {
            backpacks.remove(id);
            markDirty();
        }

        public BackpackData getBackpack(int id) {
            return backpacks.get(id);
        }

        public Map<Integer, BackpackData> getAllBackpacks() {
            return new HashMap<>(backpacks);
        }

        public boolean hasBackpack(int id) {
            return backpacks.containsKey(id);
        }

        public void markDirty() {
            this.dirty = true;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void markClean() {
            this.dirty = false;
        }

        public void setLastSync(long time) {
            this.lastSync = time;
        }

        public long getLastSync() {
            return lastSync;
        }

        // Los métodos de serialización permanecen igual pero con manejo de errores mejorado
        public Document toDocument() {
            try {
                Document doc = new Document();
                List<Document> backpacksList = new ArrayList<>();

                for (Map.Entry<Integer, BackpackData> entry : backpacks.entrySet()) {
                    try {
                        Document backpackDoc = new Document();
                        backpackDoc.put("id", entry.getKey());
                        backpackDoc.put("name", entry.getValue().getName());
                        backpackDoc.put("slots", entry.getValue().getSlots());

                        // Serializar icono de forma más segura
                        ItemStack icon = entry.getValue().getIcon();
                        if (icon != null && !icon.isEmpty()) {
                            MinecraftServer server = BackpacksMod.getServer();
                            if (server != null) {
                                try {
                                    NbtCompound iconNbt = new NbtCompound();
                                    ItemStack.CODEC.encode(icon, server.getRegistryManager().getOps(NbtOps.INSTANCE), new NbtCompound())
                                            .resultOrPartial(error -> {})
                                            .ifPresent(encoded -> backpackDoc.put("icon", encoded.toString()));
                                } catch (Exception iconError) {
                                    // Skip icon if error
                                }
                            }
                        }

                        // Serializar items
                        List<Document> items = new ArrayList<>();
                        DefaultedList<ItemStack> inventory = entry.getValue().getInventory();

                        for (int i = 0; i < inventory.size(); i++) {
                            ItemStack stack = inventory.get(i);
                            if (!stack.isEmpty()) {
                                try {
                                    Document itemDoc = new Document();
                                    itemDoc.put("slot", i);

                                    MinecraftServer server = BackpacksMod.getServer();
                                    if (server != null) {
                                        NbtCompound nbt = new NbtCompound();
                                        ItemStack.CODEC.encode(stack, server.getRegistryManager().getOps(NbtOps.INSTANCE), new NbtCompound())
                                                .resultOrPartial(error -> {})
                                                .ifPresent(encoded -> {
                                                    itemDoc.put("nbt", encoded.toString());
                                                    itemDoc.put("count", stack.getCount());
                                                });
                                    }

                                    if (itemDoc.containsKey("nbt")) {
                                        items.add(itemDoc);
                                    }
                                } catch (Exception itemError) {
                                    // Skip problematic items
                                }
                            }
                        }

                        backpackDoc.put("items", items);
                        backpackDoc.put("lastModified", System.currentTimeMillis());
                        backpacksList.add(backpackDoc);

                    } catch (Exception backpackError) {
                        BackpacksMod.LOGGER.warn("Error serializing backpack " + entry.getKey() + ": " + backpackError.getMessage());
                        // Skip problematic backpack
                    }
                }

                doc.put("backpacks", backpacksList);
                doc.put("lastUpdated", System.currentTimeMillis());
                doc.put("serverId", ConfigManager.getConfig().serverId);

                return doc;
            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error creating document", e);
                return new Document(); // Return empty document as fallback
            }
        }

        // fromDocument method similar improvements...
        public void fromDocument(Document doc) {
            backpacks.clear();

            if (!doc.containsKey("backpacks")) {
                return;
            }

            try {
                List<Document> backpacksList = doc.getList("backpacks", Document.class);
                if (backpacksList == null) return;

                for (Document backpackDoc : backpacksList) {
                    try {
                        Integer id = backpackDoc.getInteger("id");
                        String name = backpackDoc.getString("name");
                        Integer slots = backpackDoc.getInteger("slots", ConfigManager.getConfig().backpackSlots);

                        if (id == null || name == null || slots == null) {
                            continue; // Skip invalid backpack
                        }

                        BackpackData backpack = new BackpackData(name, slots);

                        // Deserializar icono de forma segura
                        if (backpackDoc.containsKey("icon")) {
                            try {
                                String iconNbtString = backpackDoc.getString("icon");
                                if (iconNbtString != null && !iconNbtString.isEmpty()) {
                                    MinecraftServer server = BackpacksMod.getServer();
                                    if (server != null) {
                                        NbtCompound iconNbt = StringNbtReader.parse(iconNbtString);
                                        ItemStack.CODEC.decode(server.getRegistryManager().getOps(NbtOps.INSTANCE), iconNbt)
                                                .resultOrPartial(error -> {})
                                                .ifPresent(pair -> backpack.setIcon(pair.getFirst()));
                                    }
                                }
                            } catch (Exception iconError) {
                                backpack.setIcon(new ItemStack(Items.CHEST));
                            }
                        }

                        // Deserializar items de forma segura
                        if (backpackDoc.containsKey("items")) {
                            List<Document> items = backpackDoc.getList("items", Document.class);
                            if (items != null) {
                                for (Document itemDoc : items) {
                                    try {
                                        Integer slot = itemDoc.getInteger("slot");
                                        String nbtString = itemDoc.getString("nbt");

                                        if (slot != null && nbtString != null && !nbtString.isEmpty() &&
                                                slot >= 0 && slot < backpack.getInventory().size()) {

                                            MinecraftServer server = BackpacksMod.getServer();
                                            if (server != null) {
                                                NbtCompound nbt = StringNbtReader.parse(nbtString);
                                                ItemStack.CODEC.decode(server.getRegistryManager().getOps(NbtOps.INSTANCE), nbt)
                                                        .resultOrPartial(error -> {})
                                                        .ifPresent(pair -> backpack.getInventory().set(slot, pair.getFirst()));
                                            }
                                        }
                                    } catch (Exception itemError) {
                                        // Skip problematic item
                                    }
                                }
                            }
                        }

                        backpacks.put(id, backpack);

                    } catch (Exception backpackError) {
                        BackpacksMod.LOGGER.warn("Error deserializing backpack: " + backpackError.getMessage());
                        // Skip problematic backpack
                    }
                }

                setLastSync(System.currentTimeMillis());
                markClean();

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error processing MongoDB document", e);
            }
        }
    }

    // BackpackData class permanece igual...
    public static class BackpackData {
        private String name;
        private final DefaultedList<ItemStack> inventory;
        private final int slots;
        private ItemStack icon;

        public BackpackData(String name, int slots) {
            this.name = name;
            this.slots = slots;
            this.inventory = DefaultedList.ofSize(slots, ItemStack.EMPTY);
            this.icon = new ItemStack(Items.CHEST);
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public DefaultedList<ItemStack> getInventory() { return inventory; }
        public int getSlots() { return slots; }
        public ItemStack getIcon() { return icon; }
        public void setIcon(ItemStack icon) {
            this.icon = icon != null ? icon.copy() : new ItemStack(Items.CHEST);
        }
    }
}