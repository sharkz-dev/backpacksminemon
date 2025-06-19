// OPTIMIZADO: BackpacksMod.java - SIN sistema de backups
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BackpacksMod implements DedicatedServerModInitializer {
	public static final String MOD_ID = "backpacks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MinecraftServer server;
	private static MongoBackpackManager mongoManager;
	// ELIMINADO: BackupManager completamente removido

	// Control de estado simplificado
	private static final AtomicBoolean isFullyReady = new AtomicBoolean(false);
	private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
	private static final AtomicInteger healthCheckCounter = new AtomicInteger(0);

	// Contadores optimizados (solo cache cleanup)
	private static final AtomicInteger cacheCleanupCounter = new AtomicInteger(0);
	private static final int CACHE_CLEANUP_INTERVAL = 36000; // 30 minutos
	private static final int HEALTH_CHECK_INTERVAL = 12000; // 10 minutos

	@Override
	public void onInitializeServer() {
		LOGGER.info("Iniciando BackpacksMod v3.1.0 - Optimizado SIN backups");

		try {
			// Inicializar componentes esenciales con timeout
			CompletableFuture<Void> initTask = CompletableFuture.runAsync(() -> {
				try {
					LanguageManager.initialize();
					ConfigManager.initialize();
					LOGGER.info("Configuration initialized successfully");
				} catch (Exception e) {
					LOGGER.error("Critical error initializing configuration", e);
					throw new RuntimeException("Configuration initialization failed", e);
				}
			});

			initTask.get(10, TimeUnit.SECONDS);

		} catch (Exception e) {
			LOGGER.error("Failed to initialize core components", e);
			throw new RuntimeException("Core initialization failed", e);
		}

		// SOLO MongoDB - sin backups
		if (ConfigManager.isFeatureEnabled("mongodb")) {
			try {
				mongoManager = new MongoBackpackManager();
				LOGGER.info("MongoDB initialized with performance optimizations");
			} catch (Exception e) {
				LOGGER.error("MongoDB initialization failed", e);
				throw new RuntimeException("MongoDB is required but failed to initialize", e);
			}
		}

		// Sistema de sincronización
		try {
			BackpackSyncManager.initialize();
			LOGGER.info("Sync system initialized");
		} catch (Exception e) {
			LOGGER.error("Sync system initialization failed", e);
			throw new RuntimeException("Sync system is required", e);
		}

		// Registrar comandos
		try {
			CommandRegistrationCallback.EVENT.register(BackpackCommands::register);
			CommandRegistrationCallback.EVENT.register(ConfigCommands::register);
			CommandRegistrationCallback.EVENT.register(VipCommands::register);
			CommandRegistrationCallback.EVENT.register(PermissionCommands::register);
			CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
				BackpackCommands.registerConsoleHelp(dispatcher);
			});
			LOGGER.info("Commands registered successfully");
		} catch (Exception e) {
			LOGGER.error("Command registration failed", e);
		}

		// Eventos
		try {
			PlayerEventsHandler.register();
			BackpackRenameManager.register();
			LOGGER.info("Event handlers registered");
		} catch (Exception e) {
			LOGGER.error("Event handler registration failed", e);
		}

		// Networking
		try {
			registerNetworking();
			LOGGER.info("Networking registered");
		} catch (Exception e) {
			LOGGER.error("Networking registration failed", e);
		}

		registerServerEvents();

		LOGGER.info("BackpacksMod v3.1.0 initialized successfully WITHOUT backup system");
		LOGGER.info("=== CONSOLE USAGE ===");
		LOGGER.info("Use '{}' commands from console for administration", ConfigManager.getConfig().mainCommand);
		LOGGER.info("Type '{}-help' for full console command list", ConfigManager.getConfig().mainCommand);
		LOGGER.info("=== PERFORMANCE NOTES ===");
		LOGGER.info("• Backup system DISABLED for maximum performance");
		LOGGER.info("• All data relies on MongoDB persistence");
		LOGGER.info("• Reduced memory usage and CPU overhead");
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
			LOGGER.error("Error registering networking", e);
		}
	}

	private void registerServerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			BackpacksMod.server = server;

			try {
				LuckPermsManager.initialize();
				LOGGER.info("Permission system: " + LuckPermsManager.getPermissionSystemInfo());

				isFullyReady.set(true);

				LOGGER.info("=== ADMINISTRATION INFO ===");
				LOGGER.info("Console has full administrative privileges");
				LOGGER.info("NO backup system - data relies on MongoDB only");
				LOGGER.info("Health monitoring active every 10 minutes");
				LOGGER.info("==========================");

			} catch (Exception e) {
				LOGGER.error("Error initializing permissions", e);
			}
		});

		// SIMPLIFICADO: Shutdown solo con MongoDB
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Server stopping - saving data to MongoDB...");
			isShuttingDown.set(true);
			isFullyReady.set(false);

			try {
				// 1. Parar sincronización
				CompletableFuture<Void> syncShutdown = CompletableFuture.runAsync(() -> {
					try {
						BackpackSyncManager.shutdown();
						LOGGER.info("Sync system shut down");
					} catch (Exception e) {
						LOGGER.error("Error shutting down sync system", e);
					}
				});

				// 2. Guardar datos MongoDB (MÁS TIEMPO SIN BACKUPS)
				CompletableFuture<Void> mongoSave = CompletableFuture.runAsync(() -> {
					if (mongoManager != null) {
						try {
							mongoManager.saveAllDirtyBackpacks();
							LOGGER.info("All data saved to MongoDB");
						} catch (Exception e) {
							LOGGER.error("Error saving MongoDB data", e);
						}
					}
				});

				// Más tiempo para guardado sin presión de backups
				CompletableFuture.allOf(syncShutdown, mongoSave)
						.get(15, TimeUnit.SECONDS); // Más tiempo

				LOGGER.info("Graceful shutdown completed - data saved to MongoDB");

			} catch (Exception e) {
				LOGGER.error("Error during graceful shutdown", e);
			}
		});

		// Cleanup final simplificado
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			try {
				CompletableFuture<Void> finalCleanup = CompletableFuture.runAsync(() -> {
					try {
						if (mongoManager != null) {
							mongoManager.close();
						}
						BackpacksMod.server = null;
						LOGGER.info("MongoDB connection closed");
					} catch (Exception e) {
						LOGGER.error("Error in final cleanup", e);
					}
				});

				finalCleanup.get(5, TimeUnit.SECONDS);

			} catch (Exception e) {
				LOGGER.error("Error during final cleanup", e);
			}
		});

		// SIMPLIFICADO: Tick sin backup manager
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (isShuttingDown.get() || !isFullyReady.get()) {
				return;
			}

			try {
				// Solo cache cleanup - NO backup ticks
				int cacheCount = cacheCleanupCounter.incrementAndGet();
				if (cacheCount >= CACHE_CLEANUP_INTERVAL) {
					cacheCleanupCounter.set(0);
					if (mongoManager != null) {
						CompletableFuture.runAsync(() -> {
							try {
								mongoManager.cleanupInactiveCache();
							} catch (Exception e) {
								LOGGER.warn("Error in cache cleanup: " + e.getMessage());
							}
						});
					}
				}

				// Health check simplificado
				int healthCount = healthCheckCounter.incrementAndGet();
				if (healthCount >= HEALTH_CHECK_INTERVAL) {
					healthCheckCounter.set(0);
					performSimplifiedHealthCheck();
				}

			} catch (Exception e) {
				if (Math.random() < 0.001) {
					LOGGER.warn("Error in server tick: " + e.getMessage());
				}
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

	// ELIMINADO: getBackupManager() - ya no existe

	public static boolean isFullyInitialized() {
		return isFullyReady.get() && server != null && mongoManager != null && ConfigManager.getConfig() != null;
	}

	public static boolean isShuttingDown() {
		return isShuttingDown.get();
	}

	// SIMPLIFICADO: Emergency save solo MongoDB
	public static void emergencySave(String reason) {
		if (isShuttingDown.get()) {
			LOGGER.warn("Cannot perform emergency save during shutdown");
			return;
		}

		LOGGER.warn("=== EMERGENCY SAVE TO MONGODB ===");
		LOGGER.warn("Reason: {}", reason);

		try {
			CompletableFuture<Void> emergencyTask = CompletableFuture.runAsync(() -> {
				try {
					if (mongoManager != null) {
						mongoManager.saveAllDirtyBackpacks();
						LOGGER.info("Emergency save: All data saved to MongoDB");
					}
				} catch (Exception e) {
					LOGGER.error("Error in emergency save", e);
				}
			});

			emergencyTask.get(20, TimeUnit.SECONDS); // Más tiempo sin backups
			LOGGER.warn("=== EMERGENCY SAVE COMPLETED ===");

		} catch (Exception e) {
			LOGGER.error("Emergency save failed or timed out", e);
		}
	}

	// SIMPLIFICADO: Health check sin backup system
	public static void performSimplifiedHealthCheck() {
		if (isShuttingDown.get()) return;

		try {
			StringBuilder healthReport = new StringBuilder();
			healthReport.append("=== SIMPLIFIED HEALTH CHECK ===\n");

			boolean allHealthy = true;
			int issues = 0;

			// MongoDB health
			if (mongoManager == null) {
				healthReport.append("❌ MongoDB: Not initialized\n");
				allHealthy = false;
				issues++;
			} else {
				boolean hasPendingWrites = mongoManager.hasPendingWrites();
				if (hasPendingWrites) {
					healthReport.append("⚠️ MongoDB: Has pending writes\n");
				} else {
					healthReport.append("✅ MongoDB: Healthy\n");
				}
			}

			// Memory health
			Runtime runtime = Runtime.getRuntime();
			long totalMemory = runtime.totalMemory();
			long freeMemory = runtime.freeMemory();
			long usedMemory = totalMemory - freeMemory;
			double memoryUsagePercent = (double) usedMemory / totalMemory * 100;

			if (memoryUsagePercent > 90) {
				healthReport.append("❌ Memory: Critical usage (").append(String.format("%.1f", memoryUsagePercent)).append("%)\n");
				allHealthy = false;
				issues++;
			} else if (memoryUsagePercent > 75) {
				healthReport.append("⚠️ Memory: High usage (").append(String.format("%.1f", memoryUsagePercent)).append("%)\n");
			} else {
				healthReport.append("✅ Memory: Normal usage (").append(String.format("%.1f", memoryUsagePercent)).append("%)\n");
			}

			// VIP system health
			try {
				boolean vipValid = VipBackpackManager.isVipConfigurationValid();
				if (vipValid) {
					healthReport.append("✅ VIP System: Configuration valid\n");
				} else {
					healthReport.append("⚠️ VIP System: Configuration issues\n");
				}
			} catch (Exception e) {
				healthReport.append("❌ VIP System: Error checking\n");
				issues++;
			}

			// Overall status
			healthReport.append("=== STATUS: ");
			if (allHealthy && issues == 0) {
				healthReport.append("HEALTHY (NO BACKUPS) ===");
				LOGGER.info(healthReport.toString());
			} else if (issues < 2) {
				healthReport.append("MINOR ISSUES ===");
				LOGGER.warn(healthReport.toString());
			} else {
				healthReport.append("ISSUES DETECTED ===");
				LOGGER.error(healthReport.toString());

				// Limpieza automática si hay problemas
				if (memoryUsagePercent > 90) {
					LOGGER.warn("Performing emergency cleanup...");
					System.gc();
					if (mongoManager != null) {
						mongoManager.cleanupInactiveCache();
					}
				}
			}

		} catch (Exception e) {
			LOGGER.error("Error during simplified health check", e);
		}
	}

	// SIMPLIFICADO: Stats sin backup info
	public static String getServerStats() {
		try {
			StringBuilder stats = new StringBuilder();
			stats.append("BackpacksMod Server Statistics (NO BACKUPS):\n");
			stats.append("- Server ID: ").append(ConfigManager.getConfig().serverId).append("\n");
			stats.append("- Status: ").append(isFullyReady.get() ? "Ready" : "Not Ready").append("\n");
			stats.append("- Max backpacks per player: ").append(ConfigManager.getConfig().maxBackpacksPerPlayer).append("\n");
			stats.append("- Language messages: ").append(LanguageManager.getTotalMessages()).append("\n");
			stats.append("- Permission system: ").append(LuckPermsManager.getPermissionSystemInfo()).append("\n");
			stats.append("- VIP system: ").append(VipBackpackManager.isVipConfigurationValid() ? "Compatible" : "Issues detected").append("\n");

			// Memory info
			Runtime runtime = Runtime.getRuntime();
			long totalMemory = runtime.totalMemory();
			long freeMemory = runtime.freeMemory();
			long usedMemory = totalMemory - freeMemory;
			stats.append("- Memory usage: ").append(String.format("%.1f", (double) usedMemory / totalMemory * 100)).append("%\n");

			if (mongoManager != null) {
				stats.append("- MongoDB: Connected\n");
				stats.append("- Pending writes: ").append(mongoManager.hasPendingWrites() ? "Yes" : "No").append("\n");
			}

			stats.append("- Backup system: DISABLED for performance\n");
			stats.append("- Data persistence: MongoDB only\n");

			return stats.toString();
		} catch (Exception e) {
			return "Error getting server statistics: " + e.getMessage();
		}
	}

	// SIMPLIFICADO: Cleanup solo para MongoDB
	public static void forceSystemCleanup(String reason) {
		if (isShuttingDown.get()) {
			LOGGER.warn("Cannot perform cleanup during shutdown");
			return;
		}

		LOGGER.info("Forcing system cleanup: {}", reason);

		try {
			CompletableFuture<Void> cleanupTask = CompletableFuture.runAsync(() -> {
				try {
					System.gc();

					if (mongoManager != null) {
						mongoManager.cleanupInactiveCache();
					}

					LOGGER.info("System cleanup completed (no backup system)");
				} catch (Exception e) {
					LOGGER.error("Error during system cleanup", e);
				}
			});

			cleanupTask.get(10, TimeUnit.SECONDS);

		} catch (Exception e) {
			LOGGER.error("System cleanup failed or timed out", e);
		}
	}

	// Performance metrics simplificados
	public static String getPerformanceMetrics() {
		try {
			StringBuilder metrics = new StringBuilder();
			metrics.append("=== Performance Metrics (No Backups) ===\n");

			// Thread info
			java.lang.management.ThreadMXBean threadBean =
					java.lang.management.ManagementFactory.getThreadMXBean();
			metrics.append("Active threads: ").append(threadBean.getThreadCount()).append("\n");

			// Memory info
			Runtime runtime = Runtime.getRuntime();
			long totalMemory = runtime.totalMemory();
			long freeMemory = runtime.freeMemory();
			long usedMemory = totalMemory - freeMemory;

			metrics.append("Memory used: ").append(usedMemory / 1024 / 1024).append(" MB\n");
			metrics.append("Memory usage: ").append(String.format("%.1f", (double) usedMemory / totalMemory * 100)).append("%\n");

			// MongoDB status
			if (mongoManager != null) {
				metrics.append("MongoDB pending writes: ").append(mongoManager.hasPendingWrites() ? "Yes" : "No").append("\n");
			}

			metrics.append("Backup system: DISABLED\n");
			metrics.append("Performance impact: MINIMAL\n");

			return metrics.toString();
		} catch (Exception e) {
			return "Error getting performance metrics: " + e.getMessage();
		}
	}
}