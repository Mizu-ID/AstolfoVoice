package id.astolfo.voicechat.api;

/**
 * Opsi playback (volume, distance, category, loop).
 */
public final class PlaybackOptions {

    private double volume = 1.0;
    private double distance = 32.0;
    private String category;
    private boolean loop;

    public PlaybackOptions() {
    }

    public PlaybackOptions(double volume, double distance, boolean loop) {
        this.volume = volume;
        this.distance = distance;
        this.loop = loop;
    }

    public double getVolume() {
        return volume;
    }

    public PlaybackOptions setVolume(double volume) {
        this.volume = volume;
        return this;
    }

    public double getDistance() {
        return distance;
    }

    public PlaybackOptions setDistance(double distance) {
        this.distance = distance;
        return this;
    }

    public String getCategory() {
        return category;
    }

    public PlaybackOptions setCategory(String category) {
        this.category = category;
        return this;
    }

    public boolean isLoop() {
        return loop;
    }

    public PlaybackOptions setLoop(boolean loop) {
        this.loop = loop;
        return this;
    }
}
