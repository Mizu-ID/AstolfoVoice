package id.astolfo.voicechat.api.impl;

import id.astolfo.voicechat.api.PlaybackHandle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PlaybackHandle implementation. Tracks the running state of a streaming
 * playback started by StreamingAudioPlayer. stop() flips the flag so the
 * streaming virtual thread exits its frame loop on the next iteration.
 */
public final class PlaybackHandleImpl implements PlaybackHandle {

    private final String file;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlaybackHandleImpl(String file) {
        this.file = file;
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getFile() {
        return file;
    }

    /** Called by the streaming thread when playback reaches the final frame. */
    public void markStopped() {
        running.set(false);
    }
}
