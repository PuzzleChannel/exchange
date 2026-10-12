package com.puzzlechannel.vmexchange.menu;

import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.network.ClientState;
import com.puzzlechannel.vmexchange.registry.ModBlocks;
import com.puzzlechannel.vmexchange.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class VmExchangeMenu extends AbstractContainerMenu implements VmExchangeManager.VmExchangeMenuAccess {
    public static final int EXCHANGE_SLOTS = 4;
    private final SimpleContainer container = new SimpleContainer(EXCHANGE_SLOTS);
    private final Inventory playerInventory;
    private final ResourceKey<Level> exchangeDimension;
    private final BlockPos exchangePos;

    // Used by the client-side menu factory. The server creates the menu with the block's exact position.
    public VmExchangeMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, null, null);
    }

    public VmExchangeMenu(int syncId, Inventory playerInventory,
                          ResourceKey<Level> exchangeDimension, BlockPos exchangePos) {
        super(ModMenus.EXCHANGE, syncId);
        this.playerInventory = playerInventory;
        this.exchangeDimension = exchangeDimension;
        this.exchangePos = exchangePos == null ? null : exchangePos.immutable();

        // Four resource inputs use real Minecraft-style slot coordinates matching the screen.
        for (int i = 0; i < EXCHANGE_SLOTS; i++) {
            final int index = i;
            int[] xs = {31, 63, 95, 127};
            addSlot(new Slot(container, i, xs[i], 36) {
                @Override public boolean mayPlace(ItemStack stack) { return isAcceptedAt(stack, index); }
                @Override public int getMaxStackSize() { return 64; }
            });
        }

        // Exact vanilla player-inventory slot coordinates relative to the background
        // section rendered from inventory.png at screen Y=146. The texture's player
        // inventory section starts at V=83, so its slot rows are +1/+19/+37 and hotbar +59.
        // Must match VmExchangeScreen.INVENTORY_TOP so the vanilla inventory starts
        // directly below the exchange panel without clipping the first row.
        int inventoryTop = 143;
        int inventoryLeft = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        inventoryLeft + 8 + col * 18, inventoryTop + 1 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, inventoryLeft + 8 + col * 18, inventoryTop + 59));
        }
    }

    private boolean isAccepted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        MinecraftServer server = playerInventory.player.level().getServer();
        if (server == null) {
            var s = ClientState.get();
            return id.equals(s.m1()) || id.equals(s.m2()) || id.equals(s.m3()) || id.equals(s.m4());
        }
        return VmExchangeManager.get(server).materials().contains(id);
    }

    private boolean isAcceptedAt(ItemStack stack, int slotIndex) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        MinecraftServer server = playerInventory.player.level().getServer();
        if (server != null) {
            var materials = VmExchangeManager.get(server).materials();
            return slotIndex < materials.size() && materials.get(slotIndex).equals(id);
        }
        var state = ClientState.get();
        String expected = switch (slotIndex) { case 0 -> state.m1(); case 1 -> state.m2(); case 2 -> state.m3(); case 3 -> state.m4(); default -> ""; };
        return id.equals(expected);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (index < EXCHANGE_SLOTS) {
            if (!moveItemStackTo(slot.getItem(), EXCHANGE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!isAccepted(slot.getItem()) || !moveItemStackTo(slot.getItem(), 0, EXCHANGE_SLOTS, false)) return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override public boolean stillValid(Player player) {
        if (!player.isAlive() || player.isRemoved()) return false;
        if (exchangePos == null || exchangeDimension == null) return true;
        return player.level().dimension().equals(exchangeDimension)
                && player.level().getBlockState(exchangePos).is(ModBlocks.EXCHANGE)
                && player.distanceToSqr(Vec3.atCenterOf(exchangePos)) <= 64.0D;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            for (int i = 0; i < EXCHANGE_SLOTS; i++) {
                ItemStack stack = container.removeItemNoUpdate(i);
                if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    public void performExchange(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        VmExchangeManager.get(sp.level().getServer()).exchange(sp, this);
        broadcastChanges();
    }

    public boolean canExchange(Player player) {
        return player instanceof net.minecraft.server.level.ServerPlayer;
    }

    @Override public int exchangeSlots() { return EXCHANGE_SLOTS; }
    @Override public ItemStack stackInSlot(int slot) { return container.getItem(slot); }
    @Override public void removeFromSlot(int slot, int amount) { container.removeItem(slot, amount); }
    @Override public ResourceKey<Level> exchangeDimension() { return exchangeDimension; }
    @Override public BlockPos exchangePos() { return exchangePos; }
}
