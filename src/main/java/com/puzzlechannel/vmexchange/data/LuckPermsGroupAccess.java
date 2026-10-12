package com.puzzlechannel.vmexchange.data;

import net.luckperms.api.LuckPermsProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps LuckPerms API references isolated from client-side class loading.
 * LuckPerms needs to be installed on the server; clients do not need the mod.
 */
public final class LuckPermsGroupAccess {
    public static boolean isAvailable() {
        try {
            LuckPermsProvider.get();
            return true;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    public static boolean groupExists(String groupName) {
        try {
            return LuckPermsProvider.get().getGroupManager().getGroup(groupName) != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    public static boolean playerHasGroup(ServerPlayer player, String groupName) {
        try {
            var user = LuckPermsProvider.get().getUserManager().getUser(player.getUUID());
            if (user == null) return false;
            if (user.getPrimaryGroup().equalsIgnoreCase(groupName)) return true;
            return user.getInheritedGroups(user.getQueryOptions()).stream()
                    .anyMatch(group -> group.getName().equalsIgnoreCase(groupName));
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private LuckPermsGroupAccess() {}
}
