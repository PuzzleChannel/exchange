package com.puzzlechannel.vmexchange.config;

import com.google.gson.*;
import com.puzzlechannel.vmexchange.VmExchangeMod;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class VmExchangeConfig {
    public static final int DEFAULT_LIMIT = 25;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = Path.of("config", "vmexchange.json");

    public int weeklyLimit = 25;
    public Reset reset = new Reset();
    public boolean randomizeMaterials = true;
    public List<String> currentMaterials = new ArrayList<>(List.of(
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond", "minecraft:ender_pearl"));

    // Slots 1-3: overworld resources, including processed resources, gems and raw ores.
    // Nether-only resources are intentionally excluded.
    public List<String> standardMaterialIds = new ArrayList<>(List.of(
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot",
            "minecraft:diamond", "minecraft:emerald", "minecraft:coal",
            "minecraft:redstone", "minecraft:lapis_lazuli",
            "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper"));

    // Slot 4: harder-to-automate overworld materials. Emerald ore is deliberately
    // excluded. Only overworld stone-hosted ore blocks are used here; no Nether ore.
    public List<String> specialMaterialIds = new ArrayList<>(List.of(
            "minecraft:ender_pearl", "minecraft:iron_ore", "minecraft:gold_ore",
            "minecraft:copper_ore", "minecraft:coal_ore", "minecraft:lapis_ore",
            "minecraft:redstone_ore", "minecraft:diamond_ore"));

    public Map<String, MaterialConfig> materials = new LinkedHashMap<>();
    public List<Tier> tiers = new ArrayList<>(List.of(
            new Tier(5, 1), new Tier(10, 5), new Tier(15, 10), new Tier(20, 15), new Tier(24, 20), new Tier(25, 25)));

    public static final class Reset {
        public String day = "MONDAY";
        public int hour = 0;
        public int minute = 0;
    }
    public static final class MaterialConfig {
        public boolean enabled = true;
        public double weight = 1.0;
        public MaterialConfig() {}
        public MaterialConfig(boolean enabled, double weight) { this.enabled = enabled; this.weight = weight; }
    }
    public static final class Tier {
        public int limit;
        public int price;
        public Tier(int limit, int price) { this.limit = limit; this.price = price; }
    }

    private static VmExchangeConfig INSTANCE;

    public static synchronized VmExchangeConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static synchronized void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (!Files.exists(PATH)) {
                INSTANCE = defaults();
                save();
                return;
            }
            INSTANCE = GSON.fromJson(Files.readString(PATH), VmExchangeConfig.class);
            if (INSTANCE == null) INSTANCE = defaults();
            INSTANCE.normalize();
        } catch (Exception e) {
            VmExchangeMod.LOGGER.error("Failed to load config; using defaults", e);
            INSTANCE = defaults();
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            VmExchangeMod.LOGGER.error("Failed to save config", e);
        }
    }

    private static VmExchangeConfig defaults() {
        VmExchangeConfig c = new VmExchangeConfig();
        LinkedHashSet<String> poolIds = new LinkedHashSet<>();
        poolIds.addAll(c.standardMaterialIds);
        poolIds.addAll(c.specialMaterialIds);
        for (String id : poolIds) c.materials.put(id, new MaterialConfig(true, 1.0));
        return c;
    }

    private void normalize() {
        weeklyLimit = Math.max(1, weeklyLimit);
        if (reset == null) reset = new Reset();
        reset.hour = Math.max(0, Math.min(23, reset.hour));
        reset.minute = Math.max(0, Math.min(59, reset.minute));
        // Migrate old configs: remove Nether/quartz-only entries from the automatic
        // material pools so an older config cannot reintroduce Nether resources.
        Set<String> forbiddenNetherIds = Set.of(
                "minecraft:quartz", "minecraft:quartz_ore",
                "minecraft:netherite_scrap", "minecraft:netherite_ingot", "minecraft:ancient_debris",
                "minecraft:nether_gold_ore", "minecraft:netherrack", "minecraft:soul_sand",
                "minecraft:soul_soil", "minecraft:nether_wart", "minecraft:glowstone"
        );
        if (currentMaterials == null) currentMaterials = new ArrayList<>();
        if (standardMaterialIds == null || standardMaterialIds.isEmpty()) {
            standardMaterialIds = new ArrayList<>(List.of(
                    "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot",
                    "minecraft:diamond", "minecraft:emerald", "minecraft:coal",
                    "minecraft:redstone", "minecraft:lapis_lazuli",
                    "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper"));
        }
        if (specialMaterialIds == null || specialMaterialIds.isEmpty()) {
            specialMaterialIds = new ArrayList<>(List.of(
                    "minecraft:ender_pearl", "minecraft:iron_ore", "minecraft:gold_ore",
                    "minecraft:copper_ore", "minecraft:coal_ore", "minecraft:lapis_ore",
                    "minecraft:redstone_ore", "minecraft:diamond_ore"));
        }
        standardMaterialIds.removeIf(forbiddenNetherIds::contains);
        specialMaterialIds.removeIf(forbiddenNetherIds::contains);
        if (materials == null) materials = new LinkedHashMap<>();
        materials.remove("minecraft:quartz");
        materials.remove("minecraft:quartz_ore");
        materials.remove("minecraft:netherite_scrap");
        materials.remove("minecraft:netherite_ingot");
        materials.remove("minecraft:ancient_debris");
        materials.remove("minecraft:nether_gold_ore");
        LinkedHashSet<String> allPoolIds = new LinkedHashSet<>();
        allPoolIds.addAll(standardMaterialIds);
        allPoolIds.addAll(specialMaterialIds);
        for (String id : allPoolIds) materials.putIfAbsent(id, new MaterialConfig(true, 1.0));
        if (tiers == null || tiers.isEmpty()) tiers = new ArrayList<>(List.of(new Tier(weeklyLimit, 1)));
        tiers.removeIf(t -> t == null || t.limit <= 0 || t.price <= 0);
        tiers.sort(Comparator.comparingInt(t -> t.limit));

        // Migrate every older built-in six-step schedule to the exact requested
        // schedule: the 25th coin is the only coin in the final 25-material tier.
        boolean oldBuiltInSchedule = tiers.size() == 6
                && tiers.get(0).limit == 5 && tiers.get(0).price == 1
                && tiers.get(1).limit == 10 && tiers.get(1).price == 5
                && tiers.get(2).limit == 15 && tiers.get(2).price == 10
                && tiers.get(3).limit == 20 && tiers.get(3).price == 15
                && ((tiers.get(4).limit == 25 && tiers.get(4).price == 20 && tiers.get(5).limit == 26 && tiers.get(5).price == 25)
                    || (tiers.get(4).limit == 25 && tiers.get(4).price == 25));
        boolean oldFiveStepSchedule = tiers.size() == 5
                && tiers.get(0).limit == 5 && tiers.get(0).price == 3
                && tiers.get(1).limit == 10 && tiers.get(1).price == 5
                && tiers.get(2).limit == 15 && tiers.get(2).price == 10
                && tiers.get(3).limit == 20 && tiers.get(3).price == 15
                && tiers.get(4).limit == 25 && tiers.get(4).price == 25;
        if (oldBuiltInSchedule || oldFiveStepSchedule) {
            tiers = new ArrayList<>(List.of(
                    new Tier(5, 1),
                    new Tier(10, 5),
                    new Tier(15, 10),
                    new Tier(20, 15),
                    new Tier(24, 20),
                    new Tier(25, 25)
            ));
        }
        if (materials.isEmpty()) {
            for (String id : currentMaterials) materials.put(id, new MaterialConfig(true, 1.0));
        }
        currentMaterials.removeIf(id -> id == null || Identifier.tryParse(id) == null);
        while (currentMaterials.size() > 4) currentMaterials.remove(currentMaterials.size() - 1);
        normalizeCurrentMaterialRoles();
    }

    private void normalizeCurrentMaterialRoles() {
        if (currentMaterials.size() != 4) return;

        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < 3 && i < currentMaterials.size(); i++) {
            String id = currentMaterials.get(i);
            if (standardMaterialIds.contains(id) && validEnabled(id) && !normalized.contains(id)) normalized.add(id);
        }
        for (String id : standardMaterialIds) {
            if (normalized.size() >= 3) break;
            if (validEnabled(id) && !normalized.contains(id)) normalized.add(id);
        }
        if (normalized.size() != 3) return;

        String fourth = currentMaterials.get(3);
        if (!specialMaterialIds.contains(fourth) || !validEnabled(fourth) || normalized.contains(fourth)) {
            fourth = firstEnabledValid(specialMaterialIds);
        }
        if (fourth == null) return;

        normalized.add(fourth);
        currentMaterials = normalized;
    }

    private String firstEnabledValid(List<String> ids) {
        for (String id : ids) {
            if (Identifier.tryParse(id) != null && isEnabled(id)) return id;
        }
        return null;
    }

    private boolean isEnabled(String id) {
        MaterialConfig c = materials.get(id);
        return c == null || c.enabled;
    }

    public List<String> enabledMaterialIds() {
        return materials.entrySet().stream().filter(e -> e.getValue() != null && e.getValue().enabled && Identifier.tryParse(e.getKey()) != null)
                .map(Map.Entry::getKey).toList();
    }

    public List<String> enabledStandardMaterialIds() {
        return standardMaterialIds.stream().filter(this::validEnabled).distinct().toList();
    }

    public List<String> enabledSpecialMaterialIds() {
        return specialMaterialIds.stream().filter(this::validEnabled).distinct().toList();
    }

    public boolean isSpecialMaterial(String id) {
        return id != null && specialMaterialIds.contains(id);
    }

    private boolean validEnabled(String id) {
        if (Identifier.tryParse(id) == null) return false;
        MaterialConfig c = materials.get(id);
        return c == null || c.enabled;
    }

    public int priceFor(int earned) {
        for (Tier tier : tiers) if (earned < tier.limit) return tier.price;
        return tiers.get(tiers.size() - 1).price;
    }

    public int tierFor(int earned) {
        for (int i = 0; i < tiers.size(); i++) if (earned < tiers.get(i).limit) return i;
        return Math.max(0, tiers.size() - 1);
    }

    public int tierLimit(int tier) {
        if (tiers.isEmpty()) return weeklyLimit;
        if (tier < 0) return tiers.get(0).limit;
        return tiers.get(Math.min(tier, tiers.size() - 1)).limit;
    }
}
