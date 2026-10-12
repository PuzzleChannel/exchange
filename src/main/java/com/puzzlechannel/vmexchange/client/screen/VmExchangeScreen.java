package com.puzzlechannel.vmexchange.client.screen;

import com.puzzlechannel.vmexchange.menu.VmExchangeMenu;
import com.puzzlechannel.vmexchange.network.ClientState;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class VmExchangeScreen extends AbstractContainerScreen<VmExchangeMenu> {
    // One continuous Minecraft-style container: the exchange area flows directly
    // into the player inventory without overlapping or creating a clipped top row.
    private static final int GUI_WIDTH = 176;
    private static final int EXCHANGE_HEIGHT = 143;
    private static final int INVENTORY_TOP = EXCHANGE_HEIGHT;
    private static final int INVENTORY_SECTION_HEIGHT = 83;
    private static final int TOTAL_GUI_HEIGHT = INVENTORY_TOP + INVENTORY_SECTION_HEIGHT;

    private static final Identifier VANILLA_INVENTORY_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    // Exchange slots: four materials in one centered row.
    private static final int MATERIAL_SLOT_Y = 36;
    private static final int[] MATERIAL_SLOT_X = {31, 63, 95, 127};

    // Centered reward area below the materials with a clear visual separation.
    private static final int ARROW_X = 84;
    private static final int ARROW_Y = 64;
    private static final int COIN_SLOT_X = 79;
    private static final int COIN_SLOT_Y = 77;

    private static final int EXCHANGE_BUTTON_X = 46;
    private static final int EXCHANGE_BUTTON_Y = 101;
    private static final int EXCHANGE_BUTTON_WIDTH = 84;
    private static final int EXCHANGE_BUTTON_HEIGHT = 20;

    private static final int WEEK_TEXT_Y = 126;

    // The snapshot is received asynchronously. Only start listening for successful
    // purchases after the first real snapshot has arrived, so opening the screen
    // never produces a false purchase sound.
    private boolean soundStateInitialized = false;
    private int lastPlayedEarned = 0;

    public VmExchangeScreen(VmExchangeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GUI_WIDTH, TOTAL_GUI_HEIGHT);

        // The exchange title is drawn in the vanilla-style dark header ourselves.
        this.titleLabelX = -1000;
        this.titleLabelY = -1000;

        // Do not draw the vanilla inventory caption: the player already sees the
        // inventory itself, and the extra word would sit on top of the panel.
        this.inventoryLabelX = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);

        drawExchangePanel(graphics);

        // The unified frame is our own background, so do NOT blit the full vanilla
        // inventory texture over the player-inventory area. Doing that would paint a
        // second opaque panel on top of the slot wells. Instead, draw only the 18x18
        // vanilla slot wells here; the actual player items/slot contents are still
        // rendered by AbstractContainerScreen in its normal contents pass.
        drawPlayerInventorySlotWells(graphics);

        for (int i = 0; i < 4; i++) {
            drawVanillaStyleSlot(graphics, leftPos + MATERIAL_SLOT_X[i], topPos + MATERIAL_SLOT_Y, false);
        }
        drawCoinPreview(graphics);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // The old duplicate heading "Ресурсы для обмена" is intentionally removed from
        // the body. It now lives in the header instead.
        drawCenteredText(graphics, Component.translatable("vmexchange.inputs"), 11, 0xFFFFFFFF);

        VmExchangeNetworking.SnapshotPayload state = ClientState.get();
        playPurchaseSoundOnStateChange(state);
        String[] ids = {state.m1(), state.m2(), state.m3(), state.m4()};
        int price = Math.max(1, state.price());

        // Empty exchange slots show the required item as a dimmed ghost.
        for (int i = 0; i < 4; i++) {
            if (menu.stackInSlot(i).isEmpty()) {
                renderFadedGhost(graphics, ids[i], MATERIAL_SLOT_X[i] + 1, MATERIAL_SLOT_Y + 1);
            }
        }

        // Required amount directly below each material slot.
        for (int i = 0; i < 4; i++) {
            drawCenteredText(graphics, Component.literal("×" + price),
                    MATERIAL_SLOT_Y + 20, 0xFF303030, MATERIAL_SLOT_X[i] + 9);
        }

        // A clean centered downward arrow separates the inputs from the reward.
        drawCenteredText(graphics, Component.literal("▼"), ARROW_Y, 0xFF4A4A4A, ARROW_X + 4);

        boolean ready = canExchangeOnce(state);
        drawButton(graphics, mouseX, mouseY, ready);

        if (state.earned() >= state.limit()) {
            graphics.text(font, Component.translatable("vmexchange.limit_finished"),
                    8, WEEK_TEXT_Y, 0xFFB03030, false);
        } else {
            graphics.text(font,
                    Component.translatable("vmexchange.received_short", state.earned(), state.limit()),
                    8, WEEK_TEXT_Y, 0xFF303030, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && insideExchangeButton(event.x(), event.y())) {
            VmExchangeNetworking.SnapshotPayload state = ClientState.get();
            if (state.earned() >= state.limit()) {
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.sendOverlayMessage(Component.translatable("vmexchange.limit_finished"));
                }
                return true;
            }
            if (canExchangeOnce(state)) {
                ClientPlayNetworking.send(new VmExchangeNetworking.ExchangeRequestPayload());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void playPurchaseSoundOnStateChange(VmExchangeNetworking.SnapshotPayload state) {
        if (!soundStateInitialized) {
            // The default ClientState is empty. Wait for the server snapshot before
            // establishing the baseline, otherwise opening the menu could sound like
            // a purchase when the player's existing weekly progress is received.
            if (!state.m1().isBlank() && !state.m2().isBlank() && !state.m3().isBlank() && !state.m4().isBlank()) {
                lastPlayedEarned = state.earned();
                soundStateInitialized = true;
            }
            return;
        }

        if (state.earned() > lastPlayedEarned) {
            lastPlayedEarned = state.earned();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.VILLAGER_TRADE, 0.85F, 1.08F);
            }
        } else if (state.earned() < lastPlayedEarned) {
            // A weekly reset may happen while the screen is open; re-baseline without
            // playing a purchase sound for the reset itself.
            lastPlayedEarned = state.earned();
        }
    }

    private void drawExchangePanel(GuiGraphicsExtractor graphics) {
        int x = leftPos;
        int y = topPos;
        int right = x + GUI_WIDTH;
        int bottom = y + TOTAL_GUI_HEIGHT;
        int inventoryY = y + INVENTORY_TOP;

        // One continuous vanilla-style container surface. The exchange area and the
        // player inventory share one inner background and one outer bevel, with no
        // second framed panel and no horizontal seam where the two areas meet.
        // The edge treatment deliberately matches Minecraft containers: bright on
        // the top/left and dark on the bottom/right.
        graphics.fill(x, y, right, bottom, 0xFF4A4A4A);
        graphics.fill(x + 1, y + 1, right - 2, bottom - 2, 0xFFF1F1F1);
        graphics.fill(x + 2, y + 2, right - 3, bottom - 3, 0xFF8B8B8B);
        graphics.fill(x + 3, y + 3, right - 3, bottom - 3, 0xFFC6C6C6);

        // Keep the surface identical on both sides of the transition. Do not blit
        // inventory.png here: its own outer frame would create the white/right-dark
        // texture breaks seen in the previous version. AbstractContainerScreen then
        // renders the actual player Slot wells and item stacks in its contents pass.

        // Dark inset header with the same simple, blocky bevel language as vanilla
        // Minecraft container screens.
        graphics.fill(x + 5, y + 5, right - 5, y + 30, 0xFF1F1F1F);
        graphics.fill(x + 6, y + 6, right - 6, y + 29, 0xFF3B3B3B);
        graphics.fill(x + 6, y + 29, right - 6, y + 30, 0xFF2A2A2A);

    }

    private void drawPlayerInventorySlotWells(GuiGraphicsExtractor graphics) {
        // Player inventory slots from VmExchangeMenu, expressed relative to the
        // container origin. Keep these coordinates in lockstep with the menu.
        final int inventoryTop = 143;
        final int slotX0 = 8;
        final int row1Y = inventoryTop + 1;
        final int row2Y = inventoryTop + 19;
        final int row3Y = inventoryTop + 37;
        final int hotbarY = inventoryTop + 59;

        int[] ys = {row1Y, row2Y, row3Y, hotbarY};
        for (int row = 0; row < ys.length; row++) {
            for (int col = 0; col < 9; col++) {
                drawVanillaStyleSlot(graphics, leftPos + slotX0 + col * 18,
                        topPos + ys[row], false);
            }
        }
    }

    private void drawCoinPreview(GuiGraphicsExtractor graphics) {
        boolean ready = canExchangeOnce(ClientState.get());
        int coinX = leftPos + COIN_SLOT_X;
        int coinY = topPos + COIN_SLOT_Y;

        drawVanillaStyleSlot(graphics, coinX, coinY, ready);
        if (ready) {
            // A small green accent makes the available reward immediately readable,
            // while keeping the slot itself visually consistent with Minecraft UI.
            graphics.fill(coinX + 1, coinY + 1, coinX + 17, coinY + 2, 0xFF61C761);
            graphics.fill(coinX + 1, coinY + 2, coinX + 2, coinY + 17, 0xFF61C761);
            renderFullItem(graphics, "vmtrade:vmcoin", coinX + 1, coinY + 1);
        } else {
            renderFadedGhost(graphics, "vmtrade:vmcoin", coinX + 1, coinY + 1);
        }
    }

    private boolean canExchangeOnce(VmExchangeNetworking.SnapshotPayload state) {
        if (state.earned() >= state.limit()) return false;

        String[] ids = {state.m1(), state.m2(), state.m3(), state.m4()};
        int price = Math.max(1, state.price());

        // All four resource slots are mandatory: each must contain the designated
        // material and enough items for exactly one current-tier exchange.
        for (int i = 0; i < 4; i++) {
            ItemStack stack = menu.stackInSlot(i);
            if (stack.isEmpty()) return false;
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(ids[i])) return false;
            if (stack.getCount() < price) return false;
        }
        return true;
    }

    private void drawVanillaStyleSlot(GuiGraphicsExtractor graphics, int x, int y, boolean ready) {
        // Reproduce the standard Minecraft 18x18 slot bevel from the vanilla
        // container texture so exchange inputs look like real inventory slots.
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                VANILLA_INVENTORY_TEXTURE,
                x, y,
                7.0F, 83.0F,
                18, 18,
                256, 256
        );

        if (ready) {
            // Availability is communicated by a thin green top/left accent rather
            // than replacing the Minecraft slot artwork.
            graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF70C970);
            graphics.fill(x + 1, y + 2, x + 2, y + 17, 0xFF70C970);
            graphics.fill(x + 2, y + 16, x + 17, y + 17, 0xFF3D7B3D);
            graphics.fill(x + 16, y + 2, x + 17, y + 16, 0xFF3D7B3D);
        }
    }

    private void renderFadedGhost(GuiGraphicsExtractor graphics, String id, int x, int y) {
        if (id == null || id.isBlank()) return;
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return;
        var item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
        if (item == null) return;
        graphics.item(new ItemStack(item), x, y);
        graphics.fill(x, y, x + 16, y + 16, 0x99000000);
    }

    private void renderFullItem(GuiGraphicsExtractor graphics, String id, int x, int y) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return;
        var item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
        if (item == null) return;
        graphics.item(new ItemStack(item), x, y);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean ready) {
        boolean hovered = ready && insideExchangeButton(mouseX, mouseY);
        // extractLabels() is already translated to the container origin, so these
        // coordinates must remain local to the exchange panel.
        int x = EXCHANGE_BUTTON_X;
        int y = EXCHANGE_BUTTON_Y;

        // A compact, vanilla-style 3D button. Green only appears when the entire
        // four-material set is valid, making the action state unambiguous.
        int top = ready ? (hovered ? 0xFF8AE28A : 0xFF74C874) : 0xFFD0D0D0;
        int side = ready ? (hovered ? 0xFF2E7A2E : 0xFF356D35) : 0xFF5A5A5A;
        int face = ready ? (hovered ? 0xFF5BAE5B : 0xFF4A914A) : 0xFF8B8B8B;

        graphics.fill(x, y, x + EXCHANGE_BUTTON_WIDTH, y + EXCHANGE_BUTTON_HEIGHT, side);
        graphics.fill(x + 1, y + 1, x + EXCHANGE_BUTTON_WIDTH - 1, y + EXCHANGE_BUTTON_HEIGHT - 2, top);
        graphics.fill(x + 2, y + 2, x + EXCHANGE_BUTTON_WIDTH - 2, y + EXCHANGE_BUTTON_HEIGHT - 3, face);

        Component text = Component.translatable("vmexchange.exchange");
        int textX = x + EXCHANGE_BUTTON_WIDTH / 2 - font.width(text) / 2;
        graphics.text(font, text, textX + 1, y + 6, 0xFF303030, false);
        graphics.text(font, text, textX, y + 5, 0xFFFFFFFF, false);
    }

    private boolean insideExchangeButton(double mouseX, double mouseY) {
        return mouseX >= leftPos + EXCHANGE_BUTTON_X
                && mouseX < leftPos + EXCHANGE_BUTTON_X + EXCHANGE_BUTTON_WIDTH
                && mouseY >= topPos + EXCHANGE_BUTTON_Y
                && mouseY < topPos + EXCHANGE_BUTTON_Y + EXCHANGE_BUTTON_HEIGHT;
    }

    private void drawCenteredText(GuiGraphicsExtractor graphics, Component component, int relativeY, int color) {
        drawCenteredText(graphics, component, relativeY, color, GUI_WIDTH / 2);
    }

    private void drawCenteredText(GuiGraphicsExtractor graphics, Component component, int relativeY, int color, int centerX) {
        graphics.text(font, component, centerX - font.width(component) / 2,
                relativeY, color, false);
    }


}
