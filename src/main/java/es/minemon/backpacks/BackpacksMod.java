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
	private static final int CACHE_CLEANUP_INTERVAL = 12000; // 10 minutos en ticks

	@Override
	public void onInitializeServer() {
		LOGGER.info("Iniciando BackpacksMod v3.1.0 - Sistema de Idiomas y Permisos LuckPerms (Server-Side)");

		// PASO 1: Inicializar sistema de idiomas PRIMERO
		try {
			LanguageManager.initialize();
			LOGGER.info("Sistema de idiomas inicializado correctamente");
		} catch (Exception e) {
			LOGGER.error("Error inicializando sistema de idiomas", e);
		}

		// PASO 2: Inicializar configuración
		try {
			ConfigManager.initialize();
			LOGGER.info("Configuración inicializada correctamente");
		} catch (Exception e) {
			LOGGER.error("Error inicializando configuración, usando valores por defecto", e);
		}

		// PASO 3: Inicializar MongoDB
		if (ConfigManager.isFeatureEnabled("mongodb")) {
			try {
				mongoManager = new MongoBackpackManager();
				LOGGER.info("MongoDB inicializado correctamente");
			} catch (Exception e) {
				LOGGER.error("Error inicializando MongoDB: ", e);
				throw new RuntimeException("No se pudo inicializar MongoDB", e);
			}
		}

		// PASO 4: Inicializar backup
		if (ConfigManager.isFeatureEnabled("backup") && mongoManager != null) {
			backupManager = new BackupManager(mongoManager);
			LOGGER.info("Sistema de backup inicializado");
		}

		// PASO 5: Inicializar sistema de sincronización
		BackpackSyncManager.initialize();
		LOGGER.info("Sistema de sincronización inicializado");

		// PASO 6: Registrar comandos
		CommandRegistrationCallback.EVENT.register(BackpackCommands::register);
		CommandRegistrationCallback.EVENT.register(ConfigCommands::register);
		CommandRegistrationCallback.EVENT.register(PermissionCommands::register);
		CommandRegistrationCallback.EVENT.register(VipCommands::register); // NUEVO: Comandos VIP

		// PASO 7: Registrar eventos
		PlayerEventsHandler.register();
		BackpackRenameManager.register();

		// PASO 8: Registrar networking
		registerNetworking();

		// PASO 9: Eventos del servidor
		registerServerEvents();

		LOGGER.info("BackpacksMod v3.1.0 inicializado correctamente (Server-Side)");
		LOGGER.info("Archivos de configuración en: config/backpacks/");
		LOGGER.info("Idioma: " + LanguageManager.getLanguageInfo());

		// NOTA: LuckPerms se inicializará cuando el servidor esté completamente cargado
	}

	private void registerNetworking() {
		try {
			// Registrar tipos de payload
			PayloadTypeRegistry.playC2S().register(BackpackNetworking.OpenBackpackPayload.ID, BackpackNetworking.OpenBackpackPayload.CODEC);
			PayloadTypeRegistry.playC2S().register(BackpackNetworking.ChangeIconPayload.ID, BackpackNetworking.ChangeIconPayload.CODEC);
			PayloadTypeRegistry.playC2S().register(BackpackNetworking.RenameBackpackPayload.ID, BackpackNetworking.RenameBackpackPayload.CODEC);

			// Registrar handlers
			ServerPlayNetworking.registerGlobalReceiver(BackpackNetworking.OpenBackpackPayload.ID, BackpackNetworking::handleOpenBackpack);
			ServerPlayNetworking.registerGlobalReceiver(BackpackNetworking.ChangeIconPayload.ID, BackpackNetworking::handleChangeIcon);
			ServerPlayNetworking.registerGlobalReceiver(BackpackNetworking.RenameBackpackPayload.ID, BackpackNetworking::handleRenameBackpack);

			LOGGER.info("Sistema de networking inicializado correctamente");
		} catch (Exception e) {
			LOGGER.error("Error inicializando networking", e);
		}
	}

	private void registerServerEvents() {
		// Evento de inicio del servidor
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			BackpacksMod.server = server;
			LOGGER.info("Servidor iniciado - BackpacksMod v3.1.0 activo (Server-Side)");

			// AHORA inicializar LuckPerms cuando el servidor esté completamente cargado
			try {
				LuckPermsManager.initialize();
				LOGGER.info("Sistema de permisos inicializado: " + LuckPermsManager.getPermissionSystemInfo());

				// Mostrar información de diagnóstico si LuckPerms no se detecta
				if (!LuckPermsManager.isLuckPermsAvailable()) {
					LOGGER.warn("LuckPerms no detectado. Información de diagnóstico:");
					String[] diagLines = LuckPermsManager.getDiagnosticInfo().split("\n");
					for (String line : diagLines) {
						LOGGER.warn(line);
					}
					LOGGER.warn("Use /backpack-perms diagnose para más información");
				}
			} catch (Exception e) {
				LOGGER.error("Error inicializando sistema de permisos", e);
			}

			LOGGER.info("Idioma actual: " + LanguageManager.getLanguageInfo());
			LOGGER.info("Mensajes totales: " + LanguageManager.getTotalMessages());

			// Mostrar información de configuración importante
			logImportantConfig();
		});

		// Evento de cierre del servidor
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Servidor cerrándose - guardando datos...");

			try {
				// Limpiar sincronización
				BackpackSyncManager.shutdown();

				// Guardar datos de MongoDB
				if (mongoManager != null) {
					mongoManager.saveAllDirtyBackpacks();
				}

				// Crear backup de emergencia si está habilitado
				if (backupManager != null && ConfigManager.getConfig().createEmergencyBackup) {
					backupManager.performEmergencyBackup();
				}

				LOGGER.info("Datos guardados correctamente");

			} catch (Exception e) {
				LOGGER.error("Error durante cierre del servidor", e);
			}
		});

		// Evento de servidor completamente cerrado
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			try {
				// Cerrar managers
				if (backupManager != null) {
					backupManager.shutdown();
				}

				if (mongoManager != null) {
					mongoManager.close();
				}

				// Limpiar variables estáticas
				BackpacksMod.server = null;
				LOGGER.info("Recursos liberados correctamente");
			} catch (Exception e) {
				LOGGER.error("Error liberando recursos", e);
			}
		});

		// Tick del servidor (optimizado)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
				// Backup automático
				if (backupManager != null && ConfigManager.isFeatureEnabled("backup")) {
					backupManager.tick();
				}

				// Limpieza de cache menos frecuente (cada 10 minutos)
				cacheCleanupCounter++;
				if (cacheCleanupCounter >= CACHE_CLEANUP_INTERVAL) {
					cacheCleanupCounter = 0;
					if (mongoManager != null) {
						mongoManager.cleanupInactiveCache();
					}
				}
			} catch (Exception e) {
				// Error handling silencioso para evitar spam en logs
			}
		});
	}

	private void logImportantConfig() {
		try {
			BackpackConfig config = ConfigManager.getConfig();

			LOGGER.info("=== CONFIGURACIÓN IMPORTANTE ===");
			LOGGER.info("Comando principal: /" + config.mainCommand);
			LOGGER.info("Comando de jugador: /" + config.playerCommand);
			LOGGER.info("Max mochilas por jugador: " + config.maxBackpacksPerPlayer);
			LOGGER.info("Renombrado permitido: " + config.allowBackpackRename);
			LOGGER.info("Iconos personalizados: " + config.allowCustomIcons);
			LOGGER.info("Nivel de permisos admin: " + config.adminPermissionLevel);
			LOGGER.info("Intervalo de backup: " + config.backupIntervalMinutes + " minutos");

			if (LuckPermsManager.isLuckPermsAvailable()) {
				LOGGER.info("LuckPerms ACTIVO - Permisos granulares disponibles");
			} else {
				LOGGER.info("LuckPerms NO DISPONIBLE - Usando sistema de fallback con OP");
				LOGGER.info("Para habilitar LuckPerms, asegúrate de tener LuckPerms-Fabric instalado");
			}

			LOGGER.info("MongoDB: " + config.mongoConnectionString);
			LOGGER.info("Base de datos: " + config.databaseName);
			LOGGER.info("================================");
		} catch (Exception e) {
			LOGGER.error("Error mostrando configuración", e);
		}
	}

	// Métodos estáticos para acceso global
	public static MinecraftServer getServer() {
		return server;
	}

	public static MongoBackpackManager getMongoManager() {
		return mongoManager;
	}

	public static BackupManager getBackupManager() {
		return backupManager;
	}

	// Método para obtener información del mod
	public static String getModInfo() {
		StringBuilder info = new StringBuilder();
		info.append("BackpacksMod v3.1.0 (Server-Side)\n");
		info.append("Sistema de permisos: ").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");
		info.append("Idioma: ").append(LanguageManager.getLanguageInfo()).append("\n");
		info.append("MongoDB: ").append(mongoManager != null ? "Activo" : "Inactivo").append("\n");
		info.append("Backup: ").append(backupManager != null ? "Activo" : "Inactivo");
		return info.toString();
	}

	// Método para verificar si el mod está completamente inicializado
	public static boolean isFullyInitialized() {
		return server != null &&
				mongoManager != null &&
				ConfigManager.getConfig() != null &&
				LanguageManager.getTotalMessages() > 0;
	}

	// Método para obtener estadísticas del mod
	public static String getModStats() {
		if (!isFullyInitialized()) {
			return "Mod no completamente inicializado";
		}

		StringBuilder stats = new StringBuilder();
		stats.append("=== BackpacksMod Statistics (Server-Side) ===\n");
		stats.append("Versión: 3.1.0\n");
		stats.append("Servidor activo: ").append(server != null ? "Sí" : "No").append("\n");
		stats.append("Jugadores online: ").append(server != null ? server.getCurrentPlayerCount() : 0).append("\n");
		stats.append("Sistema de permisos: ").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");
		stats.append("Mensajes de idioma: ").append(LanguageManager.getTotalMessages()).append("\n");
		stats.append("MongoDB activo: ").append(mongoManager != null ? "Sí" : "No").append("\n");
		stats.append("Backup activo: ").append(backupManager != null ? "Sí" : "No").append("\n");

		try {
			BackpackConfig config = ConfigManager.getConfig();
			stats.append("Max mochilas/jugador: ").append(config.maxBackpacksPerPlayer).append("\n");
			stats.append("Comando principal: /").append(config.mainCommand).append("\n");
			stats.append("Comando jugador: /").append(config.playerCommand);
		} catch (Exception e) {
			stats.append("Error obteniendo configuración");
		}

		return stats.toString();
	}

	// Método para recargar configuraciones (para uso administrativo)
	public static boolean reloadConfigurations() {
		try {
			ConfigManager.reloadConfig();
			LanguageManager.reloadLanguage();
			LuckPermsManager.forceReinitialization(); // Re-inicializar permisos

			LOGGER.info("Configuraciones recargadas correctamente");
			return true;
		} catch (Exception e) {
			LOGGER.error("Error recargando configuraciones", e);
			return false;
		}
	}

	// Método para forzar guardado de emergencia
	public static void emergencySave(String reason) {
		try {
			LOGGER.warn("Ejecutando guardado de emergencia: " + reason);

			if (mongoManager != null) {
				mongoManager.saveAllDirtyBackpacks();
			}

			if (backupManager != null) {
				backupManager.createManualBackup("Emergency save: " + reason);
			}

			LOGGER.info("Guardado de emergencia completado");
		} catch (Exception e) {
			LOGGER.error("Error en guardado de emergencia", e);
		}
	}
}