package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import org.bukkit.entity.Player;

/**
 * Kontrak paket plugin-channel (TCP via Bukkit Messenger). Byte-exact SVC.
 * Channel = getID().toString() ("voicechat:<name>").
 */
public interface Packet {

    Key getID();

    void toBytes(FriendlyByteBuf buf);

    /** Default no-op; incoming packet menangani logika di onPacket. */
    default void onPacket(Player player) {
    }
}
