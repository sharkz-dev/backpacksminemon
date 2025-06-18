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
 * Sistema de gestión de idiomas para BackpacksMod
 * ACTUALIZADO: Incluye mensajes para mochilas por defecto y VIP customizable
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
            BackpacksMod.LOGGER.info("Language system initialized: " + langPath);
        } catch (Exception e) {
            BackpacksMod.LOGGER.error("Error initializing language system", e);
            languageData = createDefaultLanguageData();
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
                    BackpacksMod.LOGGER.warn("Null language data, creating default");
                    createDefaultLanguageFile();
                } else {
                    languageData = loadedData;
                    // Validar y completar mensajes faltantes
                    validateAndCompleteLanguageData();
                    saveLanguage();
                    BackpacksMod.LOGGER.info("Language data loaded successfully");
                }

            } catch (JsonSyntaxException e) {
                BackpacksMod.LOGGER.error("JSON syntax error in language file", e);
                BackpacksMod.LOGGER.info("Creating new default language file");
                createDefaultLanguageFile();
            } catch (IOException e) {
                BackpacksMod.LOGGER.error("Error reading language file", e);
                languageData = createDefaultLanguageData();
            }
        }
    }

    private static void createDefaultLanguageFile() {
        languageData = createDefaultLanguageData();
        saveLanguage();
        BackpacksMod.LOGGER.info("Default language file created");
    }

    private static LanguageData createDefaultLanguageData() {
        LanguageData data = new LanguageData();

        // === INFORMACIÓN GENERAL ===
        data.info.put("language", "English");
        data.info.put("version", "1.4.0");
        data.info.put("author", "BackpacksMod");
        data.info.put("description", "Complete English language pack for BackpacksMod with customizable VIP and default backpacks");

        // === MENSAJES BÁSICOS ===
        data.messages.put("backpackGiven", "<#d8a2d8>Backpack given successfully</>");
        data.messages.put("backpackRemoved", "<#d8a2d8>Backpack removed successfully</>");
        data.messages.put("backpackOpened", "<#c8a8e9>Opening backpack: <#f5f5a0>%s</>");
        data.messages.put("backpackRenamed", "<#d8a2d8>Backpack renamed to: <#f5f5a0>%s</>");
        data.messages.put("backpackIconChanged", "<#d8a2d8>Backpack icon changed successfully</>");
        data.messages.put("dataLoaded", "<#d8a2d8>Backpack data loaded</>");
        data.messages.put("dataSaved", "<#d8a2d8>Data saved successfully</>");
        data.messages.put("dataSynced", "<#d8a2d8>Data synchronized from database</>");
        data.messages.put("dataLoadedWithCount", "<#d8a2d8>Backpack data loaded successfully. You have <#f5f5a0>%d <#d8a2d8>backpacks</>");
        data.messages.put("errorLoadingRetry", "<#e6a3e6>Error loading your backpack data. Please try again or contact an administrator.</>");

        // === NUEVOS MENSAJES PARA MOCHILAS POR DEFECTO ===
        data.messages.put("defaultBackpacksGiven", "<#d8a2d8>Welcome! You've been given <#f5f5a0>%d <#d8a2d8>starter backpacks</>");
        data.messages.put("defaultBackpacksSkipped", "<#9a9a9a>You already have backpacks, no starter backpacks given</>");
        data.messages.put("defaultBackpacksDisabled", "<#f5d5a0>Default backpacks are disabled on this server</>");
        data.messages.put("defaultBackpacksError", "<#e6a3e6>Error creating your starter backpacks. Please contact an administrator.</>");
        data.messages.put("defaultBackpacksLimitReached", "<#e6a3e6>Cannot give all starter backpacks - you would exceed the maximum limit</>");

        // === MENSAJES DE ERROR ===
        data.messages.put("errorGeneral", "<#e6a3e6>An error occurred: <#f5c2c2>%s</>");
        data.messages.put("errorLoadingData", "<#e6a3e6>Error loading your data. <#f5d5a0>Please try again.</>");
        data.messages.put("errorSavingData", "<#e6a3e6>Error saving data</>");
        data.messages.put("errorSyncingData", "<#e6a3e6>Error synchronizing data between servers</>");
        data.messages.put("errorOpeningBackpack", "<#e6a3e6>Error opening backpack</>");
        data.messages.put("errorBackpackNotFound", "<#e6a3e6>You don't have access to that backpack</>");
        data.messages.put("errorBackpackExists", "<#e6a3e6>You already have a backpack with ID <#f5f5a0>%d</>");
        data.messages.put("errorMaxBackpacks", "<#e6a3e6>You have reached the maximum backpack limit <#f5f5a0>(%d)</>");
        data.messages.put("errorInvalidId", "<#e6a3e6>Invalid backpack ID</>");
        data.messages.put("errorPlayerNotFound", "<#e6a3e6>Player not found</>");
        data.messages.put("errorPlayerOnly", "<#e6a3e6>Only players can use this command</>");
        data.messages.put("errorDataCorruption", "<#e6a3e6>Data corruption detected. <#f5d5a0>A backup has been created.</>");
        data.messages.put("errorInvalidSlots", "<#e6a3e6>Invalid slot count. Must be a multiple of 9 (9-54)</>");
        data.messages.put("errorRenamingBackpack", "<#e6a3e6>Error renaming backpack</>");
        data.messages.put("errorChangingIcon", "<#e6a3e6>Error changing backpack icon</>");

        // === MENSAJES DE PERMISOS (ACTUALIZADOS) ===
        data.messages.put("errorNoPermission", "<#e6a3e6>You don't have permission to use this command</>");
        data.messages.put("errorNoPermissionLuckPerms", "<#e6a3e6>You don't have permission: <#f5c2c2>%s</>");
        data.messages.put("errorNoPermissionFallback", "<#e6a3e6>You need administrator privileges to use this command</>");
        data.messages.put("errorNoPermissionVipBackpack", "<#e6a3e6>You don't have permission to access this VIP backpack</>");
        data.messages.put("permissionRequired", "<#f5d5a0>Permission required: <#f5c2c2>%s</>");
        data.messages.put("permissionDenied", "<#e6a3e6>Access denied - insufficient permissions</>");
        data.messages.put("permissionGranted", "<#d8a2d8>Permission granted: <#b8e6b8>%s</>");
        data.messages.put("permissionRemoved", "<#f5d5a0>Permission removed: <#f5c2c2>%s</>");
        data.messages.put("permissionsReloaded", "<#d8a2d8>Permissions system reloaded: <#b8e6b8>%s</>");
        data.messages.put("errorReloadingPermissions", "<#e6a3e6>Error reloading permissions: <#f5c2c2>%s</>");
        data.messages.put("luckPermsNotAvailable", "<#f5d5a0>LuckPerms is not available. Using fallback permission system.</>");
        data.messages.put("luckPermsActive", "<#d8a2d8>LuckPerms is active and functioning</>");
        data.messages.put("fallbackPermissions", "<#f5d5a0>Using fallback OP-based permission system</>");

        // === MENSAJES VIP (ACTUALIZADOS Y EXPANDIDOS) ===
        data.messages.put("vipBackpackCreated", "<gradient:#ffd700:#ffaa00>✦ VIP %s backpack created: %s ✦</gradient>");
        data.messages.put("vipBackpacksUpdated", "<#ffd700>VIP backpacks updated! You now have access to %d VIP backpacks</>");
        data.messages.put("vipBackpacksHidden", "<#f5d5a0>Some VIP backpacks are now hidden due to permission changes</>");
        data.messages.put("vipBackpackRestored", "<#ffd700>VIP backpack restored: %s (items preserved)</>");
        data.messages.put("vipPermissionGranted", "<gradient:#ffd700:#ffaa00>✦ VIP %s permission granted! ✦</gradient>");
        data.messages.put("vipPermissionRevoked", "<#f5d5a0>VIP %s permission revoked (backpacks hidden, items preserved)</>");
        data.messages.put("vipBackpackAccess", "<#ffd700>Accessing VIP %s backpack</>");
        data.messages.put("vipStatusChanged", "<#ffd700>VIP status updated. Check your backpacks menu!</>");
        data.messages.put("vipConfigUpdated", "<#ffd700>VIP configuration updated - backpacks will be synchronized</>");
        data.messages.put("vipCustomIconSet", "<#ffd700>VIP backpack icon updated to custom configuration</>");

        // === MENSAJES VIP PARA LÍMITES ===
        data.messages.put("vipBackpackLimitReached", "<#e6a3e6>⚠ VIP Backpack Limit Warning</>\n<#f5d5a0>Cannot create all VIP backpacks due to maximum limit.</>\n<#9a9a9a>Max allowed: <#f5f5a0>%d</> <#9a9a9a>| Current: <#f5f5a0>%d</> <#9a9a9a>| VIP needed: <#f5f5a0>%d</>\n<#f5d5a0>Contact an administrator to increase your backpack limit.</>");
        data.messages.put("vipConfigurationInvalid", "<#e6a3e6>⚠ VIP configuration issue detected</>\n<#f5d5a0>The VIP system would exceed the maximum backpack limit.</>\n<#9a9a9a>Consider adjusting VIP backpack counts or increasing the maximum limit.</>");
        data.messages.put("vipPartialCreation", "<#f5d5a0>Only some VIP backpacks could be created due to limit restrictions</>");
        data.messages.put("vipSkippedDueToLimit", "<#9a9a9a>Some VIP %s backpacks were skipped to stay within limits</>");

        // === MENSAJES INFORMATIVOS ===
        data.messages.put("backpackReceived", "<#d8a2d8>You received backpack: <#f5f5a0>%s</>");
        data.messages.put("backpackLost", "<#e6a3e6>Your backpack ID <#f5f5a0>%d <#e6a3e6>has been removed</>");
        data.messages.put("noBackpacks", "<gradient:#d8a2d8:#c8a8e9>You don't have any available backpacks</gradient>");
        data.messages.put("connecting", "<#c8a8e9>Connecting...</>");
        data.messages.put("loadingData", "<#c8a8e9>Loading your data...</>");
        data.messages.put("syncingData", "<#c8a8e9>Synchronizing data...</>");
        data.messages.put("saving", "<#c8a8e9>Saving...</>");
        data.messages.put("renameInstructions", "<#f5f5a0>Type the new name for your backpack in chat</>");
        data.messages.put("renameTimeout", "<#e6a3e6>Timeout for renaming backpack</>");
        data.messages.put("iconChangeInstructions", "<#f5f5a0>Drag an item onto the backpack icon to change it</>");
        data.messages.put("backpackSuccessMessage", "<#d8a2d8>Backpack ID <#f5f5a0>%d <#d8a2d8>renamed successfully to: <#f5f5a0>%s</>");
        data.messages.put("nameCannotBeEmpty", "<#e6a3e6>The name cannot be empty</>");
        data.messages.put("nameTooLong", "<#e6a3e6>The name is too long (maximum 50 characters)</>");
        data.messages.put("typeShorterName", "<#f5d5a0>Please type a shorter name</>");
        data.messages.put("typeNewName", "<#f5d5a0>Please type a new name</>");
        data.messages.put("renameSuccessful", "<#d8a2d8>Backpack renamed successfully to: <#f5f5a0>%s</>");
        data.messages.put("tryAgainLater", "<#f5d5a0>Please try again later</>");
        data.messages.put("useBackpacksCommand", "<#f5d5a0>Use /%s to open your backpacks menu</>");
        data.messages.put("menuUpdated", "<#9a9a9a>Menu updated successfully</>");
        data.messages.put("cannotUseEmptyIcon", "<#e6a3e6>You cannot use an empty item as an icon</>");
        data.messages.put("itemDroppedInventoryFull", "<#f5d5a0>Item dropped near you because your inventory is full</>");
        data.messages.put("iconChangedToItem", "<#d8a2d8>Icon changed to: <#f5f5a0>%s</>");
        data.messages.put("originalItemReturned", "<#9a9a9a>The original item has been returned to your inventory</>");
        data.messages.put("emptyCursorRequired", "<#e6a3e6>You need to have an empty cursor to edit. Drop the item first.</>");
        data.messages.put("emptyCursorForRename", "<#e6a3e6>You need to have an empty cursor to rename. Drop the item first.</>");

        // === MENSAJES DE ADMINISTRACIÓN ===
        data.messages.put("adminViewOpened", "<#c8a8e9>Opened admin view for player: <#b8e6b8>%s</>");
        data.messages.put("adminEditingBackpack", "<#c8a8e9>Editing backpack <#f5f5a0>%d <#c8a8e9>of player <#b8e6b8>%s</>");
        data.messages.put("adminChangesAutosaved", "<#9a9a9a>Admin changes are automatically saved</>");
        data.messages.put("adminEditCompleted", "<#d8a2d8>Admin edit session completed</>");
        data.messages.put("adminBackpackDeleted", "<#e6a3e6>Admin deleted backpack ID <#f5f5a0>%d <#e6a3e6>from <#b8e6b8>%s</>");
        data.messages.put("adminEditModeWarning", "<#e6a3e6>⚠ ADMIN EDIT MODE ⚠</>");
        data.messages.put("adminAboutToEdit", "<#f5d5a0>You are about to edit another player's backpack</>");
        data.messages.put("adminEditingPlayer", "<#f5f5a0>Player: <#b8e6b8>%s</>");
        data.messages.put("adminEditingBackpackInfo", "<#f5f5a0>Backpack: <#c8a8e9>%s <#9a9a9a>(ID: %d)</>");
        data.messages.put("adminChangesAutomatic", "<#9a9a9a>Changes will be saved automatically</>");
        data.messages.put("adminSlotReserved", "<#f5d5a0>The last slot contains admin controls</>");
        data.messages.put("adminEditModeHeader", "<#e6a3e6>=== ADMIN EDIT MODE ===</>");
        data.messages.put("adminOwnerInfo", "<#f5f5a0>Owner: <#b8e6b8>%s</>");
        data.messages.put("adminControlsHeader", "<#e6a3e6>Admin Controls:</>");
        data.messages.put("adminLeftClickEdit", "<#f5d5a0>Left click to edit backpack</>");
        data.messages.put("adminRightClickRename", "<#f5d5a0>Right click to rename</>");
        data.messages.put("adminModeWarning", "<#e6a3e6>⚠ Changes are irreversible</>");
        data.messages.put("adminPanelTitle", "<#e6a3e6>Admin Panel</>");
        data.messages.put("adminPlayerInfo", "<#f5f5a0>Player: <#b8e6b8>%s</>");
        data.messages.put("adminActionsHeader", "<#e6a3e6>Available Actions:</>");
        data.messages.put("adminCanEdit", "<#f5d5a0>• Edit any backpack inventory</>");
        data.messages.put("adminCanAddRemove", "<#f5d5a0>• Add or remove backpacks</>");
        data.messages.put("adminCanRename", "<#f5d5a0>• Rename any backpack</>");
        data.messages.put("adminWarningHeader", "<#e6a3e6>⚠ CAUTION:</>");
        data.messages.put("adminCannotUndo", "<#f5c2c2>• Changes cannot be undone</>");
        data.messages.put("adminPlayerUUID", "<#9a9a9a>UUID: %s</>");

        // === MENSAJES DE ESTADÍSTICAS ===
        data.messages.put("statsHeader", "<gradient:#d8a2d8:#c8a8e9>=== %s Statistics ===</gradient>");
        data.messages.put("statsTotalBackpacks", "<#f5f5a0>Total backpacks: <#b8e6b8>%d</>");
        data.messages.put("statsItemsStored", "<#f5f5a0>Items stored: <#b8e6b8>%d</>");
        data.messages.put("statsEmptySlots", "<#f5f5a0>Empty slots: <#f5c2c2>%d</>");
        data.messages.put("statsTotalSlots", "<#f5f5a0>Total slots: <#b8e6b8>%d</>");
        data.messages.put("statsUsagePercent", "<#f5f5a0>Space usage: <#c8a8e9>%.1f%%</>");
        data.messages.put("playerNameDisplay", "<#f5f5a0>Player: <#b8e6b8>%s</>");
        data.messages.put("uuidDisplay", "<#9a9a9a>UUID: %s</>");
        data.messages.put("backpackIdDisplay", "<#f5f5a0>Backpack ID: <#b8e6b8>%d</>");
        data.messages.put("currentNameDisplay", "<#f5f5a0>Current name: <#c8a8e9>%s</>");

        // === MENSAJES DE BACKUP ===
        data.messages.put("backupCreating", "<#c8a8e9>Creating backup...</>");
        data.messages.put("backupCreated", "<#d8a2d8>Backup created successfully</>");
        data.messages.put("backupEmergency", "<#e6a3e6>Executing emergency backup</>");
        data.messages.put("backupFailed", "<#e6a3e6>Error creating backup</>");
        data.messages.put("backupForced", "<#d8a2d8>Forced save completed</>");
        data.messages.put("backupList", "<gradient:#d8a2d8:#c8a8e9>=== Available Backups ===</gradient>");
        data.messages.put("backupNone", "<#f5d5a0>No backups available</>");

        // === GUI - LORE DE ITEMS ===
        data.gui.put("loreItems", "<#9a9a9a>Items: <#b8e6b8>%d<#9a9a9a>/<#b8e6b8>%d</>");
        data.gui.put("loreEmptySlots", "<#9a9a9a>Empty slots: <#f5c2c2>%d</>");
        data.gui.put("loreUsage", "<#9a9a9a>Usage: <#c8a8e9>%.1f%%</>");
        data.gui.put("loreSlots", "<#9a9a9a>Capacity: <#f5f5a0>%d slots</>");
        data.gui.put("loreControls", "<#f5d5a0>Controls:</>");
        data.gui.put("loreClickToOpen", "<#f5d5a0>Left click to open</>");
        data.gui.put("loreRightClickRename", "<#f5d5a0>Right click to rename</>");
        data.gui.put("loreDragToChangeIcon", "<#f5d5a0>Drag item to change icon</>");
        data.gui.put("loreBackpackInfo", "<#f5d5a0>Backpack Information</>");
        data.gui.put("loreMaxBackpacks", "<#f5f5a0>You can get more backpacks</>");
        data.gui.put("loreAskAdmin", "<#f5f5a0>by asking an administrator</>");
        data.gui.put("loreSyncedFromDB", "<#9a9a9a>Synchronized from DB</>");
        data.gui.put("loreLastSync", "<#9a9a9a>Last sync: <#c8a8e9>%s</>");
        data.gui.put("loreCustomIcon", "<#9a9a9a>Custom icon: <#b8e6b8>%s</>");
        data.gui.put("loreDefaultIcon", "<#9a9a9a>Default icon</>");
        data.gui.put("loreIconQuantityNote", "<#9a9a9a>Only 1 item will be used as icon</>");
        data.gui.put("loreIconChangeWarning", "<#f5c2c2>⚠ Item will be returned to inventory</>");
        data.gui.put("loreReturnToInventory", "<#9a9a9a>after changing the icon</>");

        // === NUEVOS: LORE VIP CUSTOMIZABLE ===
        data.gui.put("loreVipBackpack", "<gradient:#ffd700:#ffaa00>✦ VIP %s Backpack ✦</gradient>");
        data.gui.put("loreVipExclusive", "<#ffd700>VIP Exclusive Storage</>");
        data.gui.put("loreVipPermissionRequired", "<#9a9a9a>Requires: <#ffd700>%s</>");
        data.gui.put("loreVipCustomSlots", "<#9a9a9a>VIP Slots: <#ffd700>%d</>");
        data.gui.put("loreVipCustomIcon", "<#9a9a9a>VIP Icon: <#ffd700>%s</>");

        // === GUI - TÍTULOS DE MENÚS ===
        data.gui.put("menuTitleMain", "<gradient:#c8a8e9:#9b7ec7>My Backpacks (%s)</gradient>");
        data.gui.put("menuTitleBackpack", "<#f5f5a0>%s <#9a9a9a>(%d/%d)</>");
        data.gui.put("menuTitleAdminView", "<#e6a3e6>[ADMIN] <#f5f5a0>%s's Backpacks</>");
        data.gui.put("menuTitleAdminEdit", "<#e6a3e6>[ADMIN EDIT] <#f5f5a0>%s</>");

        // === GUI - NAVEGACIÓN ===
        data.gui.put("navigationPreviousPage", "<#c8a8e9>← Previous Page</>");
        data.gui.put("navigationNextPage", "<#c8a8e9>Next Page →</>");
        data.gui.put("navigationPageInfo", "<#f5d5a0>Page Information</>");
        data.gui.put("navigationCurrentPage", "<#f5f5a0>Page: <#f5f5a0>%d <#9a9a9a>/ <#f5f5a0>%d</>");
        data.gui.put("navigationTotalBackpacks", "<#f5f5a0>Total backpacks: <#b8e6b8>%d</>");
        data.gui.put("navigationShowing", "<#f5f5a0>Showing: <#b8e6b8>%d <#9a9a9a>- <#b8e6b8>%d</>");
        data.gui.put("navigationUseArrows", "<#9a9a9a>Use the arrow buttons to navigate</>");
        data.gui.put("navigationManyBackpacks", "<#9a9a9a>between pages if you have many backpacks</>");
        data.gui.put("navigationGoToPage", "<#f5f5a0>Go to page %d</>");
        data.gui.put("navigationClickToView", "<#9a9a9a>Click to view</>");
        data.gui.put("navigationClickForMore", "<#9a9a9a>Click for more backpacks</>");
        data.gui.put("navigationHeader", "<gradient:#c8a8e9:#9b7ec7>=== Navigation Help ===</gradient>");
        data.gui.put("navigationPreviousInstruction", "<#f5d5a0>Click the left arrow to go back</>");
        data.gui.put("navigationNextInstruction", "<#f5d5a0>Click the right arrow to continue</>");
        data.gui.put("navigationTotalInfo", "<#f5f5a0>You have %d backpacks total</>");
        data.gui.put("navigationTotalSlotsUsed", "<#9a9a9a>Total slots used: <#b8e6b8>%d</>");

        // === GUI - BOTONES ===
        data.gui.put("backButtonToMenu", "<#e6a3e6>← Back to Backpacks Menu</>");
        data.gui.put("backButtonToAdminView", "<#e6a3e6>← Back to Admin View</>");
        data.gui.put("backButtonDescription", "<#9a9a9a>Click to return to the</>");
        data.gui.put("backButtonMainMenu", "<#9a9a9a>main backpacks menu</>");
        data.gui.put("backButtonAdminOverview", "<#9a9a9a>backpack overview</>");
        data.gui.put("backButtonNotStored", "<#f5d5a0>This item is not stored</>");
        data.gui.put("backButtonInBackpack", "<#f5d5a0>in your backpack</>");
        data.gui.put("backButtonCannotRemove", "<#f5d5a0>This button cannot be removed</>");
        data.gui.put("backButtonAdminControl", "<#e6a3e6>⚠ ADMIN CONTROL</>");
        data.gui.put("backButtonSlotNotSaved", "<#9a9a9a>Changes to this slot won't be saved</>");

        // === COMANDOS ===
        data.commands.put("commandUsage", "<#f5f5a0>Usage: <#f5f5a0>%s</>");
        data.commands.put("commandBackpackGive", "<#f5f5a0>/<#c8a8e9>%s <#f5f5a0>give <player> <name> <slots></>");
        data.commands.put("commandBackpackRemove", "<#f5f5a0>/<#c8a8e9>%s <#f5f5a0>remove <player> <id></>");
        data.commands.put("commandBackpackAdminView", "<#f5f5a0>/<#c8a8e9>%s <#f5f5a0>admin view <player></>");
        data.commands.put("commandSlotsInfo", "<#f5f5a0>Available slots: <#b8e6b8>9, 18, 27, 36, 45, 54</>");
        data.commands.put("commandIdAutoGenerated", "<#f5f5a0>Auto-generated ID: <#b8e6b8>%d</>");
        data.commands.put("commandHelpHeader", "<gradient:#d8a2d8:#c8a8e9>=== Backpack Commands ===</gradient>");
        data.commands.put("commandHelpPlayer", "<#f5f5a0>/<#c8a8e9>%s <#9a9a9a>- Open your backpacks menu</>");
        data.commands.put("commandHelpRename", "<#f5f5a0>/rename-backpack <id> <name> <#9a9a9a>- Rename your backpack</>");
        data.commands.put("commandHelpAdmin", "<#f5d5a0>Admin Commands:</>");
        data.commands.put("commandSlotCountInvalid", "<#e6a3e6>Slot count must be a multiple of 9 (9-54)</>");
        data.commands.put("commandNameEmpty", "<#e6a3e6>Name cannot be empty</>");
        data.commands.put("commandNameTooLong", "<#e6a3e6>Name too long (maximum 50 characters)</>");
        data.commands.put("useCommand", "<#f5d5a0>Use the command:</>");
        data.commands.put("renameCommandExample", "<#f5f5a0>/%s admin rename %s %d \"New Name\"</>");

        // === NUEVOS COMANDOS PARA CONFIGURACIÓN ===
        data.commands.put("configDefaultBackpacks", "<#f5f5a0>Default backpacks: <#b8e6b8>%s</>");
        data.commands.put("configDefaultCount", "<#f5f5a0>Default count: <#b8e6b8>%d</>");
        data.commands.put("configDefaultSlots", "<#f5f5a0>Default slots: <#b8e6b8>%d</>");
        data.commands.put("configDefaultPattern", "<#f5f5a0>Name pattern: <#b8e6b8>%s</>");
        data.commands.put("configDefaultIcon", "<#f5f5a0>Default icon: <#b8e6b8>%s</>");
        data.commands.put("configVipRankEnabled", "<#f5f5a0>VIP %s: <#b8e6b8>%s</>");
        data.commands.put("configVipRankDisabled", "<#f5f5a0>VIP %s: <#f5c2c2>Disabled</>");

        // === PERMISOS (ACTUALIZADOS CON VIP) ===
        data.permissions.put("permissionSystemInfo", "<#f5f5a0>Permission System: <#b8e6b8>%s</>");
        data.permissions.put("luckPermsVersion", "<#f5f5a0>LuckPerms Version: <#b8e6b8>%s</>");
        data.permissions.put("fallbackSystem", "<#f5d5a0>Using fallback OP-based permission system</>");
        data.permissions.put("permissionCheckSuccess", "<#d8a2d8>Permission check successful: <#b8e6b8>%s</>");
        data.permissions.put("permissionCheckFailed", "<#e6a3e6>Permission check failed: <#f5c2c2>%s</>");
        data.permissions.put("userPermissions", "<#f5f5a0>=== User Permissions ===</>");
        data.permissions.put("adminPermissions", "<#e6a3e6>=== Admin Permissions ===</>");
        data.permissions.put("vipPermissions", "<#ffd700>=== VIP Permissions ===</>");
        data.permissions.put("permissionNode", "<#9a9a9a>• <#f5f5a0>%s <#9a9a9a>- %s</>");
        data.permissions.put("permissionGrantExample", "<#f5d5a0>Example: <#b8e6b8>/lp user %s permission set %s true</>");
        data.permissions.put("permissionGroupExample", "<#f5d5a0>Example: <#b8e6b8>/lp group %s permission set %s true</>");
        data.permissions.put("noLuckPermsFound", "<#f5d5a0>LuckPerms not detected. Install LuckPerms for granular permission control.</>");
        data.permissions.put("permissionDatabaseError", "<#e6a3e6>Error accessing permission database</>");
        data.permissions.put("permissionUserNotFound", "<#e6a3e6>Permission user not found: <#f5c2c2>%s</>");

        // === ADMIN ===
        data.admin.put("adminControlRestored", "<#f5d5a0>Admin control button restored. Last slot is reserved.</>");
        data.admin.put("adminSlotReserved", "<#f5d5a0>Last slot has admin controls - cannot be modified</>");
        data.admin.put("adminChangesAutomatic", "<#9a9a9a>All changes will be saved automatically</>");

        // === VIP (ACTUALIZADOS CON CONFIGURACIÓN CUSTOMIZABLE) ===
        data.vip.put("vipBackpackCount", "<#ffd700>VIP %s backpacks: %d</>");
        data.vip.put("vipPermissionRequired", "<#ffd700>Requires permission: <#f5f5a0>%s</>");
        data.vip.put("vipBackpackCreationSuccess", "<gradient:#ffd700:#ffaa00>✦ %d VIP %s backpacks created! ✦</gradient>");
        data.vip.put("vipBackpackVisibilityUpdate", "<#ffd700>VIP backpack visibility updated</>");
        data.vip.put("vipStatusActive", "<gradient:#ffd700:#ffaa00>✦ VIP %s Active ✦</gradient>");
        data.vip.put("vipStatusInactive", "<#9a9a9a>VIP %s Inactive</>");
        data.vip.put("vipBackpackAccess", "<#ffd700>Accessing VIP backpack</>");
        data.vip.put("vipBackpackDenied", "<#e6a3e6>VIP permission required</>");
        data.vip.put("vipSystemInfo", "<#ffd700>=== VIP Backpack System ===</>");
        data.vip.put("vipTotalVipBackpacks", "<#ffd700>Total VIP backpacks: %d</>");
        data.vip.put("vipActiveRanks", "<#ffd700>Active VIP ranks: %s</>");
        data.vip.put("vipLimitInfo", "<#ffd700>VIP Limit Info: <#9a9a9a>Max possible VIP backpacks: <#f5f5a0>%d</>");
        data.vip.put("vipConfigValid", "<#b8e6b8>✓ VIP configuration is valid</>");
        data.vip.put("vipConfigInvalid", "<#e6a3e6>✗ VIP configuration exceeds limits</>");

        // === NUEVOS MENSAJES VIP PARA CONFIGURACIÓN CUSTOMIZABLE ===
        data.vip.put("vipRankCustomized", "<#ffd700>VIP %s configured: %d backpacks, %d slots each</>");
        data.vip.put("vipIconUpdated", "<#ffd700>VIP %s icon updated to: %s</>");
        data.vip.put("vipNamePatternUpdated", "<#ffd700>VIP %s name pattern updated to: %s</>");
        data.vip.put("vipSlotsUpdated", "<#ffd700>VIP %s slots updated to: %d per backpack</>");
        data.vip.put("vipCountUpdated", "<#ffd700>VIP %s count updated to: %d backpacks</>");
        data.vip.put("vipRankEnabled", "<#b8e6b8>✓ VIP %s rank enabled</>");
        data.vip.put("vipRankDisabled", "<#f5c2c2>✗ VIP %s rank disabled</>");
        data.vip.put("vipConfigReloaded", "<#ffd700>VIP configuration reloaded - all players will be synchronized</>");

        // === MISCELÁNEOS ===
        data.misc.put("welcome", "<#f5f5a0>Welcome! Use <#b8e6b8>/%s <#f5f5a0>to view your backpacks</>");
        data.misc.put("firstTime", "<#f5f5a0>Seems like it's your first time. <#c8a8e9>Ask an admin for backpacks!</>");
        data.misc.put("maintenance", "<#e6a3e6>Backpack system under maintenance</>");
        data.misc.put("serverRestart", "<#f5d5a0>Server will restart soon. <#b8e6b8>Your data is safe.</>");
        data.misc.put("dataCorrupted", "<#e6a3e6>Your data seems corrupted. <#f5d5a0>A backup has been created.</>");
        data.misc.put("crossServerSync", "<#c8a8e9>Data synchronized between servers</>");
        data.misc.put("itemsRestored", "<#d8a2d8>Your items have been restored from database</>");
        data.misc.put("syncInProgress", "<#c8a8e9>Synchronization in progress...</>");
        data.misc.put("iconChanged", "<#d8a2d8>Icon changed to: <#f5f5a0>%s</>");
        data.misc.put("iconResetToDefault", "<#f5d5a0>Icon reset to default</>");
        data.misc.put("menuUpdated", "<#9a9a9a>Menu updated successfully</>");
        data.misc.put("adminPermissionRequired", "<#e6a3e6>Administrator permission level <#f5f5a0>%d <#e6a3e6>required</>");
        data.misc.put("commandsChanged", "<#f5f5a0>Commands updated! Main: <#b8e6b8>/%s <#f5f5a0>| Player: <#b8e6b8>/%s</>");
        data.misc.put("nameInstructions", "<gradient:#c8a8e9:#9b7ec7>=== RENAME BACKPACK ===</gradient>");
        data.misc.put("renameInstructions", "<#f5f5a0>Type the new name in chat (you have 30 seconds)</>");
        data.misc.put("renameTimeLimit", "<#9a9a9a>Maximum 50 characters, minimum 1</>");
        data.misc.put("menuAutoOpen", "<#9a9a9a>The menu will reopen automatically</>");

        // === NUEVOS MISCELÁNEOS PARA CONFIGURACIÓN ===
        data.misc.put("configurationUpdated", "<#d8a2d8>Configuration updated successfully</>");
        data.misc.put("configurationReloaded", "<#d8a2d8>Configuration reloaded from file</>");
        data.misc.put("configurationSaved", "<#d8a2d8>Configuration saved to file</>");
        data.misc.put("configurationInvalid", "<#e6a3e6>Invalid configuration detected</>");
        data.misc.put("configurationReset", "<#f5d5a0>Configuration reset to defaults</>");
        data.misc.put("playersSynchronized", "<#d8a2d8>All online players synchronized with new configuration</>");

        return data;
    }

    private static void validateAndCompleteLanguageData() {
        // Crear datos por defecto para comparar
        LanguageData defaultData = createDefaultLanguageData();

        // Completar categorías faltantes
        if (languageData.info == null) languageData.info = new HashMap<>();
        if (languageData.messages == null) languageData.messages = new HashMap<>();
        if (languageData.gui == null) languageData.gui = new HashMap<>();
        if (languageData.commands == null) languageData.commands = new HashMap<>();
        if (languageData.permissions == null) languageData.permissions = new HashMap<>();
        if (languageData.admin == null) languageData.admin = new HashMap<>();
        if (languageData.vip == null) languageData.vip = new HashMap<>();
        if (languageData.misc == null) languageData.misc = new HashMap<>();

        // Agregar mensajes faltantes de cada categoría
        addMissingMessages(languageData.info, defaultData.info);
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
                BackpacksMod.LOGGER.info("Added missing language key: " + entry.getKey());
            }
        }
    }

    public static void saveLanguage() {
        try (FileWriter writer = new FileWriter(langPath.toFile())) {
            GSON.toJson(languageData, writer);
        } catch (IOException e) {
            BackpacksMod.LOGGER.error("Error saving language file", e);
        }
    }

    public static void reloadLanguage() {
        BackpacksMod.LOGGER.info("Reloading language data...");
        loadLanguage();
        BackpacksMod.LOGGER.info("Language data reloaded successfully");
    }

    // === MÉTODOS PRINCIPALES DE ACCESO ===

    public static String getMessage(String key, Object... args) {
        String message = getMessageRaw(key);

        if (args.length > 0) {
            try {
                return String.format(message, args);
            } catch (Exception e) {
                BackpacksMod.LOGGER.warn("Error formatting message '{}' with args: {}", key, e.getMessage());
                return message;
            }
        }
        return message;
    }

    public static String getMessageRaw(String key) {
        if (languageData == null) {
            return "[Language not loaded: " + key + "]";
        }

        // Buscar en todas las categorías
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

        // Fallback
        BackpacksMod.LOGGER.warn("Missing language key: " + key);
        return "<#9a9a9a>[Message not found: " + key + "]</>";
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

    // === MÉTODOS DE INFORMACIÓN ===

    public static String getLanguageInfo() {
        if (languageData == null || languageData.info == null) {
            return "Language: Unknown";
        }

        String language = languageData.info.getOrDefault("language", "Unknown");
        String version = languageData.info.getOrDefault("version", "Unknown");
        String author = languageData.info.getOrDefault("author", "Unknown");

        return String.format("Language: %s (v%s) by %s", language, version, author);
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

    // === CLASE DE DATOS ACTUALIZADA ===

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