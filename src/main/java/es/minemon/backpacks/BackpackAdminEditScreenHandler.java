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
import net.minecraft.util.collection.DefaultedList;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BackpackAdminEditScreenHandler extends GenericContainerScreenHandler {
    private final ServerPlayerEntity admin;
    private final ServerPlayerEntity targetPlayer;
    private final int backpackId;
    private final MongoBackpackManager.BackpackData backpackData;
    private final SimpleInventory backpackInventory;

    // Sistema de guardado optimizado para admins
    private volatile boolean hasChanges = false;
    private long lastSave = 0;
    private static final long SAVE_DELAY = 200;

    private volatile boolean isInternalUpdate = false;
    private String observerId;

    public BackpackAdminEditScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity admin, ServerPlayerEntity targetPlayer, int backpackId) {
        this(syncId, playerInventory, createInventoryFromBackpack(targetPlayer, backpackId), admin, targetPlayer, backpackId);
    }

    private BackpackAdminEditScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, ServerPlayerEntity admin, ServerPlayerEntity targetPlayer, int backpackId) {
        super(getScreenHandlerType(targetPlayer, backpackId), syncId, playerInventory, inventory, getRows(targetPlayer, backpackId));
        this.admin = admin;
        this.targetPlayer = targetPlayer;
        this.backpackId = backpackId;
        this.backpackData = BackpackManager.getBackpack(targetPlayer.getUuid(), backpackId);
        this.backpackInventory = (SimpleInventory) inventory;

        BackpackSyncManager.registerAdminObserver(admin, targetPlayer.getUuid(), backpackId);
        this.observerId = "admin_" + admin.getUuid() + "_" + targetPlayer.getUuid() + "_" + backpackId;

        addAdminBackButton();
    }

    public int getBackpackId() {
        return backpackId;
    }

    public UUID getTargetPlayerId() {
        return targetPlayer.getUuid();
    }

    public void syncInventory(DefaultedList<ItemStack> newInventory) {
        if (isInternalUpdate) {
            return;
        }

        isInternalUpdate = true;
        try {
            int maxSlot = backpackInventory.size() - 1;

            boolean hasActualChanges = false;
            for (int i = 0; i < Math.min(maxSlot, newInventory.size()); i++) {
                ItemStack currentStack = backpackInventory.getStack(i);
                ItemStack newStack = newInventory.get(i);

                if (!ItemStack.areEqual(currentStack, newStack)) {
                    backpackInventory.setStack(i, newStack.copy());
                    hasActualChanges = true;
                }
            }

            if (hasActualChanges) {
                addAdminBackButton();
                this.sendContentUpdates();
            }

        } finally {
            isInternalUpdate = false;
        }
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> getScreenHandlerType(ServerPlayerEntity player, int backpackId) {
        MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(player.getUuid(), backpackId);
        if (backpack == null) {
            return ScreenHandlerType.GENERIC_9X3;
        }

        int slots = backpack.getSlots();
        return switch (slots) {
            case 9 -> ScreenHandlerType.GENERIC_9X1;
            case 18 -> ScreenHandlerType.GENERIC_9X2;
            case 27 -> ScreenHandlerType.GENERIC_9X3;
            case 36 -> ScreenHandlerType.GENERIC_9X4;
            case 45 -> ScreenHandlerType.GENERIC_9X5;
            case 54 -> ScreenHandlerType.GENERIC_9X6;
            default -> ScreenHandlerType.GENERIC_9X3;
        };
    }

    private static int getRows(ServerPlayerEntity player, int backpackId) {
        MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(player.getUuid(), backpackId);
        return backpack != null ? backpack.getSlots() / 9 : 3;
    }

    private static SimpleInventory createInventoryFromBackpack(ServerPlayerEntity player, int backpackId) {
        MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(player.getUuid(), backpackId);

        if (backpack == null) {
            throw new RuntimeException("Backpack not found");
        }

        SimpleInventory inventory = new SimpleInventory(backpack.getSlots());

        for (int i = 0; i < backpack.getInventory().size(); i++) {
            ItemStack originalStack = backpack.getInventory().get(i);
            if (!originalStack.isEmpty()) {
                inventory.setStack(i, originalStack.copy());
            }
        }

        return inventory;
    }

    private void addAdminBackButton() {
        if (backpackData == null || backpackInventory == null) return;

        int lastSlot = backpackInventory.size() - 1;

        ItemStack backButton = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        // ACTUALIZADO: Usar sistema de idiomas
        backButton.set(DataComponentTypes.CUSTOM_NAME, LanguageManager.getMessageAsText("backButtonToAdminView"));

        String playerName = targetPlayer.getName().getString();

        List<Text> lore = List.of(
                MessageUtils.parseText("<#f5d5a0>=== ADMIN VIEW ===</>"),
                LanguageManager.getMessageAsText("backButtonDescription"),
                LanguageManager.getMessageAsText("backButtonAdminOverview"),
                Text.literal(""),
                LanguageManager.getMessageAsText("adminEditingPlayer", playerName),
                LanguageManager.getMessageAsText("adminEditingBackpackInfo", backpackData.getName(), backpackId),
                Text.literal(""),
                LanguageManager.getMessageAsText("backButtonAdminControl"),
                LanguageManager.getMessageAsText("backButtonCannotRemove"),
                LanguageManager.getMessageAsText("backButtonSlotNotSaved")
        );

        backButton.set(DataComponentTypes.LORE, new LoreComponent(lore));

        var customData = backButton.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
        customData = customData.apply(nbt -> {
            nbt.putBoolean("admin_back_button", true);
            nbt.putInt("original_slot", lastSlot);
        });
        backButton.set(DataComponentTypes.CUSTOM_DATA, customData);

        backpackInventory.setStack(lastSlot, backButton);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < backpackInventory.size()) {
            ItemStack clickedStack = backpackInventory.getStack(slotIndex);
            var customData = clickedStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);

            if (!clickedStack.isEmpty() && customData.copyNbt().contains("admin_back_button")) {
                if (actionType == SlotActionType.PICKUP && button == 0) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(100);
                            if (BackpacksMod.getServer() != null) {
                                BackpacksMod.getServer().execute(() -> {
                                    BackpackAdminViewScreenHandler.openAdminView((ServerPlayerEntity) player, targetPlayer);
                                });
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    return;
                }
                return;
            }
        }

        super.onSlotClick(slotIndex, button, actionType, player);
        markChangesAndNotify();
        restoreAdminButtonIfNeeded(slotIndex);
    }

    private void restoreAdminButtonIfNeeded(int modifiedSlot) {
        int lastSlot = backpackInventory.size() - 1;

        if (modifiedSlot == lastSlot) {
            ItemStack currentStack = backpackInventory.getStack(lastSlot);
            var customData = currentStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);

            if (currentStack.isEmpty() || !customData.copyNbt().contains("admin_back_button")) {
                addAdminBackButton();
                // ACTUALIZADO: Usar sistema de idiomas
                LanguageManager.sendMessage(admin, "adminControlRestored");
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        ItemStack result = ItemStack.EMPTY;

        if (slot >= backpackInventory.size()) {
            ItemStack stackToMove = this.slots.get(slot).getStack();
            if (stackToMove.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int maxSlot = backpackInventory.size() - 1;

            for (int i = 0; i < maxSlot; i++) {
                ItemStack backpackStack = backpackInventory.getStack(i);
                if (backpackStack.isEmpty()) {
                    backpackInventory.setStack(i, stackToMove.copy());
                    stackToMove.setCount(0);
                    markChangesAndNotify();
                    return ItemStack.EMPTY;
                } else if (ItemStack.areItemsAndComponentsEqual(backpackStack, stackToMove)) {
                    int maxCount = Math.min(backpackStack.getMaxCount(), stackToMove.getMaxCount());
                    int spaceAvailable = maxCount - backpackStack.getCount();
                    if (spaceAvailable > 0) {
                        int toMove = Math.min(spaceAvailable, stackToMove.getCount());
                        backpackStack.increment(toMove);
                        stackToMove.decrement(toMove);
                        markChangesAndNotify();
                        if (stackToMove.isEmpty()) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            }
            result = stackToMove;
        } else {
            int lastSlot = backpackInventory.size() - 1;
            if (slot == lastSlot) {
                ItemStack clickedStack = backpackInventory.getStack(slot);
                var customData = clickedStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);

                if (!clickedStack.isEmpty() && customData.copyNbt().contains("admin_back_button")) {
                    return ItemStack.EMPTY;
                }
            }

            result = super.quickMove(player, slot);
            markChangesAndNotify();
        }

        return result;
    }

    private void markChangesAndNotify() {
        if (isInternalUpdate) return;

        saveCurrentInventoryToBackpack();

        hasChanges = true;
        BackpackManager.markBackpackDirty(targetPlayer.getUuid());

        BackpackSyncManager.notifyInventoryChange(targetPlayer.getUuid(), backpackId, admin.getUuid());

        long now = System.currentTimeMillis();
        if (now - lastSave > SAVE_DELAY) {
            lastSave = now;
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50);
                    BackpackManager.forcePlayerSave(targetPlayer.getUuid());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    private void saveCurrentInventoryToBackpack() {
        if (backpackData != null && backpackInventory != null) {
            try {
                int maxSlot = backpackInventory.size() - 1;

                boolean hasActualChanges = false;
                for (int i = 0; i < Math.min(maxSlot, backpackData.getInventory().size()); i++) {
                    ItemStack currentStack = backpackInventory.getStack(i);
                    ItemStack backpackStack = backpackData.getInventory().get(i);

                    if (!ItemStack.areEqual(currentStack, backpackStack)) {
                        backpackData.getInventory().set(i, currentStack.copy());
                        hasActualChanges = true;
                    }
                }

                if (hasActualChanges) {
                    BackpackManager.markBackpackDirty(targetPlayer.getUuid());
                }

            } catch (Exception e) {
                // ACTUALIZADO: Usar sistema de idiomas
                LanguageManager.sendMessage(admin, "errorSavingData");
            }
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        return LuckPermsManager.canEditOthers(serverPlayer) &&
                BackpackManager.getBackpack(targetPlayer.getUuid(), backpackId) != null;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        if (observerId != null) {
            BackpackSyncManager.unregisterObserver(observerId);
        }

        if (hasChanges) {
            saveCurrentInventoryToBackpack();

            BackpackManager.forcePlayerSave(targetPlayer.getUuid())
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            // ACTUALIZADO: Usar sistema de idiomas
                            LanguageManager.sendMessage(admin, "errorSavingData");
                        } else {
                            LanguageManager.sendMessage(admin, "dataSaved");
                        }
                    });

            if (BackpacksMod.getBackupManager() != null) {
                String playerName = targetPlayer.getName().getString();
                BackpacksMod.getBackupManager().createManualBackup(
                        "Admin " + admin.getName().getString() + " edited backpack " + backpackId +
                                " of player " + playerName);
            }
        }

        super.onClosed(player);
    }

    public static void openAdminEdit(ServerPlayerEntity admin, ServerPlayerEntity targetPlayer, int backpackId) {
        try {
            // Verificar permisos antes de abrir
            if (!LuckPermsManager.canEditOthers(admin)) {
                LuckPermsManager.sendNoPermissionMessage(admin, LuckPermsManager.ADMIN_EDIT_PERMISSION);
                return;
            }

            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayer.getUuid(), backpackId);
            if (backpack == null) {
                // ACTUALIZADO: Usar sistema de idiomas
                LanguageManager.sendMessage(admin, "errorBackpackNotFound");
                return;
            }

            admin.sendMessage(Text.literal(""), false);
            // ACTUALIZADO: Usar sistema de idiomas
            admin.sendMessage(LanguageManager.getMessageAsText("adminEditModeWarning"), false);
            admin.sendMessage(LanguageManager.getMessageAsText("adminAboutToEdit"), false);
            admin.sendMessage(LanguageManager.getMessageAsText("adminEditingPlayer", targetPlayer.getName().getString()), false);
            admin.sendMessage(LanguageManager.getMessageAsText("adminEditingBackpackInfo", backpack.getName(), backpackId), false);
            admin.sendMessage(LanguageManager.getMessageAsText("adminChangesAutomatic"), false);
            admin.sendMessage(LanguageManager.getMessageAsText("adminSlotReserved"), false);
            admin.sendMessage(Text.literal(""), false);

            admin.openHandledScreen(new BackpackAdminEditFactory(admin, targetPlayer, backpackId));

        } catch (Exception e) {
            // ACTUALIZADO: Usar sistema de idiomas
            LanguageManager.sendMessage(admin, "errorOpeningBackpack");
        }
    }

    public static class BackpackAdminEditFactory implements net.minecraft.screen.NamedScreenHandlerFactory {
        private final ServerPlayerEntity admin;
        private final ServerPlayerEntity targetPlayer;
        private final int backpackId;

        public BackpackAdminEditFactory(ServerPlayerEntity admin, ServerPlayerEntity targetPlayer, int backpackId) {
            this.admin = admin;
            this.targetPlayer = targetPlayer;
            this.backpackId = backpackId;
        }

        @Override
        public Text getDisplayName() {
            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(targetPlayer.getUuid(), backpackId);
            if (backpack != null) {
                // ACTUALIZADO: Usar sistema de idiomas para título
                return LanguageManager.getMessageAsText("menuTitleAdminEdit",
                        backpack.getName() + " (" + targetPlayer.getName().getString() + ")");
            }
            return MessageUtils.parseText("<#e6a3e6>[ADMIN EDIT] <#f5f5a0>Backpack #" + backpackId + "</>");
        }

        @Override
        public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
            return new BackpackAdminEditScreenHandler(syncId, inv, admin, targetPlayer, backpackId);
        }
    }
}