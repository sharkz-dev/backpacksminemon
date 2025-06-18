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

public class BackpackAdminViewScreenHandler extends GenericContainerScreenHandler {
    private final ServerPlayerEntity admin;
    private final ServerPlayerEntity targetPlayer;
    private final SimpleInventory menuInventory;

    public BackpackAdminViewScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity admin, ServerPlayerEntity targetPlayer) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(54), 6);
        this.admin = admin;
        this.targetPlayer = targetPlayer;
        this.menuInventory = new SimpleInventory(54);

        // Copiar el inventario creado al campo de la clase padre
        for (int i = 0; i < 54; i++) {
            this.getSlot(i).inventory.setStack(i, ItemStack.EMPTY);
        }

        populateAdminMenu();
    }

    private void populateAdminMenu() {
        CompletableFuture.runAsync(() -> {
            try {
                MongoBackpackManager.PlayerBackpacks playerBackpacks = BackpackManager.getPlayerBackpacks(targetPlayer.getUuid());

                if (BackpacksMod.getServer() != null) {
                    BackpacksMod.getServer().execute(() -> populateMenuWithData(playerBackpacks));
                }

            } catch (Exception e) {
                if (BackpacksMod.getServer() != null) {
                    BackpacksMod.getServer().execute(() -> {
                        // ACTUALIZADO: Usar sistema de idiomas
                        LanguageManager.sendMessage(admin, "errorLoadingData");
                        admin.closeHandledScreen();
                    });
                }
            }
        });
    }

    private void populateMenuWithData(MongoBackpackManager.PlayerBackpacks playerBackpacks) {
        Map<Integer, MongoBackpackManager.BackpackData> backpacks = playerBackpacks.getAllBackpacks();

        // Limpiar inventario usando los slots
        for (int i = 0; i < 54; i++) {
            this.getSlot(i).setStack(ItemStack.EMPTY);
        }

        String playerName = targetPlayer.getName().getString();

        int slot = 0;
        for (Map.Entry<Integer, MongoBackpackManager.BackpackData> entry : backpacks.entrySet()) {
            if (slot >= 54) break;

            MongoBackpackManager.BackpackData backpack = entry.getValue();

            ItemStack backpackItem = backpack.getIcon().copy();
            if (backpackItem.isEmpty()) {
                backpackItem = new ItemStack(Items.CHEST);
            }

            // Calcular estadísticas
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

            String backpackName = MessageUtils.parseColors("<#ff4444>[ADMIN]</> <#ffff44>" + backpack.getName() + "</> <#888888>(ID: " + entry.getKey() + ")</>");
            backpackItem.set(DataComponentTypes.CUSTOM_NAME, MessageUtils.parseText(backpackName));

            // ACTUALIZADO: Usar sistema de idiomas para lore de admin
            List<Text> lore = List.of(
                    LanguageManager.getMessageAsText("adminEditModeHeader"),
                    LanguageManager.getMessageAsText("adminOwnerInfo", playerName),
                    LanguageManager.getMessageAsText("loreItems", itemCount, totalSlots),
                    LanguageManager.getMessageAsText("loreEmptySlots", emptySlots),
                    LanguageManager.getMessageAsText("loreUsage", (double) itemCount / totalSlots * 100),
                    LanguageManager.getMessageAsText("loreSlots", totalSlots),
                    Text.literal(""),
                    LanguageManager.getMessageAsText("adminControlsHeader"),
                    LanguageManager.getMessageAsText("adminLeftClickEdit"),
                    LanguageManager.getMessageAsText("adminRightClickRename"),
                    Text.literal(""),
                    LanguageManager.getMessageAsText("adminModeWarning")
            );

            backpackItem.set(DataComponentTypes.LORE, new LoreComponent(lore));

            var customData = backpackItem.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
            customData = customData.apply(nbt -> nbt.putInt("backpack_id", entry.getKey()));
            backpackItem.set(DataComponentTypes.CUSTOM_DATA, customData);

            this.getSlot(slot).setStack(backpackItem);
            slot++;
        }

        // Item de información de administrador - ACTUALIZADO
        if (slot < 54) {
            ItemStack adminInfoItem = new ItemStack(Items.REDSTONE_BLOCK);
            adminInfoItem.set(DataComponentTypes.CUSTOM_NAME, LanguageManager.getMessageAsText("adminPanelTitle"));

            List<Text> adminInfoLore = List.of(
                    LanguageManager.getMessageAsText("adminPlayerInfo", playerName),
                    LanguageManager.getMessageAsText("statsTotalBackpacks", backpacks.size()),
                    LanguageManager.getMessageAsText("statsItemsStored", getTotalItems(backpacks)),
                    LanguageManager.getMessageAsText("statsEmptySlots", getTotalEmptySlots(backpacks)),
                    LanguageManager.getMessageAsText("statsUsagePercent", getTotalUsagePercentage(backpacks)),
                    Text.literal(""),
                    LanguageManager.getMessageAsText("adminActionsHeader"),
                    LanguageManager.getMessageAsText("adminCanEdit"),
                    LanguageManager.getMessageAsText("adminCanAddRemove"),
                    LanguageManager.getMessageAsText("adminCanRename"),
                    Text.literal(""),
                    LanguageManager.getMessageAsText("adminWarningHeader"),
                    LanguageManager.getMessageAsText("adminChangesAutomatic"),
                    LanguageManager.getMessageAsText("adminCannotUndo"),
                    Text.literal(""),
                    LanguageManager.getMessageAsText("adminPlayerUUID", targetPlayer.getUuid().toString())
            );

            adminInfoItem.set(DataComponentTypes.LORE, new LoreComponent(adminInfoLore));

            var adminCustomData = adminInfoItem.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
            adminCustomData = adminCustomData.apply(nbt -> nbt.putBoolean("admin_info", true));
            adminInfoItem.set(DataComponentTypes.CUSTOM_DATA, adminCustomData);

            this.getSlot(53).setStack(adminInfoItem);
        }
    }

    private int getTotalItems(Map<Integer, MongoBackpackManager.BackpackData> backpacks) {
        int total = 0;
        for (MongoBackpackManager.BackpackData backpack : backpacks.values()) {
            for (ItemStack stack : backpack.getInventory()) {
                if (!stack.isEmpty()) {
                    total++;
                }
            }
        }
        return total;
    }

    private int getTotalEmptySlots(Map<Integer, MongoBackpackManager.BackpackData> backpacks) {
        int total = 0;
        for (MongoBackpackManager.BackpackData backpack : backpacks.values()) {
            for (ItemStack stack : backpack.getInventory()) {
                if (stack.isEmpty()) {
                    total++;
                }
            }
        }
        return total;
    }

    private double getTotalUsagePercentage(Map<Integer, MongoBackpackManager.BackpackData> backpacks) {
        int totalItems = getTotalItems(backpacks);
        int totalSlots = 0;
        for (MongoBackpackManager.BackpackData backpack : backpacks.values()) {
            totalSlots += backpack.getInventory().size();
        }
        return totalSlots > 0 ? (double) totalItems / totalSlots * 100 : 0;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < 54) { // Solo slots del menú de admin
            ItemStack clickedStack = this.getSlot(slotIndex).getStack();

            var customData = clickedStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);

            // Verificar si es item de información admin
            if (!clickedStack.isEmpty() && customData.copyNbt().contains("admin_info")) {
                sendDetailedPlayerInfo();
                return;
            }

            // Verificar si es una mochila - CORREGIDO: solo permitir clicks sin items en cursor
            if (!clickedStack.isEmpty() && customData.copyNbt().contains("backpack_id")) {
                int backpackId = customData.copyNbt().getInt("backpack_id");

                if (actionType == SlotActionType.PICKUP) {
                    if (button == 0) { // Left click
                        // VERIFICAR: No permitir click con item en cursor para evitar intercambio accidental
                        if (!this.getCursorStack().isEmpty()) {
                            // ACTUALIZADO: Usar sistema de idiomas
                            LanguageManager.sendMessage((ServerPlayerEntity) player, "emptyCursorRequired");
                            return;
                        }

                        // Verificar permisos antes de permitir edición
                        if (!LuckPermsManager.canEditOthers((ServerPlayerEntity) player)) {
                            LuckPermsManager.sendNoPermissionMessage((ServerPlayerEntity) player, LuckPermsManager.ADMIN_EDIT_PERMISSION);
                            return;
                        }

                        // Cerrar pantalla actual y abrir editor
                        ((ServerPlayerEntity) player).closeHandledScreen();

                        // Delay para evitar conflictos
                        CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(100);
                                if (BackpacksMod.getServer() != null) {
                                    BackpacksMod.getServer().execute(() -> {
                                        BackpackAdminEditScreenHandler.openAdminEdit((ServerPlayerEntity) player, targetPlayer, backpackId);
                                    });
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                        return;

                    } else if (button == 1) { // Right click
                        // Solo permitir rename si no hay item en cursor
                        if (!this.getCursorStack().isEmpty()) {
                            // ACTUALIZADO: Usar sistema de idiomas
                            LanguageManager.sendMessage((ServerPlayerEntity) player, "emptyCursorForRename");
                            return;
                        }

                        // Verificar permisos antes de permitir renombrado
                        if (!LuckPermsManager.canAdminRename((ServerPlayerEntity) player)) {
                            LuckPermsManager.sendNoPermissionMessage((ServerPlayerEntity) player, LuckPermsManager.ADMIN_RENAME_PERMISSION);
                            return;
                        }

                        ((ServerPlayerEntity) player).closeHandledScreen();

                        CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(100);
                                if (BackpacksMod.getServer() != null) {
                                    BackpacksMod.getServer().execute(() -> {
                                        openAdminRenameInterface((ServerPlayerEntity) player, backpackId);
                                    });
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                        return;
                    }
                }
            }
        }

        // Para clicks en inventario del jugador
        if (slotIndex >= 54) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }

        // BLOQUEAR: No permitir interacciones con slots de menú admin que no sean backpacks o info
        // Esto previene bugs con arrastrar items al menú
    }

    private void sendDetailedPlayerInfo() {
        CompletableFuture.runAsync(() -> {
            try {
                BackpackManager.BackpackStats stats = BackpackManager.getPlayerStats(targetPlayer.getUuid());
                String playerName = targetPlayer.getName().getString();

                if (BackpacksMod.getServer() != null) {
                    BackpacksMod.getServer().execute(() -> {
                        admin.sendMessage(Text.literal(""), false);
                        admin.sendMessage(LanguageManager.getMessageAsText("statsHeader", playerName), false);
                        admin.sendMessage(LanguageManager.getMessageAsText("playerNameDisplay", playerName), false);
                        admin.sendMessage(LanguageManager.getMessageAsText("uuidDisplay", targetPlayer.getUuid().toString()), false);
                        admin.sendMessage(LanguageManager.getMessageAsText("statsTotalBackpacks", stats.getTotalBackpacks()), false);
                        admin.sendMessage(LanguageManager.getMessageAsText("statsItemsStored", stats.getTotalItems()), false);
                        admin.sendMessage(LanguageManager.getMessageAsText("statsEmptySlots", stats.getEmptySlots()), false);
                        admin.sendMessage(LanguageManager.getMessageAsText("statsTotalSlots", stats.getTotalSlots()), false);
                        admin.sendMessage(LanguageManager.getMessageAsText("statsUsagePercent", stats.getUsagePercentage()), false);
                        admin.sendMessage(Text.literal(""), false);
                    });
                }
            } catch (Exception e) {
                LanguageManager.sendMessage(admin, "errorLoadingData");
            }
        });
    }

    private void openAdminRenameInterface(ServerPlayerEntity admin, int backpackId) {
        MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayer.getUuid(), backpackId);
        String playerName = targetPlayer.getName().getString();

        if (backpack != null) {
            admin.sendMessage(Text.literal(""), false);
            admin.sendMessage(MessageUtils.parseText("<#ff5555><bold>=== ADMIN RENAME BACKPACK ===</bold></>"), false);
            // ACTUALIZADO: Usar sistema de idiomas
            admin.sendMessage(LanguageManager.getMessageAsText("playerNameDisplay", playerName), false);
            admin.sendMessage(LanguageManager.getMessageAsText("backpackIdDisplay", backpackId), false);
            admin.sendMessage(LanguageManager.getMessageAsText("currentNameDisplay", backpack.getName()), false);
            admin.sendMessage(Text.literal(""), false);
            admin.sendMessage(LanguageManager.getMessageAsText("useCommand"), false);
            admin.sendMessage(LanguageManager.getMessageAsText("renameCommandExample",
                    ConfigManager.getConfig().mainCommand, playerName, backpackId), false);
            admin.sendMessage(Text.literal(""), false);
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // BLOQUEAR shift+click completamente en vista admin para prevenir bugs
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        return LuckPermsManager.canViewOthers(serverPlayer);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        // Limpiar cursor al cerrar para evitar items perdidos
        if (!this.getCursorStack().isEmpty()) {
            player.getInventory().offerOrDrop(this.getCursorStack());
            this.setCursorStack(ItemStack.EMPTY);
        }

        super.onClosed(player);
        BackpackManager.markBackpackDirty(targetPlayer.getUuid());
    }

    public static void openAdminView(ServerPlayerEntity admin, ServerPlayerEntity targetPlayer) {
        try {
            admin.openHandledScreen(new BackpackAdminViewFactory(admin, targetPlayer));
        } catch (Exception e) {
            // ACTUALIZADO: Usar sistema de idiomas
            LanguageManager.sendMessage(admin, "errorOpeningBackpack");
        }
    }

    public static class BackpackAdminViewFactory implements net.minecraft.screen.NamedScreenHandlerFactory {
        private final ServerPlayerEntity admin;
        private final ServerPlayerEntity targetPlayer;

        public BackpackAdminViewFactory(ServerPlayerEntity admin, ServerPlayerEntity targetPlayer) {
            this.admin = admin;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public Text getDisplayName() {
            // ACTUALIZADO: Usar sistema de idiomas para título
            return LanguageManager.getMessageAsText("menuTitleAdminView", targetPlayer.getName().getString());
        }

        @Override
        public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
            return new BackpackAdminViewScreenHandler(syncId, inv, admin, targetPlayer);
        }
    }
}