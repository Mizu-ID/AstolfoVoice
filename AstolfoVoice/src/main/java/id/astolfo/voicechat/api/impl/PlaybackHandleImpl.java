package id.astolfo.voicechat.api.impl;

import id.astolfo.voicechat.api.PlaybackHandle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PlaybackHandle stub. Streaming engine (Fase 2) akan menjalankan audio nyata;
 * sekarang menandai state agar API tetap konsisten.
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

    public void markStopped() {
        running.set(false);
    }
}
