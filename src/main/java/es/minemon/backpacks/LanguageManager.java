package es.minemon.backpacks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Sistema de idiomas COMPLETO - Incluye TODOS los mensajes del mod
 * CORREGIDO: Añade todos los mensajes que faltan
 */
public class LanguageManager {
    private static final String CONFIG_DIR = "config/backpacks";
    private static final String LANG_FILE = "lang.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static LanguageData languageData;
    private static Path langPath;

    public static void initialize() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            Files.createDirectories(configDir);
            langPath = configDir.resolve(LANG_FILE);
            loadLanguage();
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error inicializando idiomas", e);
            languageData = createCompleteLanguageData();
        }
    }

    private static void loadLanguage() {
        File langFile = langPath.toFile();

        if (!langFile.exists()) {
            createDefaultLanguageFile();
        } else {
            try (FileReader reader = new FileReader(langFile)) {
                LanguageData loadedData = GSON.fromJson(reader, LanguageData.class);

                if (loadedData == null) {
                    createDefaultLanguageFile();
                } else {
                    languageData = loadedData;
                    validateAndCompleteLanguageData();
                    saveLanguage();
                }

            } catch (JsonSyntaxException e) {
                BackpacksMod.LOGGER.error("Error JSON en archivo de idioma", e);
                createDefaultLanguageFile();
            } catch (IOException e) {
                languageData = createCompleteLanguageData();
            }
        }
    }

    private static void createDefaultLanguageFile() {
        languageData = createCompleteLanguageData();
        saveLanguage();
    }

    // CORREGIDO: Sistema de mensajes COMPLETO
    private static LanguageData createCompleteLanguageData() {
        LanguageData data = new LanguageData();

        // ========== INFORMACIÓN BÁSICA ==========
        data.info.put("language", "Spanish");
        data.info.put("version", "3.1.0");
        data.info.put("description", "Sistema completo de idiomas para BackpacksMod");

        // ========== MENSAJES BÁSICOS ==========
        data.messages.put("backpackGiven", "§aMochila otorgada exitosamente");
        data.messages.put("backpackRemoved", "§aMochila eliminada exitosamente");
        data.messages.put("backpackRenamed", "§aMochila renombrada a: §f%s");
        data.messages.put("backpackIconChanged", "§aIcono de mochila cambiado");
        data.messages.put("dataLoaded", "§aDatos de mochilas cargados");
        data.messages.put("dataSaved", "§aDatos guardados exitosamente");
        data.messages.put("dataLoadedWithCount", "§aDatos cargados. Tienes §f%d §amochilas");
        data.messages.put("backpackSuccessMessage", "§aMochila ID §f%d §arenombrada a: §f%s");
        data.messages.put("backpackReceived", "§aRecibiste mochila: §f%s");
        data.messages.put("backpackLost", "§cTu mochila ID §f%d §cha sido eliminada");

        // ========== ERRORES GENERALES ==========
        data.messages.put("errorGeneral", "§cError: %s");
        data.messages.put("errorLoadingData", "§cError cargando datos. Inténtalo de nuevo.");
        data.messages.put("errorLoadingRetry", "§cError cargando datos. Reintentando...");
        data.messages.put("errorSavingData", "§cError guardando datos");
        data.messages.put("errorOpeningBackpack", "§cError abriendo mochila");
        data.messages.put("errorBackpackNotFound", "§cNo tienes acceso a esa mochila");
        data.messages.put("errorMaxBackpacks", "§cHas alcanzado el límite máximo de mochilas (%d)");
        data.messages.put("errorPlayerOnly", "§cSolo los jugadores pueden usar este comando");
        data.messages.put("errorRenamingBackpack", "§cError renombrando mochila");
        data.messages.put("errorChangingIcon", "§cError cambiando icono de mochila");

        // ========== PERMISOS ==========
        data.messages.put("errorNoPermission", "§cNo tienes permisos para usar este comando");
        data.messages.put("errorNoPermissionLuckPerms", "§cNo tienes el permiso: §f%s");
        data.messages.put("errorNoPermissionFallback", "§cNecesitas privilegios de administrador");
        data.messages.put("errorNoPermissionVipBackpack", "§cNo tienes permiso para acceder a esta mochila VIP");

        // ========== VIP SISTEMA ==========
        data.messages.put("vipBackpackCreated", "§6✦ Mochila VIP %s creada: %s ✦");
        data.messages.put("vipBackpacksUpdated", "§6Mochilas VIP actualizadas! Ahora tienes acceso a %d mochilas VIP");
        data.messages.put("vipBackpackLimitReached", "§c⚠ Límite de mochilas VIP alcanzado\\n§eMáximo: §f%d §e| Actual: §f%d §e| VIP necesarias: §f%d");

        // ========== MOCHILAS POR DEFECTO ==========
        data.messages.put("defaultBackpacksGiven", "§a¡Bienvenido! Has recibido §f%d §amochilas iniciales");
        data.messages.put("defaultBackpacksError", "§cError creando mochilas iniciales. Contacta un administrador.");

        // ========== GUI ELEMENTOS ==========
        data.gui.put("loreItems", "§7Items: §a%d§7/§a%d");
        data.gui.put("loreEmptySlots", "§7Slots vacíos: §c%d");
        data.gui.put("loreUsage", "§7Uso: §e%.1f%%");
        data.gui.put("loreSlots", "§7Capacidad: §f%d slots");
        data.gui.put("loreControls", "§7--- Controles ---");
        data.gui.put("loreClickToOpen", "§eClick izquierdo para abrir");
        data.gui.put("loreRightClickRename", "§eClick derecho para renombrar");
        data.gui.put("loreDragToChangeIcon", "§eArrastrar item para cambiar icono");
        data.gui.put("loreIconQuantityNote", "§7(Solo usa 1 item como icono)");
        data.gui.put("loreIconChangeWarning", "§c⚠ El item regresará a tu inventario");
        data.gui.put("loreReturnToInventory", "§7tras cambiar el icono");

        // ========== TÍTULOS DE MENÚ ==========
        data.gui.put("menuTitleMain", "§dMis Mochilas (%s)");
        data.gui.put("menuTitleBackpack", "§f%s §7(%d/%d)");
        data.gui.put("menuTitleAdminView", "§c[ADMIN] §f%s's Mochilas");
        data.gui.put("menuTitleAdminEdit", "§c[ADMIN EDIT] §f%s");

        // ========== NAVEGACIÓN ==========
        data.gui.put("navigationPreviousPage", "§e← Página Anterior");
        data.gui.put("navigationNextPage", "§ePágina Siguiente →");
        data.gui.put("navigationPageInfo", "§fInformación de Página");
        data.gui.put("navigationCurrentPage", "§fPágina: §e%d §7/ §e%d");
        data.gui.put("navigationTotalBackpacks", "§fTotal de mochilas: §a%d");
        data.gui.put("navigationShowing", "§fMostrando: §e%d §7- §e%d");
        data.gui.put("navigationUseArrows", "§7Usa las flechas para navegar");
        data.gui.put("navigationManyBackpacks", "§7entre tus mochilas");
        data.gui.put("navigationTotalSlotsUsed", "§7Slots totales usados: §a%d");
        data.gui.put("navigationHeader", "§6=== Navegación de Mochilas ===");
        data.gui.put("navigationGoToPage", "§fIr a página §e%d");
        data.gui.put("navigationClickToView", "§7Click para ver");
        data.gui.put("navigationClickForMore", "§7Click para ver más mochilas");
        data.gui.put("navigationPreviousInstruction", "§7Usa la flecha izquierda para la página anterior");
        data.gui.put("navigationNextInstruction", "§7Usa la flecha derecha para la página siguiente");
        data.gui.put("navigationTotalInfo", "§7Tienes un total de §a%d §7mochilas");

        // ========== BOTONES ==========
        data.gui.put("backButtonToMenu", "§c← Volver al Menú de Mochilas");
        data.gui.put("backButtonToAdminView", "§c← Volver a Vista Admin");
        data.gui.put("backButtonDescription", "§7Click para regresar al");
        data.gui.put("backButtonMainMenu", "§7menú principal de mochilas");
        data.gui.put("backButtonAdminOverview", "§7resumen administrativo");
        data.gui.put("backButtonNotStored", "§c⚠ Este botón NO se guarda");
        data.gui.put("backButtonInBackpack", "§cen el inventario de la mochila");
        data.gui.put("backButtonCannotRemove", "§cNo se puede mover o eliminar");
        data.gui.put("backButtonSlotNotSaved", "§7Este slot no se guarda");

        // ========== COMANDOS ==========
        data.commands.put("commandUsage", "§fUso: §e%s");
        data.commands.put("commandSlotsInfo", "§fSlots disponibles: §a9, 18, 27, 36, 45, 54");
        data.commands.put("commandSlotCountInvalid", "§cLos slots deben ser múltiplos de 9 (9-54)");
        data.commands.put("commandNameEmpty", "§cEl nombre no puede estar vacío");
        data.commands.put("commandNameTooLong", "§cNombre muy largo (máximo 50 caracteres)");
        data.commands.put("useCommand", "§eUsa el comando:");
        data.commands.put("renameCommandExample", "§e/%s rename %s %d <nuevo_nombre>");
        data.commands.put("useBackpacksCommand", "§eUsa §f/%s §epara abrir el menú");

        // ========== PERMISOS AVANZADOS ==========
        data.permissions.put("permissionSystemInfo", "§fSistema de permisos: §a%s");
        data.permissions.put("fallbackSystem", "§eUsando sistema de permisos OP");
        data.permissions.put("permissionsReloaded", "§aPermisos recargados: §f%s");
        data.permissions.put("errorReloadingPermissions", "§cError recargando permisos: §f%s");

        // ========== ADMINISTRACIÓN ==========
        data.admin.put("adminEditModeHeader", "§c=== MODO ADMINISTRADOR ===");
        data.admin.put("adminOwnerInfo", "§7Propietario: §f%s");
        data.admin.put("adminControlsHeader", "§c--- Controles Admin ---");
        data.admin.put("adminLeftClickEdit", "§eClick izquierdo: §fEditar mochila");
        data.admin.put("adminRightClickRename", "§eClick derecho: §fRenombrar");
        data.admin.put("adminModeWarning", "§c⚠ Modo administrador activo");
        data.admin.put("adminPanelTitle", "§cPanel de Administrador");
        data.admin.put("adminPlayerInfo", "§7Jugador: §f%s");
        data.admin.put("adminActionsHeader", "§c--- Acciones Disponibles ---");
        data.admin.put("adminCanEdit", "§7• Editar inventarios de mochilas");
        data.admin.put("adminCanAddRemove", "§7• Agregar/remover mochilas");
        data.admin.put("adminCanRename", "§7• Renombrar cualquier mochila");
        data.admin.put("adminWarningHeader", "§c⚠ ADVERTENCIAS ⚠");
        data.admin.put("adminChangesAutomatic", "§7Todos los cambios se guardan automáticamente");
        data.admin.put("adminCannotUndo", "§cLos cambios no se pueden deshacer");
        data.admin.put("adminPlayerUUID", "§7UUID: §f%s");
        data.admin.put("adminControlRestored", "§eControl de admin restaurado");
        data.admin.put("adminSlotReserved", "§7El último slot está reservado para controles");
        data.admin.put("adminEditModeWarning", "§c=== MODO EDICIÓN ADMIN ACTIVO ===");
        data.admin.put("adminAboutToEdit", "§eEstás a punto de editar una mochila ajena");
        data.admin.put("adminEditingPlayer", "§7Jugador objetivo: §f%s");
        data.admin.put("adminEditingBackpackInfo", "§7Mochila: §f%s §7(ID: %d)");

        // ========== VIP AVANZADO ==========
        data.vip.put("vipBackpackCount", "§6Mochilas VIP %s: %d");
        data.vip.put("vipPermissionRequired", "§6Requiere permiso: §f%s");
        data.vip.put("vipStatusActive", "§6✦ VIP %s Activo ✦");
        data.vip.put("vipStatusInactive", "§7VIP %s Inactivo");

        // ========== ESTADÍSTICAS ==========
        data.stats.put("statsHeader", "§6=== Estadísticas de %s ===");
        data.stats.put("playerNameDisplay", "§eJugador: §f%s");
        data.stats.put("uuidDisplay", "§eUUID: §f%s");
        data.stats.put("statsTotalBackpacks", "§eTotal de mochilas: §a%d");
        data.stats.put("statsItemsStored", "§eItems almacenados: §a%d");
        data.stats.put("statsEmptySlots", "§eSlots vacíos: §a%d");
        data.stats.put("statsTotalSlots", "§eSlots totales: §a%d");
        data.stats.put("statsUsagePercent", "§eUso del espacio: §a%.1f%%");
        data.stats.put("currentNameDisplay", "§7Nombre actual: §f%s");
        data.stats.put("backpackIdDisplay", "§7ID de mochila: §f%d");

        // ========== RENOMBRADO ==========
        data.rename.put("nameInstructions", "§6=== RENOMBRAR MOCHILA ===");
        data.rename.put("renameInstructions", "§eEscribe el nuevo nombre en el chat");
        data.rename.put("renameTimeLimit", "§7Tienes 30 segundos para responder");
        data.rename.put("menuAutoOpen", "§7El menú se abrirá automáticamente después");
        data.rename.put("nameCannotBeEmpty", "§cEl nombre no puede estar vacío");
        data.rename.put("nameTooLong", "§cEl nombre es muy largo (máximo 50 caracteres)");
        data.rename.put("renameSuccessful", "§aMochila renombrada exitosamente a: §f%s");
        data.rename.put("renameTimeout", "§cTiempo de renombrado agotado");
        data.rename.put("typeNewName", "§eEscribe un nuevo nombre");
        data.rename.put("typeShorterName", "§eEscribe un nombre más corto");
        data.rename.put("tryAgainLater", "§eIntenta de nuevo más tarde");
        data.rename.put("menuUpdated", "§eMenú actualizado");

        // ========== ICONOS ==========
        data.icons.put("cannotUseEmptyIcon", "§cNo puedes usar un item vacío como icono");
        data.icons.put("iconChangedToItem", "§aIcono cambiado a: §f%s");
        data.icons.put("originalItemReturned", "§7El item original ha sido devuelto a tu inventario");
        data.icons.put("itemDroppedInventoryFull", "§7Item dropeado - inventario lleno");
        data.icons.put("emptyCursorRequired", "§cDebes tener las manos vacías para editar");
        data.icons.put("emptyCursorForRename", "§cDebes tener las manos vacías para renombrar");

        // ========== MENSAJES INFORMATIVOS ==========
        data.misc.put("noBackpacks", "§dNo tienes mochilas disponibles");
        data.misc.put("welcomeMessage", "§a¡Bienvenido al sistema de mochilas!");
        data.misc.put("firstTimeUse", "§7Usa §e/%s §7para abrir tu menú de mochilas");

        return data;
    }

    private static void validateAndCompleteLanguageData() {
        LanguageData defaultData = createCompleteLanguageData();

        // Inicializar mapas si son null
        if (languageData.info == null) languageData.info = new HashMap<>();
        if (languageData.messages == null) languageData.messages = new HashMap<>();
        if (languageData.gui == null) languageData.gui = new HashMap<>();
        if (languageData.commands == null) languageData.commands = new HashMap<>();
        if (languageData.permissions == null) languageData.permissions = new HashMap<>();
        if (languageData.admin == null) languageData.admin = new HashMap<>();
        if (languageData.vip == null) languageData.vip = new HashMap<>();
        if (languageData.stats == null) languageData.stats = new HashMap<>();
        if (languageData.rename == null) languageData.rename = new HashMap<>();
        if (languageData.icons == null) languageData.icons = new HashMap<>();
        if (languageData.misc == null) languageData.misc = new HashMap<>();

        // Agregar mensajes faltantes
        addMissingMessages(languageData.info, defaultData.info);
        addMissingMessages(languageData.messages, defaultData.messages);
        addMissingMessages(languageData.gui, defaultData.gui);
        addMissingMessages(languageData.commands, defaultData.commands);
        addMissingMessages(languageData.permissions, defaultData.permissions);
        addMissingMessages(languageData.admin, defaultData.admin);
        addMissingMessages(languageData.vip, defaultData.vip);
        addMissingMessages(languageData.stats, defaultData.stats);
        addMissingMessages(languageData.rename, defaultData.rename);
        addMissingMessages(languageData.icons, defaultData.icons);
        addMissingMessages(languageData.misc, defaultData.misc);
    }

    private static void addMissingMessages(Map<String, String> target, Map<String, String> source) {
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (!target.containsKey(entry.getKey())) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public static void saveLanguage() {
        try (FileWriter writer = new FileWriter(langPath.toFile())) {
            GSON.toJson(languageData, writer);
        } catch (IOException e) {
            BackpacksMod.LOGGER.error("Error guardando archivo de idioma", e);
        }
    }

    public static void reloadLanguage() {
        loadLanguage();
    }

    // ========== MÉTODOS PRINCIPALES DE ACCESO ==========
    public static String getMessage(String key, Object... args) {
        String message = getMessageRaw(key);

        if (args.length > 0) {
            try {
                return String.format(message, args);
            } catch (Exception e) {
                return message;
            }
        }
        return message;
    }

    public static String getMessageRaw(String key) {
        if (languageData == null) {
            return "[ERROR: Sistema de idiomas no cargado: " + key + "]";
        }

        // Buscar en todas las categorías de forma eficiente
        String message = searchInAllCategories(key);

        if (message != null) {
            return message;
        }

        // Fallback - crear mensaje de error más informativo
        BackpacksMod.LOGGER.warn("Mensaje no encontrado: " + key + " - Agregando automáticamente");

        // Auto-agregar mensaje faltante con valor por defecto
        String defaultMessage = "§7[Mensaje no configurado: " + key + "]";
        languageData.misc.put(key, defaultMessage);

        // Guardar archivo para incluir el nuevo mensaje
        saveLanguage();

        return defaultMessage;
    }

    private static String searchInAllCategories(String key) {
        // Buscar en orden de prioridad
        if (languageData.messages.containsKey(key)) return languageData.messages.get(key);
        if (languageData.gui.containsKey(key)) return languageData.gui.get(key);
        if (languageData.commands.containsKey(key)) return languageData.commands.get(key);
        if (languageData.admin.containsKey(key)) return languageData.admin.get(key);
        if (languageData.vip.containsKey(key)) return languageData.vip.get(key);
        if (languageData.stats.containsKey(key)) return languageData.stats.get(key);
        if (languageData.rename.containsKey(key)) return languageData.rename.get(key);
        if (languageData.icons.containsKey(key)) return languageData.icons.get(key);
        if (languageData.permissions.containsKey(key)) return languageData.permissions.get(key);
        if (languageData.misc.containsKey(key)) return languageData.misc.get(key);

        return null;
    }

    public static Text getMessageAsText(String key, Object... args) {
        String message = getMessage(key, args);
        return MessageUtils.parseText(message);
    }

    public static void sendMessage(net.minecraft.server.network.ServerPlayerEntity player, String key, Object... args) {
        Text message = getMessageAsText(key, args);
        player.sendMessage(message, false);
    }

    public static void sendFeedback(net.minecraft.server.command.ServerCommandSource source, String key, boolean broadcastToOps, Object... args) {
        Text message = getMessageAsText(key, args);
        source.sendFeedback(() -> message, broadcastToOps);
    }

    // ========== MÉTODOS DE INFORMACIÓN ==========
    public static String getLanguageInfo() {
        if (languageData == null || languageData.info == null) {
            return "Idioma: Desconocido";
        }

        String language = languageData.info.getOrDefault("language", "Desconocido");
        String version = languageData.info.getOrDefault("version", "3.1.0");

        return String.format("Idioma: %s (v%s)", language, version);
    }

    public static int getTotalMessages() {
        if (languageData == null) return 0;

        return languageData.messages.size() +
                languageData.gui.size() +
                languageData.commands.size() +
                languageData.permissions.size() +
                languageData.admin.size() +
                languageData.vip.size() +
                languageData.stats.size() +
                languageData.rename.size() +
                languageData.icons.size() +
                languageData.misc.size();
    }

    // ========== MÉTODOS DE UTILIDAD ==========
    public static void addCustomMessage(String key, String message) {
        if (languageData != null) {
            languageData.misc.put(key, message);
            saveLanguage();
        }
    }

    public static boolean hasMessage(String key) {
        return searchInAllCategories(key) != null;
    }

    public static String getLanguageFilePath() {
        return langPath.toString();
    }

    public static void regenerateLanguageFile() {
        languageData = createCompleteLanguageData();
        saveLanguage();
        BackpacksMod.LOGGER.info("Archivo de idiomas regenerado con todos los mensajes");
    }

    // ========== CLASE DE DATOS COMPLETA ==========
    public static class LanguageData {
        public Map<String, String> info = new HashMap<>();
        public Map<String, String> messages = new HashMap<>();
        public Map<String, String> gui = new HashMap<>();
        public Map<String, String> commands = new HashMap<>();
        public Map<String, String> permissions = new HashMap<>();
        public Map<String, String> admin = new HashMap<>();
        public Map<String, String> vip = new HashMap<>();
        public Map<String, String> stats = new HashMap<>();  // NUEVO
        public Map<String, String> rename = new HashMap<>(); // NUEVO
        public Map<String, String> icons = new HashMap<>();  // NUEVO
        public Map<String, String> misc = new HashMap<>();
    }
}