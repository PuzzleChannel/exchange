package com.puzzlechannel.vmexchange.network;

public final class ClientState {
    private static volatile VmExchangeNetworking.SnapshotPayload snapshot = new VmExchangeNetworking.SnapshotPayload("", "", "", "", 0, 25, 1, 1);
    public static void set(VmExchangeNetworking.SnapshotPayload value) { snapshot = value; }
    public static VmExchangeNetworking.SnapshotPayload get() { return snapshot; }
    private ClientState() {}
}
