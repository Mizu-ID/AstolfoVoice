package id.astolfo.voicechat.api.impl;

import id.astolfo.voicechat.api.VoiceStatus;

import java.util.UUID;

/**
 * VoiceStatus snapshot.
 */
public final class VoiceStatusImpl implements VoiceStatus {

    private final UUID uuid;
    private final String name;
    private final boolean connected;
    private final boolean disabled;
    private final boolean muted;
    private final boolean whispering;
    private final double range;
    private final UUID group;
    private final String world;

    public VoiceStatusImpl(UUID uuid, String name, boolean connected, boolean disabled, boolean muted,
                           boolean whispering, double range, UUID group, String world) {
        this.uuid = uuid;
        this.name = name;
        this.connected = connected;
        this.disabled = disabled;
        this.muted = muted;
        this.whispering = whispering;
        this.range = range;
        this.group = group;
        this.world = world;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public boolean isMuted() {
        return muted;
    }

    @Override
    public boolean isWhispering() {
        return whispering;
    }

    @Override
    public double getRange() {
        return range;
    }

    @Override
    public UUID getGroup() {
        return group;
    }

    @Override
    public String getWorld() {
        return world;
    }
}
