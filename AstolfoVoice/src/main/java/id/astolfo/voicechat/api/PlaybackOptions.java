package id.astolfo.voicechat.api;

/**
 * Opsi playback (volume, distance, category, loop, pitch, preset).
 *
 * Sound effect presets (v0.2.1):
 *   NONE    - flat (default)
 *   PHONE   - bandpass 300-3400 Hz, sedikit distortion (radio/telepon)
 *   MEGA    - megaphone: highpass + slight gain + mild clip
 *   CAVE    - strong reverb + lowpass (gema ruang besar)
 *   RADIO   - bandpass narrow + light noise gate
 *
 * pitch: 1.0 normal, <1 lower/slower, >1 higher/faster (rate-based shift).
 */
public final class PlaybackOptions {

    public enum Preset {
        NONE, PHONE, MEGA, CAVE, RADIO
    }

    private double volume = 1.0;
    private double distance = 32.0;
    private String category;
    private boolean loop;
    private double pitch = 1.0;
    private Preset preset = Preset.NONE;

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

    public double getPitch() {
        return pitch;
    }

    public PlaybackOptions setPitch(double pitch) {
        this.pitch = pitch <= 0 ? 1.0 : pitch;
        return this;
    }

    public Preset getPreset() {
        return preset;
    }

    public PlaybackOptions setPreset(Preset preset) {
        this.preset = preset == null ? Preset.NONE : preset;
        return this;
    }

    public PlaybackOptions setPreset(String name) {
        if (name == null) {
            this.preset = Preset.NONE;
            return this;
        }
        try {
            this.preset = Preset.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.preset = Preset.NONE;
        }
        return this;
    }
}
