package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.api.PrivateChannelOptions;
import id.astolfo.voicechat.api.impl.PrivateChannelImpl;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PrivateChannelRegistry — shared holder channel privat, ditulis API, dibaca
 * ProximityResolver. Hindari coupling langsung antar modul.
 *
 * Routing: saat sender bicara, jika dia anggota channel privat, audio ke anggota
 * lain channel (jarak tak terbatas bila audibleNearby=false; bila true, anggota
 * + orang dalam 'range' block juga dengar via proximity).
 */
public final class PrivateChannelRegistry {

    // member → channel
    private static final ConcurrentHashMap<UUID, PrivateChannelImpl> MEMBER_CHANNEL = new ConcurrentHashMap<>();

    public static void register(PrivateChannelImpl channel) {
        if (channel == null) return;
        for (UUID m : channel.getMembers()) {
            MEMBER_CHANNEL.put(m, channel);
        }
    }

    public static void addMember(PrivateChannelImpl channel, UUID member) {
        if (channel == null) return;
        channel.addMember(member);
        MEMBER_CHANNEL.put(member, channel);
    }

    public static void removeMember(UUID member) {
        MEMBER_CHANNEL.remove(member);
    }

    public static void unregister(PrivateChannelImpl channel) {
        if (channel == null) return;
        for (UUID m : channel.getMembers()) {
            MEMBER_CHANNEL.remove(m, channel);
        }
    }

    public static PrivateChannelImpl channelOf(UUID member) {
        return MEMBER_CHANNEL.get(member);
    }

    public static PrivateChannelOptions optionsOf(UUID member) {
        PrivateChannelImpl ch = MEMBER_CHANNEL.get(member);
        return ch != null ? ch.getOptions() : null;
    }

    public static List<UUID> membersOf(UUID member) {
        PrivateChannelImpl ch = MEMBER_CHANNEL.get(member);
        return ch != null ? ch.getMembers() : null;
    }

    private PrivateChannelRegistry() {
    }
}
