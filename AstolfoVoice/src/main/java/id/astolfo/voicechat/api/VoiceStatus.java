package id.astolfo.voicechat.api;

import java.util.UUID;

/**
 * Status voice player (read-only snapshot).
 */
public interface VoiceStatus {

    UUID getUuid();

    String getName();

    boolean isConnected();      // terhubung UDP voice

    boolean isDisabled();       // client mematikan voice

    boolean isMuted();          // server-side muted

    boolean isWhispering();     // sedang bisik (estimasi)

    double getRange();          // range efektif saat ini

    UUID getGroup();            // grup aktif (null jika tidak ada)

    String getWorld();          // nama world
}
