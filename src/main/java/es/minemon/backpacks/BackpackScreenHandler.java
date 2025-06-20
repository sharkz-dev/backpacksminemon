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
import java.util.concurrent.CompletableFuture;

public class BackpackScreenHandler extends GenericContainerScreenHandler {
    private final ServerPlayerEntity player;
    private final int backpackId;
    private final MongoBackpackManager.BackpackData backpackData;
    private final SimpleInventory backpackInventory;

    private volatile boolean hasChanges = false;
    private long lastSave = 0;
    private static final long SAVE_DELAY = 300;

    private volatile boolean isInternalUpdate = false;
    private String observerId;

    public BackpackScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity player, int backpackId) {
        this(syncId, playerInventory, createInventoryFromBackpack(player, backpackId), player, backpackId);
    }

    public BackpackScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, ServerPlayerEntity player, int backpackId) {
        super(getScreenHandlerType(player, backpackId), syncId, playerInventory, inventory, getRows(player, backpackId));
        this.player = player;
        this.backpackId = backpackId;
        this.backpackData = BackpackManager.getBackpack(player.getUuid(), backpackId);
        this.backpackInventory = (SimpleInventory) inventory;

        BackpackSyncManager.registerPlayerObserver(player, backpackId);
        this.observerId = "player_" + player.getUuid() + "_" + backpackId;

        addBackButton();
    }

    public int getBackpackId() {
        return backpackId;
    }

    public void syncInventory(DefaultedList<ItemStack> newInventory) {
        if (isInternalUpdate) {
            return;
        }

        isInternalUpdate = true;
        try {
            int maxSlot = hasBackButton() ? backpackInventory.size() - 1 : backpackInventory.size();

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
                if (hasBackButton()) {
                    addBackButton();
                }
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

    private void addBackButton() {
        if (backpackData == null || backpackInventory == null) return;

        int lastSlot = backpackInventory.size() - 1;

        if (backpackInventory.getStack(lastSlot).isEmpty()) {
            ItemStack backButton = new ItemStack(Items.BARRIER);
            // ACTUALIZADO: Usar sistema de idiomas
            backButton.set(DataComponentTypes.CUSTOM_NAME, LanguageManager.getMessageAsText("backButtonToMenu"));

            List<Text> lore = List.of(
                    LanguageManager.getMessageAsText("backButtonDescription"),
                    LanguageManager.getMessageAsText("backButtonMainMenu"),
                    Text.literal(""),
                    LanguageManager.getMessageAsText("backButtonNotStored"),
                    LanguageManager.getMessageAsText("backButtonInBackpack")
            );

            backButton.set(DataComponentTypes.LORE, new LoreComponent(lore));

            var customData = backButton.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
            customData = customData.apply(nbt -> nbt.putBoolean("back_button", true));
            backButton.set(DataComponentTypes.CUSTOM_DATA, customData);

            backpackInventory.setStack(lastSlot, backButton);
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < backpackInventory.size()) {
            ItemStack clickedStack = backpackInventory.getStack(slotIndex);
            var customData = clickedStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);

            if (!clickedStack.isEmpty() && customData.copyNbt().contains("back_button")) {
                if (actionType == SlotActionType.PICKUP && button == 0) {
                    ((ServerPlayerEntity) player).closeHandledScreen();
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(100);
                            if (BackpacksMod.getServer() != null) {
                                BackpacksMod.getServer().execute(() -> {
                                    BackpackMenuScreenHandler.openBackpackMenu((ServerPlayerEntity) player);
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
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        ItemStack itemStack = super.quickMove(player, slot);
        if (itemStack != ItemStack.EMPTY) {
            markChangesAndNotify();
        }

        return itemStack;
    }

    private boolean hasBackButton() {
        if (backpackInventory.size() == 0) return false;
        ItemStack lastStack = backpackInventory.getStack(backpackInventory.size() - 1);
        if (lastStack.isEmpty()) return false;

        var customData = lastStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT);
        return customData.copyNbt().contains("back_button");
    }

    private void markChangesAndNotify() {
        if (isInternalUpdate) return;

        saveCurrentInventoryToBackpack();

        hasChanges = true;
        BackpackManager.markBackpackDirty(player.getUuid());

        BackpackSyncManager.notifyInventoryChange(player.getUuid(), backpackId, player.getUuid());

        long now = System.currentTimeMillis();
        if (now - lastSave > SAVE_DELAY) {
            lastSave = now;
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(100);
                    BackpackManager.forcePlayerSave(player.getUuid());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    private void saveCurrentInventoryToBackpack() {
        if (backpackData != null && backpackInventory != null) {
            try {
                int maxSlot = backpackInventory.size();
                if (hasBackButton()) {
                    maxSlot = backpackInventory.size() - 1;
                }

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
                    BackpackManager.markBackpackDirty(player.getUuid());
                }

            } catch (Exception e) {
                // Error handling sin logging
            }
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }

        // Verificar permisos y que el backpack exista
        return LuckPermsManager.canUseBackpacks(serverPlayer) &&
                BackpackManager.getBackpack(player.getUuid(), backpackId) != null;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        if (observerId != null) {
            BackpackSyncManager.unregisterObserver(observerId);
        }

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

        if (hasChanges) {
            saveCurrentInventoryToBackpack();

            if (ConfigManager.getConfig().autoSaveOnClose) {
                BackpackManager.forcePlayerSave(player.getUuid())
                        .whenComplete((result, throwable) -> {
                            // Error handling sin logging
                        });
            }
        }

        super.onClosed(player);
    }

    public static void openBackpack(ServerPlayerEntity player, int backpackId) {
        try {
            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(player.getUuid(), backpackId);
            if (backpack == null) {
                // ACTUALIZADO: Usar sistema de idiomas
                LanguageManager.sendMessage(player, "errorBackpackNotFound");
                return;
            }

            player.openHandledScreen(new BackpackScreenHandlerFactory(player, backpackId));

        } catch (Exception e) {
            // ACTUALIZADO: Usar sistema de idiomas
            LanguageManager.sendMessage(player, "errorOpeningBackpack");
        }
    }

    public static class BackpackScreenHandlerFactory implements net.minecraft.screen.NamedScreenHandlerFactory {
        private final ServerPlayerEntity player;
        private final int backpackId;

        public BackpackScreenHandlerFactory(ServerPlayerEntity player, int backpackId) {
            this.player = player;
            this.backpackId = backpackId;
        }

        @Override
        public Text getDisplayName() {
            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(player.getUuid(), backpackId);
            if (backpack != null) {
                // ACTUALIZADO: Usar sistema de idiomas para título
                return LanguageManager.getMessageAsText("menuTitleBackpack",
                        backpack.getName(), getItemCount(backpack), backpack.getSlots());
            }
            return MessageUtils.parseText("<#ffff55>Backpack #" + backpackId + "</>");
        }

        private int getItemCount(MongoBackpackManager.BackpackData backpack) {
            int count = 0;
            for (ItemStack stack : backpack.getInventory()) {
                if (!stack.isEmpty()) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
            return new BackpackScreenHandler(syncId, inv, this.player, backpackId);
        }
    }
}