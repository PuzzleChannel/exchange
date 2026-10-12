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
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.MinecraftServer;
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

        // Remove the group binding when a player breaks an exchange block, so a newly
        // placed block at the same coordinates never inherits an old country's access rule.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!state.is(ModBlocks.EXCHANGE)) return;
            MinecraftServer server = level.getServer();
            if (server != null) {
                VmExchangeManager.get(server).unbindGroup(level.dimension(), pos);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> VmExchangeManager.get(server).ensureCurrentWeek(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) VmExchangeManager.get(server).ensureCurrentWeek(server);
        });
    }
}
