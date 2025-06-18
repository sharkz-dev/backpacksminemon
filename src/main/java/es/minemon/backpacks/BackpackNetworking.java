package es.minemon.backpacks;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class BackpackNetworking {
    public static final Identifier OPEN_BACKPACK_PACKET_ID = Identifier.of(BackpacksMod.MOD_ID, "open_backpack");
    public static final Identifier CHANGE_ICON_PACKET_ID = Identifier.of(BackpacksMod.MOD_ID, "change_icon");
    public static final Identifier RENAME_BACKPACK_PACKET_ID = Identifier.of(BackpacksMod.MOD_ID, "rename_backpack");

    public record OpenBackpackPayload(int backpackId) implements CustomPayload {
        public static final CustomPayload.Id<OpenBackpackPayload> ID = new CustomPayload.Id<>(OPEN_BACKPACK_PACKET_ID);
        public static final PacketCodec<RegistryByteBuf, OpenBackpackPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeInt(value.backpackId),
                buf -> new OpenBackpackPayload(buf.readInt())
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ChangeIconPayload(int backpackId, ItemStack newIcon) implements CustomPayload {
        public static final CustomPayload.Id<ChangeIconPayload> ID = new CustomPayload.Id<>(CHANGE_ICON_PACKET_ID);
        public static final PacketCodec<RegistryByteBuf, ChangeIconPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeInt(value.backpackId);
                    ItemStack.PACKET_CODEC.encode(buf, value.newIcon);
                },
                buf -> new ChangeIconPayload(
                        buf.readInt(),
                        ItemStack.PACKET_CODEC.decode(buf)
                )
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RenameBackpackPayload(int backpackId, String newName) implements CustomPayload {
        public static final CustomPayload.Id<RenameBackpackPayload> ID = new CustomPayload.Id<>(RENAME_BACKPACK_PACKET_ID);
        public static final PacketCodec<RegistryByteBuf, RenameBackpackPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeInt(value.backpackId);
                    buf.writeString(value.newName);
                },
                buf -> new RenameBackpackPayload(
                        buf.readInt(),
                        buf.readString()
                )
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void handleOpenBackpack(OpenBackpackPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            // Verificar permisos básicos antes de abrir
            if (!LuckPermsManager.canUseBackpacks(context.player())) {
                LuckPermsManager.sendNoPermissionMessage(context.player(), LuckPermsManager.USE_PERMISSION);
                return;
            }

            BackpackScreenHandler.openBackpack(context.player(), payload.backpackId());
        });
    }

    public static void handleChangeIcon(ChangeIconPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            // Verificar permisos primero
            if (!LuckPermsManager.canChangeIcon(context.player())) {
                LuckPermsManager.sendNoPermissionMessage(context.player(), LuckPermsManager.CHANGE_ICON_PERMISSION);
                return;
            }

            if (!ConfigManager.getConfig().allowCustomIcons) {
                LanguageManager.sendMessage(context.player(), "errorChangingIcon");
                return;
            }

            // Validar que el jugador tenga la mochila
            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(context.player().getUuid(), payload.backpackId());
            if (backpack == null) {
                LanguageManager.sendMessage(context.player(), "errorBackpackNotFound");
                return;
            }

            // Validar que el nuevo icono no esté vacío
            if (payload.newIcon().isEmpty()) {
                LanguageManager.sendMessage(context.player(), "cannotUseEmptyIcon");
                return;
            }

            // MODIFICADO - Crear icono con cantidad 1
            ItemStack iconStack = payload.newIcon().copy();
            iconStack.setCount(1); // Asegurar que el icono tenga cantidad 1

            // Cambiar el icono
            boolean success = BackpackManager.changeBackpackIcon(context.player().getUuid(), payload.backpackId(), iconStack);
            if (success) {
                LanguageManager.sendMessage(context.player(), "backpackIconChanged");

                // Forzar guardado
                BackpackManager.forcePlayerSave(context.player().getUuid());
            } else {
                LanguageManager.sendMessage(context.player(), "errorChangingIcon");
            }
        });
    }

    public static void handleRenameBackpack(RenameBackpackPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            // Verificar permisos primero
            if (!LuckPermsManager.canRename(context.player())) {
                LuckPermsManager.sendNoPermissionMessage(context.player(), LuckPermsManager.RENAME_PERMISSION);
                return;
            }

            if (!ConfigManager.getConfig().allowBackpackRename) {
                LanguageManager.sendMessage(context.player(), "errorRenamingBackpack");
                return;
            }

            // Validar que el jugador tenga la mochila
            MongoBackpackManager.BackpackData backpack = BackpackManager.getBackpack(context.player().getUuid(), payload.backpackId());
            if (backpack == null) {
                LanguageManager.sendMessage(context.player(), "errorBackpackNotFound");
                return;
            }

            // Validar el nombre
            String newName = payload.newName().trim();
            if (newName.isEmpty()) {
                LanguageManager.sendMessage(context.player(), "nameCannotBeEmpty");
                return;
            }

            if (newName.length() > 50) {
                LanguageManager.sendMessage(context.player(), "nameTooLong");
                return;
            }

            // Renombrar la mochila
            boolean success = BackpackManager.renameBackpack(context.player().getUuid(), payload.backpackId(), newName);
            if (success) {
                LanguageManager.sendMessage(context.player(), "backpackSuccessMessage", payload.backpackId(), newName);

                // Forzar guardado
                BackpackManager.forcePlayerSave(context.player().getUuid());
            } else {
                LanguageManager.sendMessage(context.player(), "errorRenamingBackpack");
            }
        });
    }
}