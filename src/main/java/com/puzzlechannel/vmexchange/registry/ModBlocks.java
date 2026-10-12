package com.puzzlechannel.vmexchange.registry;

import com.puzzlechannel.vmexchange.util.Ids;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;

public final class ModBlocks {
    public static final Identifier EXCHANGE_ID = Ids.id("exchange");
    public static final ResourceKey<Block> EXCHANGE_KEY = ResourceKey.create(Registries.BLOCK, EXCHANGE_ID);
    public static final ResourceKey<Item> EXCHANGE_ITEM_KEY = ResourceKey.create(Registries.ITEM, EXCHANGE_ID);
    public static final Block EXCHANGE = Registry.register(BuiltInRegistries.BLOCK, EXCHANGE_KEY,
            new VmExchangeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).setId(EXCHANGE_KEY).sound(SoundType.METAL)));
    public static final Item EXCHANGE_ITEM = Registry.register(BuiltInRegistries.ITEM, EXCHANGE_ITEM_KEY,
            new BlockItem(EXCHANGE, new Item.Properties().useBlockDescriptionPrefix().setId(EXCHANGE_ITEM_KEY)));

    public static void initialize() {
        // Deliberately provide the placeable block item in normal creative tabs.
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> entries.accept(EXCHANGE_ITEM));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(entries -> entries.accept(EXCHANGE_ITEM));
        // No crafting recipe is registered for vmexchange:exchange; it is admin-placed only.
    }

    private ModBlocks() {}
}
