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
 * Sistema de idiomas optimizado - Solo mensajes esenciales
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
            languageData = createEssentialLanguageData();
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
                languageData = createEssentialLanguageData();
            }
        }
    }

    private static void createDefaultLanguageFile() {
        languageData = createEssentialLanguageData();
        saveLanguage();
    }

    // Solo mensajes esenciales para funcionamiento básico
    private static LanguageData createEssentialLanguageData() {
        LanguageData data = new LanguageData();

        // Información básica
        data.info.put("language", "Spanish");
        data.info.put("version", "1.0.0");

        // Mensajes básicos críticos
        data.messages.put("backpackGiven", "§aMochila otorgada exitosamente");
        data.messages.put("backpackRemoved", "§aMochila eliminada exitosamente");
        data.messages.put("backpackRenamed", "§aMochila renombrada a: §f%s");
        data.messages.put("backpackIconChanged", "§aIcono de mochila cambiado");
        data.messages.put("dataLoaded", "§aDatos de mochilas cargados");
        data.messages.put("dataSaved", "§aDatos guardados exitosamente");
        data.messages.put("dataLoadedWithCount", "§aDatos cargados. Tienes §f%d §amochilas");

        // Errores críticos
        data.messages.put("errorGeneral", "§cError: %s");
        data.messages.put("errorLoadingData", "§cError cargando datos. Inténtalo de nuevo.");
        data.messages.put("errorSavingData", "§cError guardando datos");
        data.messages.put("errorOpeningBackpack", "§cError abriendo mochila");
        data.messages.put("errorBackpackNotFound", "§cNo tienes acceso a esa mochila");
        data.messages.put("errorMaxBackpacks", "§cHas alcanzado el límite máximo de mochilas (%d)");
        data.messages.put("errorPlayerOnly", "§cSolo los jugadores pueden usar este comando");
        data.messages.put("errorRenamingBackpack", "§cError renombrando mochila");
        data.messages.put("errorChangingIcon", "§cError cambiando icono de mochila");

        // Permisos básicos
        data.messages.put("errorNoPermission", "§cNo tienes permisos para usar este comando");
        data.messages.put("errorNoPermissionLuckPerms", "§cNo tienes el permiso: §f%s");
        data.messages.put("errorNoPermissionFallback", "§cNecesitas privilegios de administrador");
        data.messages.put("errorNoPermissionVipBackpack", "§cNo tienes permiso para acceder a esta mochila VIP");

        // VIP básico
        data.messages.put("vipBackpackCreated", "§6✦ Mochila VIP %s creada: %s ✦");
        data.messages.put("vipBackpacksUpdated", "§6Mochilas VIP actualizadas! Ahora tienes acceso a %d mochilas VIP");
        data.messages.put("vipBackpackLimitReached", "§c⚠ Límite de mochilas VIP alcanzado\n§eMáximo: §f%d §e| Actual: §f%d §e| VIP necesarias: §f%d");

        // Mochilas por defecto básicas
        data.messages.put("defaultBackpacksGiven", "§a¡Bienvenido! Has recibido §f%d §amochilas iniciales");
        data.messages.put("defaultBackpacksError", "§cError creando mochilas iniciales. Contacta un administrador.");

        // GUI esencial
        data.gui.put("loreItems", "§7Items: §a%d§7/§a%d");
        data.gui.put("loreEmptySlots", "§7Slots vacíos: §c%d");
        data.gui.put("loreUsage", "§7Uso: §e%.1f%%");
        data.gui.put("loreSlots", "§7Capacidad: §f%d slots");
        data.gui.put("loreClickToOpen", "§eClick izquierdo para abrir");
        data.gui.put("loreRightClickRename", "§eClick derecho para renombrar");
        data.gui.put("loreDragToChangeIcon", "§eArrastrar item para cambiar icono");

        // Títulos de menú básicos
        data.gui.put("menuTitleMain", "§dMis Mochilas (%s)");
        data.gui.put("menuTitleBackpack", "§f%s §7(%d/%d)");
        data.gui.put("menuTitleAdminView", "§c[ADMIN] §f%s's Mochilas");
        data.gui.put("menuTitleAdminEdit", "§c[ADMIN EDIT] §f%s");

        // Navegación básica
        data.gui.put("navigationPreviousPage", "§e← Página Anterior");
        data.gui.put("navigationNextPage", "§ePágina Siguiente →");
        data.gui.put("navigationPageInfo", "§fInformación de Página");
        data.gui.put("navigationCurrentPage", "§fPágina: §e%d §7/ §e%d");
        data.gui.put("navigationTotalBackpacks", "§fTotal de mochilas: §a%d");

        // Botones básicos
        data.gui.put("backButtonToMenu", "§c← Volver al Menú de Mochilas");
        data.gui.put("backButtonToAdminView", "§c← Volver a Vista Admin");
        data.gui.put("backButtonDescription", "§7Click para regresar al");
        data.gui.put("backButtonMainMenu", "§7menú principal de mochilas");

        // Comandos básicos
        data.commands.put("commandUsage", "§fUso: §e%s");
        data.commands.put("commandSlotsInfo", "§fSlots disponibles: §a9, 18, 27, 36, 45, 54");
        data.commands.put("commandSlotCountInvalid", "§cLos slots deben ser múltiplos de 9 (9-54)");
        data.commands.put("commandNameEmpty", "§cEl nombre no puede estar vacío");
        data.commands.put("commandNameTooLong", "§cNombre muy largo (máximo 50 caracteres)");

        // Permisos básicos
        data.permissions.put("permissionSystemInfo", "§fSistema de permisos: §a%s");
        data.permissions.put("fallbackSystem", "§eUsando sistema de permisos OP");
        data.permissions.put("permissionsReloaded", "§aPermisos recargados: §f%s");
        data.permissions.put("errorReloadingPermissions", "§cError recargando permisos: §f%s");

        // Admin básico
        data.admin.put("adminControlRestored", "§eControl de admin restaurado");
        data.admin.put("adminChangesAutomatic", "§7Todos los cambios se guardan automáticamente");

        // VIP básico
        data.vip.put("vipBackpackCount", "§6Mochilas VIP %s: %d");
        data.vip.put("vipPermissionRequired", "§6Requiere permiso: §f%s");
        data.vip.put("vipStatusActive", "§6✦ VIP %s Activo ✦");
        data.vip.put("vipStatusInactive", "§7VIP %s Inactivo");

        // Mensajes informativos básicos
        data.misc.put("backpackReceived", "§aRecibiste mochila: §f%s");
        data.misc.put("backpackLost", "§cTu mochila ID §f%d §cha sido eliminada");
        data.misc.put("noBackpacks", "§dNo tienes mochilas disponibles");
        data.misc.put("renameInstructions", "§eEscribe el nuevo nombre para tu mochila en el chat");
        data.misc.put("nameCannotBeEmpty", "§cEl nombre no puede estar vacío");
        data.misc.put("nameTooLong", "§cEl nombre es muy largo (máximo 50 caracteres)");
        data.misc.put("renameSuccessful", "§aMochila renombrada exitosamente a: §f%s");
        data.misc.put("cannotUseEmptyIcon", "§cNo puedes usar un item vacío como icono");
        data.misc.put("iconChangedToItem", "§aIcono cambiado a: §f%s");
        data.misc.put("originalItemReturned", "§7El item original ha sido devuelto a tu inventario");

        return data;
    }

    private static void validateAndCompleteLanguageData() {
        LanguageData defaultData = createEssentialLanguageData();

        if (languageData.info == null) languageData.info = new HashMap<>();
        if (languageData.messages == null) languageData.messages = new HashMap<>();
        if (languageData.gui == null) languageData.gui = new HashMap<>();
        if (languageData.commands == null) languageData.commands = new HashMap<>();
        if (languageData.permissions == null) languageData.permissions = new HashMap<>();
        if (languageData.admin == null) languageData.admin = new HashMap<>();
        if (languageData.vip == null) languageData.vip = new HashMap<>();
        if (languageData.misc == null) languageData.misc = new HashMap<>();

        // Solo agregar mensajes críticos faltantes
        addMissingMessages(languageData.messages, defaultData.messages);
        addMissingMessages(languageData.gui, defaultData.gui);
        addMissingMessages(languageData.commands, defaultData.commands);
        addMissingMessages(languageData.permissions, defaultData.permissions);
        addMissingMessages(languageData.admin, defaultData.admin);
        addMissingMessages(languageData.vip, defaultData.vip);
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

    // Métodos principales de acceso optimizados
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
            return "[Idioma no cargado: " + key + "]";
        }

        // Buscar en categorías de forma eficiente
        String message = languageData.messages.get(key);
        if (message != null) return message;

        message = languageData.gui.get(key);
        if (message != null) return message;

        message = languageData.commands.get(key);
        if (message != null) return message;

        message = languageData.permissions.get(key);
        if (message != null) return message;

        message = languageData.admin.get(key);
        if (message != null) return message;

        message = languageData.vip.get(key);
        if (message != null) return message;

        message = languageData.misc.get(key);
        if (message != null) return message;

        // Fallback simplificado
        return "§7[Mensaje no encontrado: " + key + "]";
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

    // Métodos de información simplificados
    public static String getLanguageInfo() {
        if (languageData == null || languageData.info == null) {
            return "Idioma: Desconocido";
        }

        String language = languageData.info.getOrDefault("language", "Desconocido");
        String version = languageData.info.getOrDefault("version", "1.0.0");

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
                languageData.misc.size();
    }

    // Clase de datos simplificada
    public static class LanguageData {
        public Map<String, String> info = new HashMap<>();
        public Map<String, String> messages = new HashMap<>();
        public Map<String, String> gui = new HashMap<>();
        public Map<String, String> commands = new HashMap<>();
        public Map<String, String> permissions = new HashMap<>();
        public Map<String, String> admin = new HashMap<>();
        public Map<String, String> vip = new HashMap<>();
        public Map<String, String> misc = new HashMap<>();
    }
}