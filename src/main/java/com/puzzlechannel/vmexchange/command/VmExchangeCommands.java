package com.puzzlechannel.vmexchange.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.puzzlechannel.vmexchange.config.VmExchangeConfig;
import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.data.LuckPermsGroupAccess;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import com.puzzlechannel.vmexchange.registry.ModBlocks;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public final class VmExchangeCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            var root = Commands.literal("vmexchange").requires(Commands.hasPermission(Commands.LEVEL_ADMINS));

            root.then(Commands.literal("info")
                    .executes(ctx -> info(ctx.getSource().getPlayerOrException())));

            root.then(Commands.literal("bindgroup")
                    .then(Commands.argument("group", StringArgumentType.word())
                            .executes(ctx -> bindGroup(
                                    ctx.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(ctx, "group")))));

            root.then(Commands.literal("unbindgroup")
                    .executes(ctx -> unbindGroup(ctx.getSource().getPlayerOrException())));

            root.then(Commands.literal("groupinfo")
                    .executes(ctx -> groupInfo(ctx.getSource().getPlayerOrException())));

            root.then(Commands.literal("setmaterial")
                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, 4))
                            .then(Commands.argument("item", StringArgumentType.word())
                                    .executes(ctx -> setMaterial(
                                            ctx.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(ctx, "slot"),
                                            StringArgumentType.getString(ctx, "item"))))));

            root.then(Commands.literal("setmaterials")
                    .then(Commands.argument("item1", StringArgumentType.word())
                            .then(Commands.argument("item2", StringArgumentType.word())
                                    .then(Commands.argument("item3", StringArgumentType.word())
                                            .then(Commands.argument("item4", StringArgumentType.word())
                                                    .executes(ctx -> setMaterials(
                                                            ctx.getSource().getPlayerOrException(),
                                                            List.of(
                                                                    StringArgumentType.getString(ctx, "item1"),
                                                                    StringArgumentType.getString(ctx, "item2"),
                                                                    StringArgumentType.getString(ctx, "item3"),
                                                                    StringArgumentType.getString(ctx, "item4")))))))));

            root.then(Commands.literal("randomize").executes(ctx -> {
                VmExchangeManager.get(ctx.getSource().getServer()).resetWeek(true, null);
                ctx.getSource().sendSuccess(() -> Component.literal("Новые 4 материала выбраны, недельный прогресс сброшен."), true);
                return 1;
            }));

            root.then(Commands.literal("limit")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
                        VmExchangeConfig.get().weeklyLimit = IntegerArgumentType.getInteger(ctx, "amount");
                        VmExchangeConfig.save();
                        ctx.getSource().sendSuccess(() -> Component.literal("Недельный лимит: " + VmExchangeConfig.get().weeklyLimit), true);
                        return 1;
                    })));

            root.then(Commands.literal("price")
                    .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                            .then(Commands.argument("price", IntegerArgumentType.integer(1)).executes(ctx -> {
                                int i = IntegerArgumentType.getInteger(ctx, "tier") - 1;
                                int price = IntegerArgumentType.getInteger(ctx, "price");
                                var cfg = VmExchangeConfig.get();
                                ensureTier(cfg, i);
                                cfg.tiers.get(i).price = price;
                                cfg.tiers.sort(Comparator.comparingInt(t -> t.limit));
                                VmExchangeConfig.save();
                                ctx.getSource().sendSuccess(() -> Component.literal("Цена ступени обновлена."), true);
                                return 1;
                            }))));

            root.then(Commands.literal("tier")
                    .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                            .then(Commands.argument("limit", IntegerArgumentType.integer(1)).executes(ctx -> {
                                int i = IntegerArgumentType.getInteger(ctx, "tier") - 1;
                                int limit = IntegerArgumentType.getInteger(ctx, "limit");
                                var cfg = VmExchangeConfig.get();
                                ensureTier(cfg, i);
                                cfg.tiers.get(i).limit = limit;
                                cfg.tiers.sort(Comparator.comparingInt(t -> t.limit));
                                VmExchangeConfig.save();
                                ctx.getSource().sendSuccess(() -> Component.literal("Лимит ступени обновлён."), true);
                                return 1;
                            }))));

            root.then(Commands.literal("resetweek").executes(ctx -> {
                VmExchangeManager.get(ctx.getSource().getServer()).resetWeek(true, null);
                ctx.getSource().sendSuccess(() -> Component.literal("Новая неделя принудительно начата."), true);
                return 1;
            }));

            root.then(Commands.literal("resetplayer")
                    .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                        VmExchangeManager.get(ctx.getSource().getServer()).data().setPlayer(player.getUUID(), 0, 0);
                        ctx.getSource().sendSuccess(() -> Component.literal("Прогресс игрока сброшен: " + player.getGameProfile().name()), true);
                        VmExchangeNetworking.sendSnapshot(player);
                        return 1;
                    })));

            root.then(Commands.literal("player")
                    .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                        var manager = VmExchangeManager.get(ctx.getSource().getServer());
                        var state = manager.state(player);
                        var config = VmExchangeConfig.get();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Игрок: " + player.getGameProfile().name()
                                        + " | Получено: " + state.weeklyEarned() + " / " + config.weeklyLimit
                                        + " | Уровень: " + state.tier()
                                        + " | Курс: " + manager.currentPrice(player)), false);
                        return 1;
                    })));

            root.then(Commands.literal("reload").executes(ctx -> {
                VmExchangeConfig.load();
                ctx.getSource().sendSuccess(() -> Component.literal("Конфигурация ВМуткоин Обменника перезагружена."), true);
                return 1;
            }));

            dispatcher.register(root);
        });
    }

    private static int bindGroup(ServerPlayer player, String requestedGroup) {
        String group = requestedGroup.trim().toLowerCase(Locale.ROOT);
        BlockPos pos = lookedAtExchange(player);
        if (pos == null) {
            player.sendSystemMessage(Component.literal(
                    "Не вижу Обменник в радиусе 8 блоков. Наведите прицел на блок и повторите команду."));
            return 0;
        }

        if (!LuckPermsGroupAccess.isAvailable()) {
            player.sendSystemMessage(Component.literal("LuckPerms API недоступен. Проверьте установку LuckPerms на сервере."));
            return 0;
        }
        if (!LuckPermsGroupAccess.groupExists(group)) {
            player.sendSystemMessage(Component.literal(
                    "Группа LuckPerms '" + group + "' не найдена. Проверьте имя группы."));
            return 0;
        }

        VmExchangeManager.get(player.level().getServer())
                .bindGroup(player.level().dimension(), pos, group);
        player.sendSystemMessage(Component.literal(
                "Обменник на " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + " привязан к группе LuckPerms: " + group));
        return 1;
    }

    private static int unbindGroup(ServerPlayer player) {
        BlockPos pos = lookedAtExchange(player);
        if (pos == null) {
            player.sendSystemMessage(Component.literal(
                    "Не вижу Обменник в радиусе 8 блоков. Наведите прицел на блок и повторите команду."));
            return 0;
        }
        VmExchangeManager.get(player.level().getServer()).unbindGroup(player.level().dimension(), pos);
        player.sendSystemMessage(Component.literal(
                "Привязка группы удалена. Обменник закрыт для всех, пока вы не назначите новую группу."));
        return 1;
    }

    private static int groupInfo(ServerPlayer player) {
        BlockPos pos = lookedAtExchange(player);
        if (pos == null) {
            player.sendSystemMessage(Component.literal(
                    "Не вижу Обменник в радиусе 8 блоков. Наведите прицел на блок и повторите команду."));
            return 0;
        }
        String group = VmExchangeManager.get(player.level().getServer())
                .exchangeGroup(player.level().dimension(), pos);
        player.sendSystemMessage(Component.literal(
                group == null || group.isBlank()
                        ? "Этот Обменник не привязан к группе и закрыт для всех."
                        : "Группа доступа этого Обменника: " + group));
        return 1;
    }

    private static BlockPos lookedAtExchange(ServerPlayer player) {
        var hit = player.pick(8.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = blockHit.getBlockPos();
        return player.level().getBlockState(pos).is(ModBlocks.EXCHANGE) ? pos : null;
    }

    private static int info(ServerPlayer player) {
        var m = VmExchangeManager.get(player.level().getServer()); var cfg = VmExchangeConfig.get();
        player.sendSystemMessage(Component.literal("Текущая неделя: " + m.data().weekKey()));
        player.sendSystemMessage(Component.literal("До сброса: " + untilReset(cfg)));
        for (int i = 0; i < 4; i++) player.sendSystemMessage(Component.literal("Материал " + (i + 1) + ": " + (i < m.materials().size() ? m.materials().get(i) : "-")));
        player.sendSystemMessage(Component.literal("Недельный лимит: " + cfg.weeklyLimit));
        return 1;
    }

    private static int setMaterial(ServerPlayer player, int slot, String id) {
        if (Identifier.tryParse(id) == null) { player.sendSystemMessage(Component.literal("Некорректный ID предмета.")); return 0; }
        var m = VmExchangeManager.get(player.level().getServer()); List<String> list = new ArrayList<>(m.materials()); while (list.size() < 4) list.add("");
        list.set(slot - 1, id);
        try { m.setMaterials(list, false); } catch (Exception e) { player.sendSystemMessage(Component.literal("Предмет не найден или набор невалиден.")); return 0; }
        player.sendSystemMessage(Component.literal("Материал " + slot + " установлен: " + id)); return 1;
    }
    private static int setMaterials(ServerPlayer player, List<String> ids) {
        try { VmExchangeManager.get(player.level().getServer()).setMaterials(ids, false); player.sendSystemMessage(Component.literal("Текущие 4 материала установлены.")); return 1; }
        catch (Exception e) { player.sendSystemMessage(Component.literal("Нужно указать 4 существующих ID предметов без повторов.")); return 0; }
    }
    private static void ensureTier(VmExchangeConfig cfg, int i) {
        while (cfg.tiers.size() <= i) cfg.tiers.add(new VmExchangeConfig.Tier(cfg.weeklyLimit, 1));
    }
    private static String untilReset(VmExchangeConfig cfg) {
        DayOfWeek day; try { day = DayOfWeek.valueOf(cfg.reset.day.toUpperCase(Locale.ROOT)); } catch (Exception e) { day = DayOfWeek.MONDAY; }
        ZonedDateTime now = ZonedDateTime.now(); ZonedDateTime next = now.with(TemporalAdjusters.nextOrSame(day)).withHour(cfg.reset.hour).withMinute(cfg.reset.minute).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusWeeks(1);
        Duration d = Duration.between(now, next); return d.toDaysPart() + "д " + d.toHoursPart() + "ч " + d.toMinutesPart() + "м";
    }
    private VmExchangeCommands() {}
}
