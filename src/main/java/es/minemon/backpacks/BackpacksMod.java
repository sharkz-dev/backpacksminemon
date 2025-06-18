package es.minemon.backpacks;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackpacksMod implements DedicatedServerModInitializer {
	public static final String MOD_ID = "backpacks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MinecraftServer server;
	private static MongoBackpackManager mongoManager;
	private static BackupManager backupManager;

	private static int cacheCleanupCounter = 0;
	private static final int CACHE_CLEANUP_INTERVAL = 24000; // 20 minutos (optimizado)

	@Override
	public void onInitializeServer() {
		LOGGER.info("Iniciando BackpacksMod v3.1.0 - Sistema Optimizado");

		// Inicializar componentes esenciales
		try {
			LanguageManager.initialize();
			ConfigManager.initialize();
		} catch (Exception e) {
			LOGGER.error("Error inicializando configuración", e);
			throw new RuntimeException("No se pudo inicializar la configuración", e);
		}

		// MongoDB
		if (ConfigManager.isFeatureEnabled("mongodb")) {
			try {
				mongoManager = new MongoBackpackManager();
				LOGGER.info("MongoDB inicializado");
			} catch (Exception e) {
				LOGGER.error("Error inicializando MongoDB", e);
				throw new RuntimeException("No se pudo inicializar MongoDB", e);
			}
		}

		// Backup
		if (ConfigManager.isFeatureEnabled("backup") && mongoManager != null) {
			backupManager = new BackupManager(mongoManager);
		}

		// Sistema de sincronización
		BackpackSyncManager.initialize();

		// Registrar comandos principales únicamente
		CommandRegistrationCallback.EVENT.register(BackpackCommands::register);
		CommandRegistrationCallback.EVENT.register(ConfigCommands::register);
		CommandRegistrationCallback.EVENT.register(VipCommands::register);

		// Eventos
		PlayerEventsHandler.register();
		BackpackRenameManager.register();

		// Networking
		registerNetworking();
		registerServerEvents();

		LOGGER.info("BackpacksMod v3.1.0 inicializado");
	}

	private void registerNetworking() {
		try {
			PayloadTypeRegistry.playC2S().register(BackpackNetworking.OpenBackpackPayload.ID, BackpackNetworking.OpenBackpackPayload.CODEC);
			PayloadTypeRegistry.playC2S().register(BackpackNetworking.ChangeIconPayload.ID, BackpackNetworking.ChangeIconPayload.CODEC);
			PayloadTypeRegistry.playC2S().register(BackpackNetworking.RenameBackpackPayload.ID, BackpackNetworking.RenameBackpackPayload.CODEC);

			ServerPlayNetworking.registerGlobalReceiver(BackpackNetworking.OpenBackpackPayload.ID, BackpackNetworking::handleOpenBackpack);
			ServerPlayNetworking.registerGlobalReceiver(BackpackNetworking.ChangeIconPayload.ID, BackpackNetworking::handleChangeIcon);
			ServerPlayNetworking.registerGlobalReceiver(BackpackNetworking.RenameBackpackPayload.ID, BackpackNetworking::handleRenameBackpack);
		} catch (Exception e) {
			LOGGER.error("Error inicializando networking", e);
		}
	}

	private void registerServerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			BackpacksMod.server = server;

			try {
				LuckPermsManager.initialize();
				LOGGER.info("Sistema de permisos: " + LuckPermsManager.getPermissionSystemInfo());
			} catch (Exception e) {
				LOGGER.error("Error inicializando permisos", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			try {
				BackpackSyncManager.shutdown();
				if (mongoManager != null) {
					mongoManager.saveAllDirtyBackpacks();
				}
				if (backupManager != null && ConfigManager.getConfig().createEmergencyBackup) {
					backupManager.performEmergencyBackup();
				}
			} catch (Exception e) {
				LOGGER.error("Error durante cierre", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			try {
				if (backupManager != null) {
					backupManager.shutdown();
				}
				if (mongoManager != null) {
					mongoManager.close();
				}
				BackpacksMod.server = null;
			} catch (Exception e) {
				LOGGER.error("Error liberando recursos", e);
			}
		});

		// Tick optimizado - limpieza menos frecuente
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
				if (backupManager != null) {
					backupManager.tick();
				}

				// Limpieza de cache cada 20 minutos
				cacheCleanupCounter++;
				if (cacheCleanupCounter >= CACHE_CLEANUP_INTERVAL) {
					cacheCleanupCounter = 0;
					if (mongoManager != null) {
						mongoManager.cleanupInactiveCache();
					}
				}
			} catch (Exception e) {
				// Error silencioso para evitar spam
			}
		});
	}

	// Métodos estáticos esenciales
	public static MinecraftServer getServer() {
		return server;
	}

	public static MongoBackpackManager getMongoManager() {
		return mongoManager;
	}

	public static BackupManager getBackupManager() {
		return backupManager;
	}

	public static boolean isFullyInitialized() {
		return server != null && mongoManager != null && ConfigManager.getConfig() != null;
	}

	// Método de emergencia simplificado
	public static void emergencySave(String reason) {
		try {
			if (mongoManager != null) {
				mongoManager.saveAllDirtyBackpacks();
			}
			if (backupManager != null) {
				backupManager.createManualBackup("Emergency: " + reason);
			}
		} catch (Exception e) {
			LOGGER.error("Error en guardado de emergencia", e);
		}
	}
}