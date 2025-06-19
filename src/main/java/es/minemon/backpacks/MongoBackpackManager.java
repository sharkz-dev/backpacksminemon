// OPTIMIZADO: MongoBackpackManager.java con timeouts más flexibles
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

    // Cache thread-safe mejorado
    private final ConcurrentHashMap<UUID, PlayerBackpacks> localCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> pendingWrites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastSyncTime = new ConcurrentHashMap<>();

    // Control de operaciones concurrentes más permisivo
    private final Semaphore mongoOperationsSemaphore = new Semaphore(20); // Aumentado a 20
    private final AtomicInteger activeOperations = new AtomicInteger(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    // Executor con más capacidad
    private final ExecutorService mongoExecutor = new ThreadPoolExecutor(
            4, // Más core threads
            12, // Más max threads
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200), // Cola más grande
            r -> {
                Thread t = new Thread(r, "Mongo-Worker-" + System.nanoTime());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    BackpacksMod.LOGGER.error("Uncaught exception in mongo thread: " + thread.getName(), ex);
                });
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // Timeouts más permisivos para evitar fallos
    private static final long CACHE_TIMEOUT = 120000; // 2 minutos (aumentado)
    private static final long OPERATION_TIMEOUT = 30000; // 30 segundos (aumentado)
    private static final int MAX_CACHE_SIZE = 1000; // Aumentado

    public MongoBackpackManager() {
        try {
            BackpackConfig config = ConfigManager.getConfig();

            mongoClient = MongoClients.create(config.mongoConnectionString);
            database = mongoClient.getDatabase(config.databaseName);
            collection = database.getCollection(config.collectionName);

            // Índices básicos
            collection.createIndex(new Document("_id", 1));

            BackpacksMod.LOGGER.info("MongoDB conectado con timeouts optimizados");
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error conectando a MongoDB", e);
            throw new RuntimeException("No se pudo conectar a MongoDB", e);
        }
    }

    // CORREGIDO: Operaciones con timeouts más permisivos y mejor fallback
    public CompletableFuture<PlayerBackpacks> loadPlayerBackpacks(UUID playerId) {
        if (isShuttingDown.get()) {
            return CompletableFuture.completedFuture(getCachedOrEmpty(playerId));
        }

        return CompletableFuture.supplyAsync(() -> {
            // Intentar obtener semáforo, pero si no se puede, usar cache
            boolean acquired = false;
            try {
                acquired = mongoOperationsSemaphore.tryAcquire(5, TimeUnit.SECONDS);
                if (!acquired) {
                    BackpacksMod.LOGGER.warn("MongoDB busy, using cache for player: " + playerId);
                    return getCachedOrEmpty(playerId);
                }

                activeOperations.incrementAndGet();

                // Verificar cache válido primero
                PlayerBackpacks cached = localCache.get(playerId);
                Long lastSync = lastSyncTime.get(playerId);

                if (cached != null && lastSync != null &&
                        (System.currentTimeMillis() - lastSync) < CACHE_TIMEOUT &&
                        !cached.isDirty()) {
                    return cached;
                }

                // Cargar desde MongoDB con timeout más permisivo
                CompletableFuture<PlayerBackpacks> loadTask = CompletableFuture.supplyAsync(() -> {
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
                }, mongoExecutor);

                try {
                    PlayerBackpacks result = loadTask.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
                    updateCache(playerId, result);
                    return result;
                } catch (TimeoutException e) {
                    BackpacksMod.LOGGER.warn("MongoDB load timeout for " + playerId + ", using cache");
                    return getCachedOrEmpty(playerId);
                } catch (Exception e) {
                    BackpacksMod.LOGGER.warn("Error loading from MongoDB for " + playerId + ": " + e.getMessage());
                    return getCachedOrEmpty(playerId);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                BackpacksMod.LOGGER.warn("Interrupted while waiting for MongoDB semaphore");
                return getCachedOrEmpty(playerId);
            } finally {
                if (acquired) {
                    activeOperations.decrementAndGet();
                    mongoOperationsSemaphore.release();
                }
            }
        }, mongoExecutor);
    }

    // CORREGIDO: Guardado más tolerante a fallos
    public CompletableFuture<Void> savePlayerBackpacks(UUID playerId, PlayerBackpacks backpacks) {
        if (isShuttingDown.get()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            boolean acquired = false;
            try {
                acquired = mongoOperationsSemaphore.tryAcquire(10, TimeUnit.SECONDS);
                if (!acquired) {
                    BackpacksMod.LOGGER.warn("MongoDB busy, skipping save for: " + playerId);
                    return;
                }

                pendingWrites.put(playerId, true);
                activeOperations.incrementAndGet();

                CompletableFuture<Void> saveTask = CompletableFuture.runAsync(() -> {
                    try {
                        Document doc = backpacks.toDocument();
                        doc.put("_id", playerId.toString());

                        Bson filter = Filters.eq("_id", playerId.toString());
                        ReplaceOptions options = new ReplaceOptions().upsert(true);

                        collection.replaceOne(filter, doc, options);
                    } catch (Exception e) {
                        BackpacksMod.LOGGER.error("Error saving to MongoDB for " + playerId + ": " + e.getMessage());
                        throw e;
                    }
                }, mongoExecutor);

                try {
                    saveTask.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);

                    backpacks.markClean();
                    backpacks.setLastSync(System.currentTimeMillis());
                    updateCache(playerId, backpacks);

                } catch (TimeoutException e) {
                    BackpacksMod.LOGGER.error("MongoDB save timeout for " + playerId);
                    // No marcar como clean si falló el guardado
                } catch (Exception e) {
                    BackpacksMod.LOGGER.error("Error saving to MongoDB for " + playerId + ": " + e.getMessage());
                    // No marcar como clean si falló el guardado
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                BackpacksMod.LOGGER.warn("Interrupted while waiting for save semaphore for " + playerId);
            } finally {
                pendingWrites.remove(playerId);
                if (acquired) {
                    activeOperations.decrementAndGet();
                    mongoOperationsSemaphore.release();
                }
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

    // CORREGIDO: Guardado masivo con timeouts más permisivos
    public void saveAllDirtyBackpacks() {
        if (isShuttingDown.get()) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int maxConcurrent = Math.min(10, localCache.size());

        int processed = 0;
        for (Map.Entry<UUID, PlayerBackpacks> entry : localCache.entrySet()) {
            if (entry.getValue().isDirty() && processed < maxConcurrent) {
                futures.add(savePlayerBackpacks(entry.getKey(), entry.getValue()));
                processed++;
            }
        }

        if (!futures.isEmpty()) {
            try {
                // Timeout más permisivo
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(60, TimeUnit.SECONDS); // Aumentado a 60 segundos
                BackpacksMod.LOGGER.info("Saved " + futures.size() + " dirty backpacks");
            } catch (TimeoutException e) {
                BackpacksMod.LOGGER.warn("Mass save timeout - some data may not be saved (normal during high load)");
            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error in mass save operation", e);
            }
        }
    }

    // Cache management mejorado
    private void updateCache(UUID playerId, PlayerBackpacks backpacks) {
        // Limpieza preventiva menos agresiva
        if (localCache.size() >= MAX_CACHE_SIZE) {
            cleanupCacheGracefully();
        }

        localCache.put(playerId, backpacks);
        lastSyncTime.put(playerId, System.currentTimeMillis());
    }

    // NUEVO: Limpieza de cache más suave
    private void cleanupCacheGracefully() {
        try {
            long now = System.currentTimeMillis();
            List<UUID> toRemove = new ArrayList<>();

            // Solo remover entradas muy antiguas
            for (Map.Entry<UUID, Long> entry : lastSyncTime.entrySet()) {
                UUID playerId = entry.getKey();
                long lastSync = entry.getValue();
                PlayerBackpacks backpacks = localCache.get(playerId);

                // Solo remover si es muy antiguo Y no está sucio
                if ((now - lastSync) > (CACHE_TIMEOUT * 2) &&
                        (backpacks == null || !backpacks.isDirty())) {
                    toRemove.add(playerId);
                }
            }

            // Limitar cuánto removemos de una vez
            int maxToRemove = Math.min(toRemove.size(), MAX_CACHE_SIZE / 4);
            for (int i = 0; i < maxToRemove; i++) {
                UUID playerId = toRemove.get(i);
                localCache.remove(playerId);
                lastSyncTime.remove(playerId);
            }

            if (maxToRemove > 0) {
                BackpacksMod.LOGGER.debug("Cleaned " + maxToRemove + " entries from cache gracefully");
            }

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error in graceful cache cleanup", e);
        }
    }

    // CORREGIDO: Cierre más robusto
    public void close() {
        BackpacksMod.LOGGER.info("Iniciando cierre de MongoDB...");
        isShuttingDown.set(true);

        try {
            // Esperar operaciones activas con timeout más largo
            long waitStart = System.currentTimeMillis();
            while (activeOperations.get() > 0 &&
                    (System.currentTimeMillis() - waitStart) < 15000) { // 15 segundos
                Thread.sleep(500);
            }

            // Guardado final con más tiempo
            saveAllDirtyBackpacks();

            // Cerrar executor
            mongoExecutor.shutdown();
            if (!mongoExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
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

    // CORREGIDO: getPlayerBackpacks más tolerante
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
                // Timeout más permisivo para carga síncrona
                backpacks = loadPlayerBackpacks(playerId).get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                BackpacksMod.LOGGER.warn("Load timeout for player " + playerId + ", using cache");
                if (backpacks == null) {
                    backpacks = new PlayerBackpacks();
                    updateCache(playerId, backpacks);
                }
            } catch (Exception e) {
                BackpacksMod.LOGGER.warn("Error loading player " + playerId + ": " + e.getMessage());
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
            cleanupCacheGracefully();
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
            // Guardado asíncrono no bloqueante
            savePlayerBackpacks(playerId, backpacks);
        } catch (IllegalStateException e) {
            throw new RuntimeException("Límite de mochilas alcanzado");
        }
    }

    public void removeBackpack(UUID playerId, int id) {
        if (isShuttingDown.get()) return;

        PlayerBackpacks backpacks = getPlayerBackpacks(playerId);
        backpacks.removeBackpack(id);
        // Guardado asíncrono no bloqueante
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