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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupManager {
    private final MongoBackpackManager mongoManager;
    private final Gson gson;
    private final Path backupPath;
    private final Path emergencyPath;

    private int tickCounter = 0;
    private final Map<UUID, Long> lastPlayerActivity = new ConcurrentHashMap<>();
    private final Set<UUID> playersWithChanges = ConcurrentHashMap.newKeySet();

    // Pool de hilos para operaciones de backup
    private final ExecutorService backupExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Backup-Worker");
        t.setDaemon(true);
        return t;
    });

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
            BackpacksMod.LOGGER.info("Sistema de backup inicializado");
        } catch (IOException e) {
            BackpacksMod.LOGGER.error("Error creando directorios de backup", e);
        }
    }

    public void tick() {
        tickCounter++;

        int backupInterval = ConfigManager.getBackupIntervalTicks();

        // Backup automático
        if (tickCounter >= backupInterval) {
            tickCounter = 0;
            performScheduledBackup();
        }

        // Auto-guardado optimizado
        int autoSaveInterval = ConfigManager.getConfig().autoSaveIntervalSeconds * 20;
        if (tickCounter % autoSaveInterval == 0) {
            saveActivePlayersChanges();
        }
    }

    public void markPlayerActivity(UUID playerId) {
        lastPlayerActivity.put(playerId, System.currentTimeMillis());
        playersWithChanges.add(playerId);
    }

    // Optimización: backup asíncrono
    private void performScheduledBackup() {
        if (!ConfigManager.isFeatureEnabled("backup")) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Guardar datos sucios primero
                mongoManager.saveAllDirtyBackpacks();

                // Crear backup completo
                createFullBackup();

                // Limpiar backups antiguos
                cleanOldBackups();

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error durante backup automático", e);
            }
        }, backupExecutor);
    }

    // Optimización: guardado más eficiente
    private void saveActivePlayersChanges() {
        if (playersWithChanges.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            Set<UUID> toSave = new HashSet<>(playersWithChanges);
            playersWithChanges.clear();

            // Usar el método optimizado del mongoManager
            mongoManager.saveAllDirtyBackpacks();
        }, backupExecutor);
    }

    public void performEmergencyBackup() {
        if (!ConfigManager.getConfig().createEmergencyBackup) {
            return;
        }

        try {
            BackpacksMod.LOGGER.info("Ejecutando backup de emergencia...");

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String filename = "emergency_backup_" + timestamp + ".json";
            Path emergencyFile = emergencyPath.resolve(filename);

            Map<UUID, MongoBackpackManager.PlayerBackpacks> allData = mongoManager.getAllCachedBackpacks();

            JsonObject emergencyData = new JsonObject();
            emergencyData.addProperty("timestamp", System.currentTimeMillis());
            emergencyData.addProperty("server_shutdown", true);
            emergencyData.addProperty("server_id", ConfigManager.getConfig().serverId);
            emergencyData.addProperty("total_players", allData.size());

            JsonObject playersData = new JsonObject();
            for (Map.Entry<UUID, MongoBackpackManager.PlayerBackpacks> entry : allData.entrySet()) {
                JsonObject playerData = serializePlayerBackpacks(entry.getValue());
                playersData.add(entry.getKey().toString(), playerData);
            }

            emergencyData.add("players", playersData);

            // Escribir archivo de emergencia
            try (FileWriter writer = new FileWriter(emergencyFile.toFile())) {
                gson.toJson(emergencyData, writer);
            }

            BackpacksMod.LOGGER.info("Backup de emergencia guardado en: " + emergencyFile);

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error durante backup de emergencia", e);
        }
    }

    private void createFullBackup() throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String filename = "backup_" + timestamp + ".json";
        Path backupFile = backupPath.resolve(filename);

        Map<UUID, MongoBackpackManager.PlayerBackpacks> allData = mongoManager.getAllCachedBackpacks();

        JsonObject backupData = new JsonObject();
        backupData.addProperty("timestamp", System.currentTimeMillis());
        backupData.addProperty("backup_type", "scheduled");
        backupData.addProperty("server_id", ConfigManager.getConfig().serverId);
        backupData.addProperty("total_players", allData.size());
        backupData.addProperty("config_version", "2.2.0");

        JsonObject playersData = new JsonObject();
        for (Map.Entry<UUID, MongoBackpackManager.PlayerBackpacks> entry : allData.entrySet()) {
            JsonObject playerData = serializePlayerBackpacks(entry.getValue());
            playersData.add(entry.getKey().toString(), playerData);
        }

        backupData.add("players", playersData);

        try (FileWriter writer = new FileWriter(backupFile.toFile())) {
            gson.toJson(backupData, writer);
        }
    }

    // Serialización optimizada
    private JsonObject serializePlayerBackpacks(MongoBackpackManager.PlayerBackpacks playerBackpacks) {
        JsonObject playerData = new JsonObject();
        JsonObject backpacksData = new JsonObject();

        for (Map.Entry<Integer, MongoBackpackManager.BackpackData> entry : playerBackpacks.getAllBackpacks().entrySet()) {
            JsonObject backpackData = new JsonObject();
            MongoBackpackManager.BackpackData backpack = entry.getValue();

            backpackData.addProperty("name", backpack.getName());
            backpackData.addProperty("slots", backpack.getInventory().size());

            // Serializar solo items no vacíos
            JsonObject itemsData = new JsonObject();
            DefaultedList<ItemStack> inventory = backpack.getInventory();

            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.get(i);
                if (stack.isEmpty()) continue; // Saltar items vacíos

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
                    } catch (Exception e) {
                        // Silencioso - saltar item problemático
                    }
                }
            }

            backpackData.add("items", itemsData);
            backpacksData.add(String.valueOf(entry.getKey()), backpackData);
        }

        playerData.add("backpacks", backpacksData);
        playerData.addProperty("last_activity", lastPlayerActivity.getOrDefault(UUID.randomUUID(), System.currentTimeMillis()));
        return playerData;
    }

    private void cleanOldBackups() {
        try {
            BackpackConfig config = ConfigManager.getConfig();

            File[] backupFiles = backupPath.toFile().listFiles((dir, name) ->
                    name.startsWith("backup_") && name.endsWith(".json"));

            if (backupFiles != null && backupFiles.length > config.maxBackupFiles) {
                Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));

                int toDelete = backupFiles.length - config.maxBackupFiles;
                for (int i = 0; i < toDelete; i++) {
                    backupFiles[i].delete();
                }
            }

        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error limpiando backups antiguos", e);
        }
    }

    public void createManualBackup(String reason) {
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
                    JsonObject playerData = serializePlayerBackpacks(entry.getValue());
                    playersData.add(entry.getKey().toString(), playerData);
                }

                backupData.add("players", playersData);

                try (FileWriter writer = new FileWriter(backupFile.toFile())) {
                    gson.toJson(backupData, writer);
                }

                BackpacksMod.LOGGER.info("Backup manual creado: " + backupFile + " (Razón: " + reason + ")");

            } catch (Exception e) {
                BackpacksMod.LOGGER.error("Error creando backup manual", e);
            }
        }, backupExecutor);
    }

    public List<String> getAvailableBackups() {
        List<String> backups = new ArrayList<>();

        File[] backupFiles = backupPath.toFile().listFiles((dir, name) ->
                name.endsWith(".json"));

        if (backupFiles != null) {
            Arrays.sort(backupFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            for (File file : backupFiles) {
                backups.add(file.getName());
            }
        }

        return backups;
    }

    // Método para cerrar recursos
    public void shutdown() {
        try {
            backupExecutor.shutdown();
            if (!backupExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                backupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            backupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}