package com.puzzlechannel.vmexchange.data;

import com.puzzlechannel.vmexchange.util.Ids;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public final class VmExchangeData extends SavedData {
    public static final Codec<PlayerState> PLAYER_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("weeklyEarned").forGetter(PlayerState::weeklyEarned),
            Codec.INT.fieldOf("tier").forGetter(PlayerState::tier)
    ).apply(i, PlayerState::new));

    public static final Codec<VmExchangeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("weekKey").forGetter(d -> d.weekKey),
            Codec.STRING.listOf().fieldOf("materials").forGetter(d -> d.materials),
            Codec.unboundedMap(Codec.STRING, PLAYER_CODEC).fieldOf("players").forGetter(d -> d.players),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("exchangeGroups", Map.of()).forGetter(d -> d.exchangeGroups)
    ).apply(i, (weekKey, materials, players, exchangeGroups) ->
            new VmExchangeData(weekKey, new ArrayList<>(materials), new HashMap<>(players), new HashMap<>(exchangeGroups))));

    @SuppressWarnings("DataFlowIssue")
    public static final SavedDataType<VmExchangeData> TYPE = new SavedDataType<>(
            Ids.id("exchange_data"),
            VmExchangeData::newEmpty,
            CODEC,
            null
    );

    private String weekKey;
    private final List<String> materials;
    private final Map<String, PlayerState> players;
    // Key is dimension-id@x,y,z. A separate group can be assigned to each placed exchange block.
    private final Map<String, String> exchangeGroups;

    public record PlayerState(int weeklyEarned, int tier) {}

    public VmExchangeData() {
        this("", new ArrayList<>(), new HashMap<>(), new HashMap<>());
    }

    private VmExchangeData(String weekKey, List<String> materials,
                           Map<String, PlayerState> players, Map<String, String> exchangeGroups) {
        this.weekKey = weekKey;
        this.materials = materials;
        this.players = players;
        this.exchangeGroups = exchangeGroups;
    }

    public static VmExchangeData newEmpty() { return new VmExchangeData(); }

    public String weekKey() { return weekKey; }
    public void setWeekKey(String key) { weekKey = key; setDirty(); }
    public List<String> materials() { return Collections.unmodifiableList(materials); }
    public void setMaterials(Collection<String> ids) { materials.clear(); materials.addAll(ids.stream().limit(4).toList()); setDirty(); }
    public Map<String, PlayerState> players() { return players; }
    public PlayerState player(UUID uuid) { return players.getOrDefault(uuid.toString(), new PlayerState(0, 0)); }
    public void setPlayer(UUID uuid, int earned, int tier) {
        players.put(uuid.toString(), new PlayerState(Math.max(0, earned), Math.max(0, tier)));
        setDirty();
    }
    public void resetPlayers() { players.clear(); setDirty(); }

    public String exchangeGroup(ResourceKey<Level> dimension, BlockPos pos) {
        return exchangeGroups.get(exchangeKey(dimension, pos));
    }

    public void setExchangeGroup(ResourceKey<Level> dimension, BlockPos pos, String group) {
        exchangeGroups.put(exchangeKey(dimension, pos), group);
        setDirty();
    }

    public void removeExchangeGroup(ResourceKey<Level> dimension, BlockPos pos) {
        if (exchangeGroups.remove(exchangeKey(dimension, pos)) != null) setDirty();
    }

    private static String exchangeKey(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
