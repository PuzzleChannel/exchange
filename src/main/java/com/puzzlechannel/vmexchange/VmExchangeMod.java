package com.puzzlechannel.vmexchange;

import com.puzzlechannel.vmexchange.command.VmExchangeCommands;
import com.puzzlechannel.vmexchange.config.VmExchangeConfig;
import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import com.puzzlechannel.vmexchange.registry.ModBlocks;
import com.puzzlechannel.vmexchange.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VmExchangeMod implements ModInitializer {
    public static final String MOD_ID = "vmexchange";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        VmExchangeConfig.load();
        ModBlocks.initialize();
        ModMenus.initialize();
        VmExchangeNetworking.initialize();
        VmExchangeCommands.initialize();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> VmExchangeManager.get(server).ensureCurrentWeek(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) VmExchangeManager.get(server).ensureCurrentWeek(server);
        });
    }
}
