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
		LOGGER.info("Iniciando BackpacksMod v3.1.0 - Sistema Optimizado con Soporte de Consola");

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
		CommandRegistrationCallback.EVENT.register(PermissionCommands::register);

		// NUEVO: Registrar comando de ayuda para consola
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			BackpackCommands.registerConsoleHelp(dispatcher);
		});

		// Eventos
		PlayerEventsHandler.register();
		BackpackRenameManager.register();

		// Networking
		registerNetworking();
		registerServerEvents();

		LOGGER.info("BackpacksMod v3.1.0 inicializado con soporte completo para consola");

		// Mensaje informativo para consola
		LOGGER.info("=== CONSOLE USAGE ===");
		LOGGER.info("Use '{}' commands from console for administration", ConfigManager.getConfig().mainCommand);
		LOGGER.info("Example: {} give <player> <name> <slots>", ConfigManager.getConfig().mainCommand);
		LOGGER.info("Type '{}-help' for full console command list", ConfigManager.getConfig().mainCommand);
		LOGGER.info("==================");
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

				// Mensaje adicional para administradores
				LOGGER.info("=== ADMINISTRATION INFO ===");
				LOGGER.info("Console has full administrative privileges");
				LOGGER.info("All console actions are logged automatically");
				LOGGER.info("Use console commands for server automation");
				LOGGER.info("==========================");

			} catch (Exception e) {
				LOGGER.error("Error inicializando permisos", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			try {
				LOGGER.info("Cerrando BackpacksMod - guardando datos...");

				BackpackSyncManager.shutdown();
				if (mongoManager != null) {
					mongoManager.saveAllDirtyBackpacks();
				}
				if (backupManager != null && ConfigManager.getConfig().createEmergencyBackup) {
					backupManager.performEmergencyBackup();
				}

				LOGGER.info("BackpacksMod cerrado correctamente");
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

				LOGGER.info("Recursos de BackpacksMod liberados");
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

	// Método de emergencia simplificado con logging mejorado
	public static void emergencySave(String reason) {
		try {
			LOGGER.warn("=== EMERGENCY SAVE TRIGGERED ===");
			LOGGER.warn("Reason: {}", reason);

			if (mongoManager != null) {
				mongoManager.saveAllDirtyBackpacks();
				LOGGER.info("Emergency save: MongoDB data saved");
			}
			if (backupManager != null) {
				backupManager.createManualBackup("Emergency: " + reason);
				LOGGER.info("Emergency save: Backup created");
			}

			LOGGER.warn("=== EMERGENCY SAVE COMPLETED ===");
		} catch (Exception e) {
			LOGGER.error("Error en guardado de emergencia", e);
		}
	}

	// NUEVOS: Métodos de utilidad para administración desde consola

	/**
	 * Ejecuta comando administrativo desde consola de forma segura
	 */
	public static void executeConsoleCommand(String command) {
		if (server == null) {
			LOGGER.error("Server not initialized, cannot execute console command: {}", command);
			return;
		}

		try {
			server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
			LOGGER.info("Console command executed: {}", command);
		} catch (Exception e) {
			LOGGER.error("Error executing console command '{}': {}", command, e.getMessage());
		}
	}

	/**
	 * Obtiene estadísticas del servidor para logging
	 */
	public static String getServerStats() {
		try {
			StringBuilder stats = new StringBuilder();
			stats.append("BackpacksMod Server Statistics:\n");
			stats.append("- Server ID: ").append(ConfigManager.getConfig().serverId).append("\n");
			stats.append("- Max backpacks per player: ").append(ConfigManager.getConfig().maxBackpacksPerPlayer).append("\n");
			stats.append("- Language messages: ").append(LanguageManager.getTotalMessages()).append("\n");
			stats.append("- Permission system: ").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");
			stats.append("- VIP system: ").append(VipBackpackManager.isVipConfigurationValid() ? "Compatible" : "Issues detected").append("\n");

			if (backupManager != null) {
				var backups = backupManager.getAvailableBackups();
				stats.append("- Available backups: ").append(backups.size()).append("\n");
			}

			if (mongoManager != null) {
				stats.append("- MongoDB: Connected\n");
				stats.append("- Pending writes: ").append(mongoManager.hasPendingWrites() ? "Yes" : "No").append("\n");
			}

			return stats.toString();
		} catch (Exception e) {
			return "Error getting server statistics: " + e.getMessage();
		}
	}

	/**
	 * Logs las estadísticas del servidor (útil para monitoreo)
	 */
	public static void logServerStats() {
		LOGGER.info("\n" + getServerStats());
	}

	/**
	 * Verifica la salud del sistema y reporta problemas
	 */
	public static void performHealthCheck() {
		StringBuilder healthReport = new StringBuilder();
		healthReport.append("=== BACKPACKS HEALTH CHECK ===\n");

		boolean allHealthy = true;

		// Verificar MongoDB
		if (mongoManager == null) {
			healthReport.append("❌ MongoDB: Not initialized\n");
			allHealthy = false;
		} else {
			healthReport.append("✅ MongoDB: Connected\n");
		}

		// Verificar sistema de backup
		if (backupManager == null) {
			healthReport.append("❌ Backup System: Not initialized\n");
			allHealthy = false;
		} else {
			try {
				var backups = backupManager.getAvailableBackups();
				if (backups.isEmpty()) {
					healthReport.append("⚠️ Backup System: No backups available\n");
				} else {
					healthReport.append("✅ Backup System: ").append(backups.size()).append(" backups available\n");
				}
			} catch (Exception e) {
				healthReport.append("❌ Backup System: Error accessing backups\n");
				allHealthy = false;
			}
		}

		// Verificar sistema de idiomas
		try {
			int messages = LanguageManager.getTotalMessages();
			if (messages < 50) {
				healthReport.append("⚠️ Language System: Low message count (").append(messages).append(")\n");
			} else {
				healthReport.append("✅ Language System: ").append(messages).append(" messages loaded\n");
			}
		} catch (Exception e) {
			healthReport.append("❌ Language System: Error\n");
			allHealthy = false;
		}

		// Verificar sistema VIP
		try {
			boolean vipValid = VipBackpackManager.isVipConfigurationValid();
			if (vipValid) {
				healthReport.append("✅ VIP System: Configuration valid\n");
			} else {
				healthReport.append("⚠️ VIP System: Configuration issues detected\n");
			}
		} catch (Exception e) {
			healthReport.append("❌ VIP System: Error checking configuration\n");
			allHealthy = false;
		}

		// Verificar permisos
		try {
			String permSystem = LuckPermsManager.getPermissionSystemInfo();
			healthReport.append("✅ Permissions: ").append(permSystem).append("\n");
		} catch (Exception e) {
			healthReport.append("❌ Permissions: Error\n");
			allHealthy = false;
		}

		healthReport.append("=== HEALTH STATUS: ").append(allHealthy ? "HEALTHY" : "ISSUES DETECTED").append(" ===");

		if (allHealthy) {
			LOGGER.info(healthReport.toString());
		} else {
			LOGGER.warn(healthReport.toString());
		}
	}

	/**
	 * Programa verificación de salud automática cada 30 minutos
	 */
	public static void scheduleHealthChecks() {
		// Esta función podría expandirse para programar verificaciones automáticas
		LOGGER.info("Health check system initialized - manual checks available");
	}
}