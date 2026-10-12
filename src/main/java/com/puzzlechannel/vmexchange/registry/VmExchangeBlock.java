package com.puzzlechannel.vmexchange.registry;

import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.menu.VmExchangeMenu;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class VmExchangeBlock extends Block {
    public VmExchangeBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

        VmExchangeManager manager = VmExchangeManager.get(serverPlayer.level().getServer());
        String requiredGroup = manager.exchangeGroup(level.dimension(), pos);
        if (requiredGroup == null || requiredGroup.isBlank()) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "Этот Обменник ещё не настроен. Администратор должен навести прицел на блок и выполнить /vmexchange bindgroup <группа>."));
            return InteractionResult.SUCCESS;
        }

        if (!manager.hasExchangeAccess(serverPlayer, level.dimension(), pos)) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "Доступ запрещён. Этот Обменник предназначен для группы LuckPerms: " + requiredGroup));
            return InteractionResult.SUCCESS;
        }

        serverPlayer.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> new VmExchangeMenu(syncId, inventory, level.dimension(), pos),
                Component.translatable("vmexchange.inputs")));
        VmExchangeNetworking.sendSnapshot(serverPlayer);
        return InteractionResult.SUCCESS;
    }
}
