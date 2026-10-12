package com.puzzlechannel.vmexchange.util;

import net.minecraft.resources.Identifier;

public final class Ids {
    private Ids() {}
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("vmexchange", path); }
}
