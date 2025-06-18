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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MongoBackpackManager {
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    // Cache optimizado para alto rendimiento
    private final Map<UUID, PlayerBackpacks> localCache = new ConcurrentHashMap<>();
    private final Set<UUID> pendingWrites = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastSyncTime = new ConcurrentHashMap<>();

    // Timeouts optimizados para múltiples usuarios
    private static final long CACHE_TIMEOUT = 60000; // 1 minuto (aumentado)
    private static final int MAX_CACHE_SIZE = 1000; // Límite de cache

    public MongoBackpackManager() {
        try {
            BackpackConfig config = ConfigManager.getConfig();

            mongoClient = MongoClients.create(config.mongoConnectionString);
            database = mongoClient.getDatabase(config.databaseName);
            collection = database.getCollection(config.collectionName);

            // Índices optimizados
            collection.createIndex(new Document("_id", 1));
            collection.createIndex(new Document("lastUpdated", -1));

            BackpacksMod.LOGGER.info("MongoDB conectado: " + config.databaseName);
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error conectando a MongoDB", e);
            throw new RuntimeException("No se pudo conectar a MongoDB", e);
        }
    }

    public static class PlayerBackpacks {
        private final Map<Integer, BackpackData> backpacks = new HashMap<>();
        private boolean dirty = false;
        private long lastSync = 0;

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

        // Serialización optimizada
        public Document toDocument() {
            Document doc = new Document();
            List<Document> backpacksList = new ArrayList<>();

            for (Map.Entry<Integer, BackpackData> entry : backpacks.entrySet()) {
                Document backpackDoc = new Document();
                backpackDoc.put("id", entry.getKey());
                backpackDoc.put("name", entry.getValue().getName());
                backpackDoc.put("slots", entry.getValue().getSlots());

                // Serializar icono de forma más eficiente
                try {
                    ItemStack icon = entry.getValue().getIcon();
                    if (icon != null && !icon.isEmpty()) {
                        MinecraftServer server = BackpacksMod.getServer();
                        if (server != null) {
                            NbtCompound iconNbt = new NbtCompound();
                            ItemStack.CODEC.encode(icon, server.getRegistryManager().getOps(NbtOps.INSTANCE), new NbtCompound())
                                    .resultOrPartial(error -> {})
                                    .ifPresent(encoded -> backpackDoc.put("icon", encoded.toString()));
                        }
                    }
                } catch (Exception e) {
                    // Error silencioso
                }

                // Serializar items solo si no están vacíos
                List<Document> items = new ArrayList<>();
                DefaultedList<ItemStack> inventory = entry.getValue().getInventory();

                for (int i = 0; i < inventory.size(); i++) {
                    ItemStack stack = inventory.get(i);
                    if (!stack.isEmpty()) { // Solo serializar items no vacíos
                        Document itemDoc = new Document();
                        itemDoc.put("slot", i);

                        try {
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
                        } catch (Exception e) {
                            // Error silencioso, saltar este item
                            continue;
                        }

                        items.add(itemDoc);
                    }
                }

                backpackDoc.put("items", items);
                backpackDoc.put("lastModified", System.currentTimeMillis());
                backpacksList.add(backpackDoc);
            }

            doc.put("backpacks", backpacksList);
            doc.put("lastUpdated", System.currentTimeMillis());
            doc.put("serverId", ConfigManager.getConfig().serverId);

            return doc;
        }

        // Deserialización optimizada
        public void fromDocument(Document doc) {
            backpacks.clear();

            if (!doc.containsKey("backpacks")) {
                return;
            }

            try {
                List<Document> backpacksList = doc.getList("backpacks", Document.class);

                for (Document backpackDoc : backpacksList) {
                    int id = backpackDoc.getInteger("id");
                    String name = backpackDoc.getString("name");
                    int slots = backpackDoc.getInteger("slots", ConfigManager.getConfig().backpackSlots);

                    BackpackData backpack = new BackpackData(name, slots);

                    // Deserializar icono
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
                        } catch (Exception e) {
                            backpack.setIcon(new ItemStack(Items.CHEST));
                        }
                    }

                    // Deserializar items
                    if (backpackDoc.containsKey("items")) {
                        List<Document> items = backpackDoc.getList("items", Document.class);

                        for (Document itemDoc : items) {
                            try {
                                int slot = itemDoc.getInteger("slot");
                                String nbtString = itemDoc.getString("nbt");

                                if (nbtString != null && !nbtString.isEmpty() && slot >= 0 && slot < backpack.getInventory().size()) {
                                    MinecraftServer server = BackpacksMod.getServer();
                                    if (server != null) {
                                        try {
                                            NbtCompound nbt = StringNbtReader.parse(nbtString);
                                            ItemStack.CODEC.decode(server.getRegistryManager().getOps(NbtOps.INSTANCE), nbt)
                                                    .resultOrPartial(error -> {})
                                                    .ifPresent(pair -> backpack.getInventory().set(slot, pair.getFirst()));
                                        } catch (Exception parseError) {
                                            // Error silencioso, saltar este item
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Error silencioso, continuar con el siguiente
                            }
                        }
                    }

                    backpacks.put(id, backpack);
                }

                setLastSync(System.currentTimeMillis());
                markClean();

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error procesando documento de MongoDB", e);
            }
        }
    }

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

    // Operaciones de carga optimizadas
    public CompletableFuture<PlayerBackpacks> loadPlayerBackpacks(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Verificar cache válido
                PlayerBackpacks cached = localCache.get(playerId);
                Long lastSync = lastSyncTime.get(playerId);

                if (cached != null && lastSync != null &&
                        (System.currentTimeMillis() - lastSync) < CACHE_TIMEOUT &&
                        !cached.isDirty()) {
                    return cached;
                }

                // Cargar desde MongoDB
                Bson filter = Filters.eq("_id", playerId.toString());
                Document doc = collection.find(filter).first();

                PlayerBackpacks backpacks = new PlayerBackpacks();
                if (doc != null) {
                    backpacks.fromDocument(doc);
                }

                // Actualizar cache con gestión de tamaño
                updateCache(playerId, backpacks);
                return backpacks;

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error cargando de MongoDB para " + playerId, e);

                // Fallback al cache
                PlayerBackpacks fallback = localCache.get(playerId);
                if (fallback == null) {
                    fallback = new PlayerBackpacks();
                    localCache.put(playerId, fallback);
                }
                return fallback;
            }
        });
    }

    // Actualización de cache con gestión de memoria
    private void updateCache(UUID playerId, PlayerBackpacks backpacks) {
        // Limpiar cache si excede el límite
        if (localCache.size() >= MAX_CACHE_SIZE) {
            cleanupOldestCacheEntries();
        }

        localCache.put(playerId, backpacks);
        lastSyncTime.put(playerId, System.currentTimeMillis());
    }

    // Limpieza de entradas más antiguas del cache
    private void cleanupOldestCacheEntries() {
        try {
            // Encontrar las 20% entradas más antiguas y eliminarlas
            int toRemove = (int) (localCache.size() * 0.2);
            List<Map.Entry<UUID, Long>> sortedBySyncTime = new ArrayList<>();

            for (Map.Entry<UUID, Long> entry : lastSyncTime.entrySet()) {
                sortedBySyncTime.add(entry);
            }

            sortedBySyncTime.sort(Map.Entry.comparingByValue());

            for (int i = 0; i < Math.min(toRemove, sortedBySyncTime.size()); i++) {
                UUID toRemoveId = sortedBySyncTime.get(i).getKey();
                localCache.remove(toRemoveId);
                lastSyncTime.remove(toRemoveId);
            }
        } catch (Exception e) {
            // Error silencioso
        }
    }

    // Guardado optimizado
    public CompletableFuture<Void> savePlayerBackpacks(UUID playerId, PlayerBackpacks backpacks) {
        return CompletableFuture.runAsync(() -> {
            try {
                pendingWrites.add(playerId);

                Document doc = backpacks.toDocument();
                doc.put("_id", playerId.toString());

                Bson filter = Filters.eq("_id", playerId.toString());
                ReplaceOptions options = new ReplaceOptions().upsert(true);

                collection.replaceOne(filter, doc, options);

                backpacks.markClean();
                backpacks.setLastSync(System.currentTimeMillis());
                updateCache(playerId, backpacks);

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error guardando en MongoDB para " + playerId, e);
            } finally {
                pendingWrites.remove(playerId);
            }
        });
    }

    // Operaciones síncronas optimizadas
    public PlayerBackpacks getPlayerBackpacks(UUID playerId) {
        PlayerBackpacks backpacks = localCache.get(playerId);
        Long lastSync = lastSyncTime.get(playerId);

        boolean needsReload = backpacks == null || lastSync == null ||
                (System.currentTimeMillis() - lastSync) > CACHE_TIMEOUT;

        if (needsReload) {
            try {
                backpacks = loadPlayerBackpacks(playerId).get(3, TimeUnit.SECONDS); // Timeout reducido
            } catch (Exception e) {
                if (backpacks == null) {
                    backpacks = new PlayerBackpacks();
                    updateCache(playerId, backpacks);
                }
            }
        }

        return backpacks;
    }

    // Métodos de gestión simplificados
    public void addBackpack(UUID playerId, int id, String name, int slots) {
        PlayerBackpacks backpacks = forceReloadPlayerBackpacks(playerId);
        try {
            backpacks.addBackpack(id, name, slots);
            savePlayerBackpacks(playerId, backpacks).join();
        } catch (IllegalStateException e) {
            throw new RuntimeException("Límite de mochilas alcanzado");
        }
    }

    public void removeBackpack(UUID playerId, int id) {
        PlayerBackpacks backpacks = forceReloadPlayerBackpacks(playerId);
        backpacks.removeBackpack(id);
        savePlayerBackpacks(playerId, backpacks).join();
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

    // Recarga forzada optimizada
    private PlayerBackpacks forceReloadPlayerBackpacks(UUID playerId) {
        try {
            localCache.remove(playerId);
            lastSyncTime.remove(playerId);
            return loadPlayerBackpacks(playerId).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            PlayerBackpacks fallback = new PlayerBackpacks();
            updateCache(playerId, fallback);
            return fallback;
        }
    }

    // Guardado masivo optimizado
    public void saveAllDirtyBackpacks() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<UUID, PlayerBackpacks> entry : localCache.entrySet()) {
            if (entry.getValue().isDirty()) {
                futures.add(savePlayerBackpacks(entry.getKey(), entry.getValue()));
            }
        }

        // Esperar con timeout para evitar bloqueos
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error en guardado masivo", e);
        }
    }

    // Métodos de utilidad
    public Map<UUID, PlayerBackpacks> getAllCachedBackpacks() {
        return new HashMap<>(localCache);
    }

    public boolean hasPendingWrites() {
        return !pendingWrites.isEmpty();
    }

    public void invalidatePlayerCache(UUID playerId) {
        localCache.remove(playerId);
        lastSyncTime.remove(playerId);
    }

    // Limpieza de cache optimizada
    public void cleanupInactiveCache() {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : lastSyncTime.entrySet()) {
            if (now - entry.getValue() > CACHE_TIMEOUT * 2) { // Doble timeout para limpieza
                toRemove.add(entry.getKey());
            }
        }

        for (UUID playerId : toRemove) {
            localCache.remove(playerId);
            lastSyncTime.remove(playerId);
        }
    }

    public void close() {
        try {
            saveAllDirtyBackpacks();
            Thread.sleep(1000); // Reducido de 2 segundos
            mongoClient.close();
            BackpacksMod.LOGGER.info("MongoDB cerrado correctamente");
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error cerrando MongoDB", e);
        }
    }
}