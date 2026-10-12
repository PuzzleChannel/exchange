package com.puzzlechannel.vmexchange.client;

import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import com.puzzlechannel.vmexchange.client.screen.VmExchangeScreen;
import com.puzzlechannel.vmexchange.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class VmExchangeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VmExchangeNetworking.initializeClient();
        MenuScreens.register(ModMenus.EXCHANGE, VmExchangeScreen::new);
    }
}
