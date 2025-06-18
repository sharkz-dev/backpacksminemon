package es.minemon.backpacks;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BackpackMenuScreenHandler extends GenericContainerScreenHandler {
    private final ServerPlayerEntity player;
    private final SimpleInventory menuInventory;
    private final int currentPage;
    private final int totalPages;
    private boolean vipUpdated = false; // NUEVO: Flag para evitar updates infinitos

    // Configuración de paginación
    private static final int ITEMS_PER_PAGE = 45; // 5 filas de 9, dejando última fila para controles
    private static final int PREV_BUTTON_SLOT = 45; // Slot 45 (fila 6, columna 1)
    private static final int INFO_SLOT = 49; // Slot 49 (fila 6, columna 5 - centro)
    private static final int NEXT_BUTTON_SLOT = 53; // Slot 53 (fila 6, columna 9)

    public BackpackMenuScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity player) {
        this(syncId, playerInventory, new SimpleInventory(54), player, 0);
    }

    public BackpackMenuScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity player, int page) {
        this(syncId, playerInventory, new SimpleInventory(54), player, page);
    }

    public BackpackMenuScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, ServerPlayerEntity player, int page) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.player = player;
        this.menuInventory = (SimpleInventory) inventory;

        // NUEVO: Actualizar mochilas VIP SOLO UNA VEZ al crear el handler
        if (!vipUpdated) {
            try {
                VipBackpackManager.updatePlayerVipBackpacks(player);
                vipUpdated = true;
            } catch (Exception e) {
                BackpacksMod.LOGGER.warn("Error updating VIP backpacks for " + player.getName().getString() + ": " + e.getMessage());
                // No relanzar la excepción, solo continuar
            }
        }

        // ACTUALIZADO: Usar mochilas filtradas por permisos VIP
        Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(player);
        int totalBackpacks = visibleBackpacks.size();
        this.totalPages = Math.max(1, (int) Math.ceil((double) totalBackpacks / ITEMS_PER_PAGE));
        this.currentPage = Math.max(0, Math.min(page, totalPages - 1));

        populateBackpackMenu();
    }

    private void populateBackpackMenu() {
        // ACTUALIZADO: Usar mochilas filtradas por permisos VIP
        Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(player);

        menuInventory.clear();

        // Convertir a lista para paginación
        var backpacksList = visibleBackpacks.entrySet().stream().toList();
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, backpacksList.size());

        // Añadir mochilas de la página actual
        int slot = 0;
        for (int i = startIndex; i < endIndex && slot < ITEMS_PER_PAGE; i++) {
            var entry = backpacksList.get(i);
            MongoBackpackManager.BackpackData backpack = entry.getValue();

            // Usar icono personalizado optimizado
            ItemStack backpackItem = backpack.getIcon().copy();
            if (backpackItem.isEmpty()) {
                backpackItem = new ItemStack(Items.CHEST);
            }

            // Calcular estadísticas de forma eficiente
            int itemCount = 0;
            int emptySlots = 0;
            for (ItemStack stack : backpack.getInventory()) {
                if (stack.isEmpty()) {
                    emptySlots++;
                } else {
                    itemCount++;
                }
            }

            int totalSlots = backpack.getInventory().size();

            // ACTUALIZADO: Detectar si es mochila VIP para colorearla diferente
            String backpackName = backpack.getName();
            boolean isVipBackpack = isVipBackpack(backpackName);

            String nameColor = isVipBackpack ? getVipColor(backpackName) : "<#c8a8e9>";
            String formattedName = nameColor + backpackName + " <#9a9a9a>(ID: " + entry.getKey() + ")</>";

            backpackItem.set(DataComponentTypes.CUSTOM_NAME, MessageUtils.parseText(formattedName));

            // ACTUALIZADO: Lore diferente para mochilas VIP
            List<Text> lore;
            if (isVipBackpack) {
                String vipRank = getVipRankFromName(backpackName);
                lore = List.of(
                        MessageUtils.parseText("<gradient:#ffd700:#ffaa00>✦ VIP " + vipRank.toUpperCase() + " ✦</gradient>"),
                        LanguageManager.getMessageAsText("loreItems", itemCount, totalSlots),
                        LanguageManager.getMessageAsText("loreEmptySlots", emptySlots),
                        LanguageManager.getMessageAsText("loreUsage", (double) itemCount / totalSlots * 100),
                        LanguageManager.getMessageAsText("loreSlots", totalSlots),
                        Text.literal(""),
                        MessageUtils.parseText("<#ffd700>VIP Exclusive Backpack</>"),
                        MessageUtils.parseText("<#9a9a9a>Requires permission: backpack." + vipRank.toLowerCase() + "</>"),
                        Text.literal(""),
                        LanguageManager.getMessageAsText("loreControls"),
                        LanguageManager.getMessageAsText("loreClickToOpen"),
                        LanguageManager.getMessageAsText("loreRightClickRename"),
                        LanguageManager.getMessageAsText("loreDragToChangeIcon"),
                        LanguageManager.getMessageAsText("loreIconQuantityNote"),
                        Text.literal(""),
                        LanguageManager.getMessageAsText("loreIconChangeWarning"),
                        LanguageManager.getMessageAsText("loreReturnToInventory")
                );
            } else {
                lore = List.of(
                        LanguageManager.getMessageAsText("loreItems", itemCount, totalSlots),
                        LanguageManager.getMessageAsText("loreEmptySlots", emptySlots),
                        LanguageManager.getMessageAsText("loreUsage", (double) itemCount / totalSlots * 100),
                        LanguageManager.getMessageAsText("loreSlots", totalSlots),
                        Text.literal(""),
                        LanguageManager.getMessageAsText("loreControls"),
                        LanguageManager.getMessageAsText("loreClickToOpen"),
                        LanguageManager.getMessageAsText("loreRightClickRename"),
                        LanguageManager.getMessageAsText("loreDragToChangeIcon"),
                        LanguageManager.getMessageAsText("loreIconQuantityNote"),
                        Text.literal(""),
                        LanguageManager.getMessageAsText("loreIconChangeWarning"),
                        LanguageManager.getMessageAsText("loreReturnToInventory")
                );
            }

            backpackItem.set(DataComponentTypes.LORE, new LoreComponent(lore));

            // Guardar ID en NBT
            var customData = backpackItem.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
            customData = customData.apply(nbt -> nbt.putInt("backpack_id", entry.getKey()));
            backpackItem.set(DataComponentTypes.CUSTOM_DATA, customData);

            menuInventory.setStack(slot, backpackItem);
            slot++;
        }

        // Añadir controles de paginación
        addPaginationControls(visibleBackpacks.size());
    }

    /**
     * CORREGIDO: Verifica si una mochila es VIP usando configuración dinámica
     */
    private boolean isVipBackpack(String name) {
        Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
        for (VipBackpackManager.VipRank rank : currentRanks.values()) {
            if (name.toLowerCase().startsWith(rank.getId().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * CORREGIDO: Obtiene el color VIP basado en el nombre de la mochila usando configuración dinámica
     */
    private String getVipColor(String name) {
        Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
        for (VipBackpackManager.VipRank rank : currentRanks.values()) {
            if (name.toLowerCase().startsWith(rank.getId().toLowerCase())) {
                return rank.getPrimaryColor();
            }
        }
        return "<#c8a8e9>"; // Color por defecto
    }

    /**
     * CORREGIDO: Obtiene el rango VIP desde el nombre de la mochila usando configuración dinámica
     */
    private String getVipRankFromName(String name) {
        Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
        for (VipBackpackManager.VipRank rank : currentRanks.values()) {
            if (name.toLowerCase().startsWith(rank.getId().toLowerCase())) {
                return rank.getDisplayName();
            }
        }
        return "Unknown";
    }

    private void addPaginationControls(int totalBackpacks) {
        // Botón Anterior (solo si no estamos en la primera página)
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Items.ARROW);
            prevButton.set(DataComponentTypes.CUSTOM_NAME, LanguageManager.getMessageAsText("navigationPreviousPage"));

            List<Text> prevLore = List.of(
                    LanguageManager.getMessageAsText("navigationGoToPage", currentPage),
                    LanguageManager.getMessageAsText("navigationClickToView")
            );

            prevButton.set(DataComponentTypes.LORE, new LoreComponent(prevLore));

            var prevData = prevButton.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
            prevData = prevData.apply(nbt -> nbt.putBoolean("prev_page", true));
            prevButton.set(DataComponentTypes.CUSTOM_DATA, prevData);

            menuInventory.setStack(PREV_BUTTON_SLOT, prevButton);
        }

        // Información de página (centro) - ACTUALIZADO con info VIP
        ItemStack pageInfo = new ItemStack(Items.BOOK);
        pageInfo.set(DataComponentTypes.CUSTOM_NAME, LanguageManager.getMessageAsText("navigationPageInfo"));

        // Contar mochilas VIP vs normales
        Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(player);
        int vipCount = 0;
        int normalCount = 0;
        for (MongoBackpackManager.BackpackData backpack : visibleBackpacks.values()) {
            if (isVipBackpack(backpack.getName())) {
                vipCount++;
            } else {
                normalCount++;
            }
        }

        List<Text> pageInfoLore = List.of(
                LanguageManager.getMessageAsText("navigationCurrentPage", currentPage + 1, totalPages),
                LanguageManager.getMessageAsText("navigationTotalBackpacks", totalBackpacks),
                MessageUtils.parseText("<#9a9a9a>Normal: <#b8e6b8>" + normalCount + " <#9a9a9a>| VIP: <#ffd700>" + vipCount),
                LanguageManager.getMessageAsText("navigationShowing",
                        currentPage * ITEMS_PER_PAGE + 1,
                        Math.min((currentPage + 1) * ITEMS_PER_PAGE, totalBackpacks)),
                Text.literal(""),
                LanguageManager.getMessageAsText("navigationUseArrows"),
                LanguageManager.getMessageAsText("navigationManyBackpacks"),
                Text.literal(""),
                ConfigManager.getConfig().showBackpackStats ?
                        LanguageManager.getMessageAsText("navigationTotalSlotsUsed", getTotalUsedSlots()) :
                        Text.literal("")
        );

        pageInfo.set(DataComponentTypes.LORE, new LoreComponent(pageInfoLore.stream().filter(text -> !text.getString().isEmpty()).toList()));

        var pageData = pageInfo.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
        pageData = pageData.apply(nbt -> nbt.putBoolean("page_info", true));
        pageInfo.set(DataComponentTypes.CUSTOM_DATA, pageData);

        menuInventory.setStack(INFO_SLOT, pageInfo);

        // Botón Siguiente (solo si no estamos en la última página)
        if (currentPage < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Items.ARROW);
            nextButton.set(DataComponentTypes.CUSTOM_NAME, LanguageManager.getMessageAsText("navigationNextPage"));

            List<Text> nextLore = List.of(
                    LanguageManager.getMessageAsText("navigationGoToPage", currentPage + 2),
                    LanguageManager.getMessageAsText("navigationClickForMore")
            );

            nextButton.set(DataComponentTypes.LORE, new LoreComponent(nextLore));

            var nextData = nextButton.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
            nextData = nextData.apply(nbt -> nbt.putBoolean("next_page", true));
            nextButton.set(DataComponentTypes.CUSTOM_DATA, nextData);

            menuInventory.setStack(NEXT_BUTTON_SLOT, nextButton);
        }
    }

    private int getTotalUsedSlots() {
        Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(player);
        int total = 0;
        for (MongoBackpackManager.BackpackData backpack : visibleBackpacks.values()) {
            for (ItemStack stack : backpack.getInventory()) {
                if (!stack.isEmpty()) {
                    total++;
                }
            }
        }
        return total;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < menuInventory.size()) {
            ItemStack clickedStack = menuInventory.getStack(slotIndex);

            var customData = clickedStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);

            // Verificar si es botón de página anterior
            if (!clickedStack.isEmpty() && customData.copyNbt().contains("prev_page")) {
                if (actionType == SlotActionType.PICKUP && button == 0) {
                    openPage(currentPage - 1);
                }
                return;
            }

            // Verificar si es botón de página siguiente
            if (!clickedStack.isEmpty() && customData.copyNbt().contains("next_page")) {
                if (actionType == SlotActionType.PICKUP && button == 0) {
                    openPage(currentPage + 1);
                }
                return;
            }

            // Verificar si es información de página
            if (!clickedStack.isEmpty() && customData.copyNbt().contains("page_info")) {
                // Mostrar información adicional en chat - ACTUALIZADO con info VIP
                player.sendMessage(Text.literal(""), false);
                player.sendMessage(LanguageManager.getMessageAsText("navigationHeader"), false);
                player.sendMessage(LanguageManager.getMessageAsText("navigationCurrentPage", currentPage + 1, totalPages), false);

                if (currentPage > 0) {
                    player.sendMessage(LanguageManager.getMessageAsText("navigationPreviousInstruction"), false);
                }
                if (currentPage < totalPages - 1) {
                    player.sendMessage(LanguageManager.getMessageAsText("navigationNextInstruction"), false);
                }

                Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(this.player);
                player.sendMessage(LanguageManager.getMessageAsText("navigationTotalInfo", visibleBackpacks.size()), false);

                // NUEVO: Mostrar información VIP
                String vipDiagnostic = VipBackpackManager.getVipDiagnosticInfo((ServerPlayerEntity) player);
                String[] vipLines = vipDiagnostic.split("\n");
                for (String line : vipLines) {
                    if (!line.trim().isEmpty()) {
                        player.sendMessage(MessageUtils.parseText("<#9a9a9a>" + line + "</>"), false);
                    }
                }

                player.sendMessage(Text.literal(""), false);
                return;
            }

            // Verificar si es una mochila
            if (!clickedStack.isEmpty() && customData.copyNbt().contains("backpack_id")) {
                int backpackId = customData.copyNbt().getInt("backpack_id");

                // NUEVO: Verificar acceso a mochila VIP
                if (!VipBackpackManager.canAccessBackpack((ServerPlayerEntity) player, backpackId)) {
                    LanguageManager.sendMessage((ServerPlayerEntity) player, "errorNoPermissionVipBackpack");
                    return;
                }

                ItemStack cursorStack = this.getCursorStack();
                boolean hasItemInCursor = !cursorStack.isEmpty();

                if (actionType == SlotActionType.PICKUP) {
                    if (button == 0) { // Click izquierdo
                        if (hasItemInCursor) {
                            handleIconChange(backpackId, cursorStack);
                            return;
                        } else {
                            ((ServerPlayerEntity) player).closeHandledScreen();
                            CompletableFuture.runAsync(() -> {
                                try {
                                    Thread.sleep(100);
                                    if (BackpacksMod.getServer() != null) {
                                        BackpacksMod.getServer().execute(() -> {
                                            BackpackScreenHandler.openBackpack((ServerPlayerEntity) player, backpackId);
                                        });
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                            return;
                        }
                    } else if (button == 1) { // Click derecho
                        if (!hasItemInCursor) {
                            ((ServerPlayerEntity) player).closeHandledScreen();
                            openRenameInterface((ServerPlayerEntity) player, backpackId);
                            return;
                        } else {
                            handleIconChange(backpackId, cursorStack);
                            return;
                        }
                    }
                }
            }
        }

        // Para clicks en inventario del jugador
        if (slotIndex >= menuInventory.size()) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }
    }

    /**
     * Abre una página específica del menú
     */
    private void openPage(int page) {
        ((ServerPlayerEntity) player).closeHandledScreen();

        // Pequeño delay para evitar conflictos
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
                if (BackpacksMod.getServer() != null) {
                    BackpacksMod.getServer().execute(() -> {
                        openBackpackMenuPage((ServerPlayerEntity) player, page);
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // CORREGIDO: Manejo mejorado de cambio de icono con retorno de item
    private void handleIconChange(int backpackId, ItemStack newIcon) {
        if (!ConfigManager.getConfig().allowCustomIcons) {
            LanguageManager.sendMessage(player, "errorChangingIcon");
            return;
        }

        // Verificar permisos
        if (!LuckPermsManager.canChangeIcon(player)) {
            LuckPermsManager.sendNoPermissionMessage(player, LuckPermsManager.CHANGE_ICON_PERMISSION);
            return;
        }

        // NUEVO: Verificar acceso a mochila VIP
        if (!VipBackpackManager.canAccessBackpack(player, backpackId)) {
            LanguageManager.sendMessage(player, "errorNoPermissionVipBackpack");
            return;
        }

        if (newIcon.isEmpty()) {
            LanguageManager.sendMessage(player, "cannotUseEmptyIcon");
            return;
        }

        try {
            // Crear icono con cantidad 1
            ItemStack iconStack = newIcon.copy();
            iconStack.setCount(1);

            boolean success = BackpackManager.changeBackpackIcon(player.getUuid(), backpackId, iconStack);

            if (success) {
                // CORREGIDO: Devolver el item original al inventario del jugador
                ItemStack returnStack = newIcon.copy();

                // Limpiar cursor
                this.setCursorStack(ItemStack.EMPTY);

                // Intentar poner en inventario del jugador
                if (!player.getInventory().insertStack(returnStack)) {
                    // Si no cabe, dropear cerca del jugador
                    player.dropStack(returnStack);
                    LanguageManager.sendMessage(player, "itemDroppedInventoryFull");
                }

                LanguageManager.sendMessage(player, "iconChangedToItem", newIcon.getItem().getName().getString());
                LanguageManager.sendMessage(player, "originalItemReturned");

                // Actualizar interfaz
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100);
                        if (BackpacksMod.getServer() != null) {
                            BackpacksMod.getServer().execute(() -> {
                                populateBackpackMenu();
                                this.sendContentUpdates();
                            });
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                // Guardar de forma asíncrona
                BackpackManager.forcePlayerSave(player.getUuid());

            } else {
                LanguageManager.sendMessage(player, "errorChangingIcon");
            }

        } catch (Exception e) {
            LanguageManager.sendMessage(player, "errorChangingIcon");
            BackpacksMod.LOGGER.error("Error in icon change", e);
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // CORREGIDO: Prevenir shift+click desde inventario del jugador al menú
        if (slot >= menuInventory.size()) {
            // Es un slot del inventario del jugador, NO permitir shift+click al menú
            return ItemStack.EMPTY;
        }
        // Es un slot del menú, tampoco permitir mover
        return ItemStack.EMPTY;
    }

    // Interfaz de renombrado optimizada con colores hex - ACTUALIZADO
    private void openRenameInterface(ServerPlayerEntity player, int backpackId) {
        // Verificar permisos primero
        if (!LuckPermsManager.canRename(player)) {
            LuckPermsManager.sendNoPermissionMessage(player, LuckPermsManager.RENAME_PERMISSION);
            return;
        }

        // NUEVO: Verificar acceso a mochila VIP
        if (!VipBackpackManager.canAccessBackpack(player, backpackId)) {
            LanguageManager.sendMessage(player, "errorNoPermissionVipBackpack");
            return;
        }

        MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(player.getUuid(), backpackId);
        if (backpack != null) {
            player.sendMessage(Text.literal(""), false);
            player.sendMessage(LanguageManager.getMessageAsText("nameInstructions"), false);
            player.sendMessage(LanguageManager.getMessageAsText("currentNameDisplay", backpack.getName()), false);

            // NUEVO: Advertencia especial para mochilas VIP
            if (isVipBackpack(backpack.getName())) {
                player.sendMessage(MessageUtils.parseText("<#ffd700>⚠ This is a VIP backpack</>"), false);
                player.sendMessage(MessageUtils.parseText("<#9a9a9a>Changing the name may affect VIP identification</>"), false);
            }

            player.sendMessage(Text.literal(""), false);
            player.sendMessage(LanguageManager.getMessageAsText("renameInstructions"), false);
            player.sendMessage(LanguageManager.getMessageAsText("renameTimeLimit"), false);
            player.sendMessage(LanguageManager.getMessageAsText("menuAutoOpen"), false);
            player.sendMessage(Text.literal(""), false);

            BackpackRenameManager.startRename(player.getUuid(), backpackId);
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        return LuckPermsManager.canUseBackpacks(serverPlayer);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        // CORREGIDO: Limpiar cursor al cerrar para evitar items perdidos
        if (!this.getCursorStack().isEmpty()) {
            ItemStack cursorStack = this.getCursorStack();
            this.setCursorStack(ItemStack.EMPTY);

            // Intentar devolver al inventario
            if (!player.getInventory().insertStack(cursorStack)) {
                // Si no cabe, dropear cerca del jugador
                player.dropStack(cursorStack);
            }
        }

        super.onClosed(player);
        BackpackManager.markBackpackDirty(player.getUuid());
        BackpackRenameManager.cancelRename(player.getUuid());
    }

    public static void openBackpackMenu(ServerPlayerEntity player) {
        // CORREGIDO: NO actualizar mochilas VIP aquí para evitar bucle infinito
        // La actualización se hace solo en el constructor del handler
        openBackpackMenuPage(player, 0);
    }

    public static void openBackpackMenuPage(ServerPlayerEntity player, int page) {
        try {
            // CORREGIDO: NO actualizar mochilas VIP aquí para evitar bucle infinito
            player.openHandledScreen(new BackpackMenuScreenHandlerFactory(player, page));
        } catch (Exception e) {
            LanguageManager.sendMessage(player, "errorOpeningBackpack");
            BackpacksMod.LOGGER.error("Error abriendo menú para " + player.getName().getString(), e);
        }
    }

    public static class BackpackMenuScreenHandlerFactory implements net.minecraft.screen.NamedScreenHandlerFactory {
        private final ServerPlayerEntity player;
        private final int page;

        public BackpackMenuScreenHandlerFactory(ServerPlayerEntity player) {
            this(player, 0);
        }

        public BackpackMenuScreenHandlerFactory(ServerPlayerEntity player, int page) {
            this.player = player;
            this.page = page;
        }

        @Override
        public Text getDisplayName() {
            // ACTUALIZADO: Usar mochilas filtradas por permisos VIP
            Map<Integer, MongoBackpackManager.BackpackData> visibleBackpacks = VipBackpackManager.getVisibleBackpacks(player);
            int count = visibleBackpacks.size();
            int totalPages = Math.max(1, (int) Math.ceil((double) count / ITEMS_PER_PAGE));

            // Contar mochilas VIP vs normales para el título
            int vipCount = 0;
            int normalCount = 0;
            for (MongoBackpackManager.BackpackData backpack : visibleBackpacks.values()) {
                String name = backpack.getName();
                boolean isVip = false;
                Map<String, VipBackpackManager.VipRank> currentRanks = VipBackpackManager.getCurrentVipRanks();
                for (VipBackpackManager.VipRank rank : currentRanks.values()) {
                    if (name.toLowerCase().startsWith(rank.getId().toLowerCase())) {
                        isVip = true;
                        break;
                    }
                }
                if (isVip) {
                    vipCount++;
                } else {
                    normalCount++;
                }
            }

            if (totalPages > 1) {
                if (vipCount > 0) {
                    return MessageUtils.parseText("<gradient:#c8a8e9:#9b7ec7>My Backpacks</gradient> <#9a9a9a>(" + normalCount + "+<#ffd700>" + vipCount + "VIP<#9a9a9a>) - Page " + (page + 1) + "/" + totalPages + "</>");
                } else {
                    return LanguageManager.getMessageAsText("menuTitleMain", count + " - Page " + (page + 1) + "/" + totalPages);
                }
            } else {
                if (vipCount > 0) {
                    return MessageUtils.parseText("<gradient:#c8a8e9:#9b7ec7>My Backpacks</gradient> <#9a9a9a>(" + normalCount + "+<#ffd700>" + vipCount + "VIP<#9a9a9a>)</>");
                } else {
                    return LanguageManager.getMessageAsText("menuTitleMain", String.valueOf(count));
                }
            }
        }

        @Override
        public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
            return new BackpackMenuScreenHandler(syncId, inv, this.player, page);
        }
    }
}