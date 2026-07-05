package id.astolfo.voicechat.api;

/**
 * Opsi private channel (skenario user).
 *
 * - audibleNearby=false, range=-1: hanya anggota saling dengar (jarak tak terbatas antar anggota),
 *   luar channel tidak dengar.
 * - audibleNearby=true, range=R: anggota + orang dalam R block dengar.
 */
public final class PrivateChannelOptions {

    private boolean audibleNearby = false;
    private double range = -1; // -1 = tak terbatas antar anggota
    private boolean includeOutsidersWithinRange = false;

    public PrivateChannelOptions() {
    }

    public PrivateChannelOptions(boolean audibleNearby, double range) {
        this.audibleNearby = audibleNearby;
        this.range = range;
    }

    public boolean isAudibleNearby() {
        return audibleNearby;
    }

    public PrivateChannelOptions setAudibleNearby(boolean audibleNearby) {
        this.audibleNearby = audibleNearby;
        return this;
    }

    public double getRange() {
        return range;
    }

    public PrivateChannelOptions setRange(double range) {
        this.range = range;
        return this;
    }

    public boolean isIncludeOutsidersWithinRange() {
        return includeOutsidersWithinRange;
    }

    public PrivateChannelOptions setIncludeOutsidersWithinRange(boolean includeOutsidersWithinRange) {
        this.includeOutsidersWithinRange = includeOutsidersWithinRange;
        return this;
    }
}
