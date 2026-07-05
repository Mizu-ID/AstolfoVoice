package id.astolfo.voicechat.api;

/**
 * Handle playback aktif (untuk stop/track).
 */
public interface PlaybackHandle {

    void stop();

    boolean isRunning();

    String getFile();
}
