package es.minemon.backpacks;

import com.mongodb.client.*;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.DataResult;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
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

    // Cache local para mejor rendimiento
    private final Map<UUID, PlayerBackpacks> localCache = new ConcurrentHashMap<>();
    private final Set<UUID> pendingWrites = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastSyncTime = new ConcurrentHashMap<>();

    // Tiempo de cache en milisegundos (30 segundos)
    private static final long CACHE_TIMEOUT = 30000;

    public MongoBackpackManager() {
        try {
            BackpackConfig config = ConfigManager.getConfig();

            mongoClient = MongoClients.create(config.mongoConnectionString);
            database = mongoClient.getDatabase(config.databaseName);
            collection = database.getCollection(config.collectionName);

            // Crear índices para mejor rendimiento
            collection.createIndex(new Document("_id", 1));
            collection.createIndex(new Document("lastUpdated", -1));
            collection.createIndex(new Document("serverId", 1));

            BackpacksMod.LOGGER.info("Conectado a MongoDB: " + config.databaseName);
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error conectando a MongoDB: ", e);
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
                throw new IllegalStateException("errorMaxBackpacks");
            }

            backpacks.put(id, new BackpackData(name, slots));
            markDirty();
        }

        public void addBackpack(int id, String name) {
            int defaultSlots = ConfigManager.getConfig().backpackSlots;
            addBackpack(id, name, defaultSlots);
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

        public Document toDocument() {
            Document doc = new Document();
            List<Document> backpacksList = new ArrayList<>();

            for (Map.Entry<Integer, BackpackData> entry : backpacks.entrySet()) {
                Document backpackDoc = new Document();
                backpackDoc.put("id", entry.getKey());
                backpackDoc.put("name", entry.getValue().getName());
                backpackDoc.put("slots", entry.getValue().getSlots());

                // Serializar icono
                try {
                    ItemStack icon = entry.getValue().getIcon();
                    if (icon != null && !icon.isEmpty()) {
                        MinecraftServer server = BackpacksMod.getServer();
                        if (server != null) {
                            NbtCompound iconNbt = new NbtCompound();
                            ItemStack.CODEC.encode(icon, server.getRegistryManager().getOps(NbtOps.INSTANCE), new NbtCompound())
                                    .resultOrPartial(error -> BackpacksMod.LOGGER.error("Error encoding icon: " + error))
                                    .ifPresent(encoded -> backpackDoc.put("icon", encoded.toString()));
                        }
                    }
                } catch (Exception e) {
                    BackpacksMod.LOGGER.error("Error serializando icono de mochila", e);
                }

                List<Document> items = new ArrayList<>();
                DefaultedList<ItemStack> inventory = entry.getValue().getInventory();

                for (int i = 0; i < inventory.size(); i++) {
                    final int slot = i;
                    ItemStack stack = inventory.get(i);
                    Document itemDoc = new Document();
                    itemDoc.put("slot", slot);

                    if (!stack.isEmpty()) {
                        try {
                            MinecraftServer server = BackpacksMod.getServer();
                            if (server != null) {
                                NbtCompound nbt = new NbtCompound();
                                ItemStack.CODEC.encode(stack, server.getRegistryManager().getOps(NbtOps.INSTANCE), new NbtCompound())
                                        .resultOrPartial(error -> BackpacksMod.LOGGER.error("Error encoding item: " + error))
                                        .ifPresent(encoded -> {
                                            itemDoc.put("nbt", encoded.toString());
                                            itemDoc.put("isEmpty", false);
                                            itemDoc.put("count", stack.getCount());
                                            itemDoc.put("itemId", stack.getItem().toString());
                                        });
                            } else {
                                BackpacksMod.LOGGER.warn("Servidor no disponible para serialización en slot " + slot);
                                itemDoc.put("isEmpty", true);
                            }
                        } catch (Exception e) {
                            BackpacksMod.LOGGER.error("Error serializando item en slot " + slot + ": " + stack, e);
                            itemDoc.put("isEmpty", true);
                        }
                    } else {
                        itemDoc.put("isEmpty", true);
                    }

                    items.add(itemDoc);
                }

                backpackDoc.put("items", items);
                backpackDoc.put("lastModified", System.currentTimeMillis());
                backpacksList.add(backpackDoc);
            }

            doc.put("backpacks", backpacksList);
            doc.put("lastUpdated", System.currentTimeMillis());
            doc.put("serverId", ConfigManager.getConfig().serverId);
            doc.put("version", "3.0.0");

            return doc;
        }

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
                                            .resultOrPartial(error -> BackpacksMod.LOGGER.error("Error decoding icon: " + error))
                                            .ifPresent(pair -> backpack.setIcon(pair.getFirst()));
                                }
                            }
                        } catch (Exception e) {
                            BackpacksMod.LOGGER.error("Error deserializando icono de mochila", e);
                            backpack.setIcon(new ItemStack(Items.CHEST));
                        }
                    }

                    if (backpackDoc.containsKey("items")) {
                        List<Document> items = backpackDoc.getList("items", Document.class);

                        for (Document itemDoc : items) {
                            try {
                                int slot = itemDoc.getInteger("slot");
                                boolean isEmpty = itemDoc.getBoolean("isEmpty", true);

                                if (!isEmpty && itemDoc.containsKey("nbt")) {
                                    String nbtString = itemDoc.getString("nbt");

                                    if (nbtString != null && !nbtString.isEmpty()) {
                                        MinecraftServer server = BackpacksMod.getServer();
                                        if (server != null) {
                                            try {
                                                NbtCompound nbt = StringNbtReader.parse(nbtString);

                                                ItemStack.CODEC.decode(server.getRegistryManager().getOps(NbtOps.INSTANCE), nbt)
                                                        .resultOrPartial(error -> BackpacksMod.LOGGER.error("Error decoding item: " + error))
                                                        .ifPresent(pair -> {
                                                            ItemStack stack = pair.getFirst();
                                                            if (slot >= 0 && slot < backpack.getInventory().size()) {
                                                                backpack.getInventory().set(slot, stack);
                                                            }
                                                        });
                                            } catch (Exception parseError) {
                                                BackpacksMod.LOGGER.error("Error parsing NBT for slot " + slot + ": " + parseError.getMessage());
                                            }
                                        } else {
                                            BackpacksMod.LOGGER.warn("Servidor no disponible para deserialización");
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                BackpacksMod.LOGGER.error("Error parseando item: ", e);
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

        public BackpackData(String name) {
            this(name, ConfigManager.getConfig().backpackSlots);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public DefaultedList<ItemStack> getInventory() {
            return inventory;
        }

        public int getSlots() {
            return slots;
        }

        public ItemStack getIcon() {
            return icon;
        }

        public void setIcon(ItemStack icon) {
            this.icon = icon != null ? icon.copy() : new ItemStack(Items.CHEST);
        }
    }

    public CompletableFuture<PlayerBackpacks> loadPlayerBackpacks(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerBackpacks cached = localCache.get(playerId);
                Long lastSync = lastSyncTime.get(playerId);

                if (cached != null && lastSync != null &&
                        (System.currentTimeMillis() - lastSync) < CACHE_TIMEOUT &&
                        !cached.isDirty()) {
                    return cached;
                }

                Bson filter = Filters.eq("_id", playerId.toString());
                Document doc = collection.find(filter).first();

                PlayerBackpacks backpacks = new PlayerBackpacks();
                if (doc != null) {
                    backpacks.fromDocument(doc);
                }

                localCache.put(playerId, backpacks);
                lastSyncTime.put(playerId, System.currentTimeMillis());
                return backpacks;

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error cargando mochilas de MongoDB para " + playerId, e);

                PlayerBackpacks fallback = localCache.get(playerId);
                if (fallback == null) {
                    fallback = new PlayerBackpacks();
                    localCache.put(playerId, fallback);
                }
                return fallback;
            }
        });
    }

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
                localCache.put(playerId, backpacks);
                lastSyncTime.put(playerId, System.currentTimeMillis());

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error guardando mochilas en MongoDB para " + playerId, e);
            } finally {
                pendingWrites.remove(playerId);
            }
        });
    }

    public PlayerBackpacks getPlayerBackpacks(UUID playerId) {
        PlayerBackpacks backpacks = localCache.get(playerId);
        Long lastSync = lastSyncTime.get(playerId);

        boolean needsReload = false;

        if (backpacks == null) {
            needsReload = true;
        } else if (lastSync == null || (System.currentTimeMillis() - lastSync) > CACHE_TIMEOUT) {
            needsReload = true;
        }

        if (needsReload) {
            try {
                backpacks = loadPlayerBackpacks(playerId).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error cargando mochilas síncronamente para " + playerId, e);
                if (backpacks == null) {
                    backpacks = new PlayerBackpacks();
                    localCache.put(playerId, backpacks);
                }
            }
        }

        return backpacks;
    }

    public void addBackpack(UUID playerId, int id, String name, int slots) {
        PlayerBackpacks backpacks = forceReloadPlayerBackpacks(playerId);

        try {
            backpacks.addBackpack(id, name, slots);
            savePlayerBackpacks(playerId, backpacks).join();
        } catch (IllegalStateException e) {
            throw new RuntimeException(LanguageManager.getMessage("errorMaxBackpacks", ConfigManager.getConfig().maxBackpacksPerPlayer));
        }
    }

    public void addBackpack(UUID playerId, int id, String name) {
        int defaultSlots = ConfigManager.getConfig().backpackSlots;
        addBackpack(playerId, id, name, defaultSlots);
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

    private PlayerBackpacks forceReloadPlayerBackpacks(UUID playerId) {
        try {
            localCache.remove(playerId);
            lastSyncTime.remove(playerId);

            return loadPlayerBackpacks(playerId).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error en recarga forzada para " + playerId, e);
            PlayerBackpacks fallback = new PlayerBackpacks();
            localCache.put(playerId, fallback);
            return fallback;
        }
    }

    public void saveAllDirtyBackpacks() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<UUID, PlayerBackpacks> entry : localCache.entrySet()) {
            if (entry.getValue().isDirty()) {
                futures.add(savePlayerBackpacks(entry.getKey(), entry.getValue()));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

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

    public void cleanupInactiveCache() {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : lastSyncTime.entrySet()) {
            if (now - entry.getValue() > CACHE_TIMEOUT * 3) {
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
            BackpacksMod.LOGGER.info("Cerrando MongoBackpackManager...");

            saveAllDirtyBackpacks();
            Thread.sleep(2000);

            mongoClient.close();
            BackpacksMod.LOGGER.info("MongoBackpackManager cerrado correctamente");
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error cerrando conexión MongoDB", e);
        }
    }
}