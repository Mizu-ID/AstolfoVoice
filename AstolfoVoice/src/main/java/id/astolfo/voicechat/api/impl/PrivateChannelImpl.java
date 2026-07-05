package id.astolfo.voicechat.api.impl;

import id.astolfo.voicechat.api.PrivateChannel;
import id.astolfo.voicechat.api.PrivateChannelOptions;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PrivateChannel impl. Anggota saling mendengar (jarak tak terbatas antar anggota).
 * Implementasi penuh routing audio di-hook saat audio engine (Fase 2) aktif.
 */
public final class PrivateChannelImpl implements PrivateChannel {

    private final UUID id;
    private final ConcurrentHashMap.KeySetView<UUID, Boolean> members;
    private final PrivateChannelOptions options;

    public PrivateChannelImpl(List<UUID> members, PrivateChannelOptions options) {
        this.id = UUID.randomUUID();
        this.members = ConcurrentHashMap.newKeySet();
        this.members.addAll(members);
        this.options = options;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public List<UUID> getMembers() {
        return List.copyOf(members);
    }

    @Override
    public void addMember(UUID player) {
        members.add(player);
    }

    @Override
    public void removeMember(UUID player) {
        members.remove(player);
    }

    @Override
    public PrivateChannelOptions getOptions() {
        return options;
    }

    public boolean hasMember(UUID player) {
        return members.contains(player);
    }
}
