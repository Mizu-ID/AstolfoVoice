package id.astolfo.voicechat.api;

import java.util.List;
import java.util.UUID;

/**
 * Private channel — anggota saling mendengar tanpa proximity broadcast global.
 */
public interface PrivateChannel {

    UUID getId();

    List<UUID> getMembers();

    void addMember(UUID player);

    void removeMember(UUID player);

    PrivateChannelOptions getOptions();
}
