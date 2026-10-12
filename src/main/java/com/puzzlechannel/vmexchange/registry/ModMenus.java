package com.puzzlechannel.vmexchange.registry;

import com.puzzlechannel.vmexchange.menu.VmExchangeMenu;
import com.puzzlechannel.vmexchange.util.Ids;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    public static final MenuType<VmExchangeMenu> EXCHANGE = Registry.register(
            BuiltInRegistries.MENU, Ids.id("exchange"), new MenuType<>(VmExchangeMenu::new, FeatureFlagSet.of()));
    public static void initialize() {}
    private ModMenus() {}
}
