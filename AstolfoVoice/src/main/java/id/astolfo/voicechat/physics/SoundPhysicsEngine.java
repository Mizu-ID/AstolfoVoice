package id.astolfo.voicechat.physics;

import id.astolfo.voicechat.config.AstolfoConfig;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SoundPhysicsEngine - simulasi gelombang suara realistis (bukan gate keras).
 *
 * Model fisika (v0.3, disempurnakan):
 *  - DIRECT path: line-of-sight (fluid NEVER di sini; medium ditangani terpisah).
 *  - DIFFRACTION: suara membungkung di tepi obstacle (Fresnel-ish probe multi-offset).
 *    Tiang 1-block tetap kedengar (leak besar), dinding 2-wide agak mendam, bukan bisu.
 *    Gain curve halus (1/(1+extra*k)) - bukan linear abrupt. Skala via
 *    sound_physics.diffraction_strength (0 = off, hemat CPU).
 *  - TRANSMISSION: suara tembus dinding tipis, redaman material-dependent
 *    (wool/leaves menyerap besar; glass redam sedang; stone/obsidian opaque penuh).
 *    Leak = e^(-absorption*thickness*strength) (hukum Beer-Lambert).
 *    Tebal >= max_occlusion_blocks = tidak tembus sama sekali (difraksi masih boleh).
 *  - MEDIUM: air/lava di eye/jalur -> lowpass dalam + absorption high-freq EKSPONENSIAL
 *    per meter (bukan per-block linier) - makin jauh di medium makin mendam.
 *    Lava jauh lebih mendam daripada air. Skala via medium_strength.
 *  - REVERB: density block solid sekitar listener (sparse sampling, murah) + probe
 *    atap ke atas -> ruang tertutup (bergema).
 *  - SMOOTHING: gain/lowpass/distance-bias di-lerp per pasangan sender>listener
 *    (sound_physics.smoothing_factor) biar tidak ada lompatan kasar saat player
 *    bergerak melewati tepi obstacle.
 *  - CACHE: hasil per pasangan di-cache selama sound_physics.cache_ticks tick
 *    (raytrace tidak diulang tiap paket 20ms).
 *
 * Output SoundPath: gain, lowpassHz, reverb, distanceBias, muffled, audible, clear.
 */
public final class SoundPhysicsEngine {

    /** Umur maksimum state smoothing sebelum snap ulang (player teleport dsb). */
    private static final long SMOOTH_STALE_MS = 2000L;
    /** Batas ukuran map sebelum sweep lazy entry basi. */
    private static final int SWEEP_THRESHOLD = 8192;

    private final AstolfoConfig config;
    private final ConcurrentHashMap<String, CachedPath> pathCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SmoothState> smoothStates = new ConcurrentHashMap<>();

    public SoundPhysicsEngine(AstolfoConfig config) {
        this.config = config;
    }

    public static final class SoundPath {
        public final float gain;
        public final float lowpassHz;
        public final float reverb;
        public final float distanceBias;
        public final boolean muffled;
        public final boolean audible;
        public final boolean clear;

        public SoundPath(float gain, float lowpassHz, float reverb, float distanceBias,
                         boolean muffled, boolean audible, boolean clear) {
            this.gain = gain;
            this.lowpassHz = lowpassHz;
            this.reverb = reverb;
            this.distanceBias = distanceBias;
            this.muffled = muffled;
            this.audible = audible;
            this.clear = clear;
        }
    }

    private static final class CachedPath {
        final SoundPath path;
        final long expireAt;
        CachedPath(SoundPath path, long expireAt) {
            this.path = path;
            this.expireAt = expireAt;
        }
    }

    private static final class SmoothState {
        float gain;
        float lowpass;
        float bias;
        long lastMs;
        SmoothState(float gain, float lowpass, float bias, long lastMs) {
            this.gain = gain;
            this.lowpass = lowpass;
            this.bias = bias;
            this.lastMs = lastMs;
        }
    }

    /** Versi tanpa smoothing/cache per-pasangan (pairKey null). */
    public SoundPath compute(Location from, Location to, double rawDistance, World world) {
        return compute(from, to, rawDistance, world, null);
    }

    /**
     * Hitung SoundPath sender->listener. pairKey (format "senderUuid>listenerUuid",
     * boleh null) mengaktifkan cache TTL + smoothing temporal per pasangan.
     */
    public SoundPath compute(Location from, Location to, double rawDistance, World world, String pairKey) {
        if (rawDistance < 1e-3) {
            return new SoundPath(1f, Float.MAX_VALUE, 0f, 0f, false, true, true);
        }
        if (!config.soundPhysicsEnabled() || !config.raytraceEnabled()) {
            return new SoundPath(1f, Float.MAX_VALUE, 0f, 0f, false, true, true);
        }

        long now = System.currentTimeMillis();
        if (pairKey != null) {
            CachedPath cached = pathCache.get(pairKey);
            if (cached != null && cached.expireAt > now) {
                return cached.path;
            }
        }

        SoundPath raw = computeRaw(from, to, rawDistance, world);
        SoundPath result = pairKey == null ? raw : smooth(pairKey, raw, now);

        if (pairKey != null) {
            long ttl = Math.max(1, config.cacheTicks()) * 50L;
            pathCache.put(pairKey, new CachedPath(result, now + ttl));
            sweepIfNeeded(now);
        }
        return result;
    }

    private SoundPath computeRaw(Location from, Location to, double rawDistance, World world) {
        Vector start = from.toVector();
        Vector end = to.toVector();
        Vector dir = end.clone().subtract(start);
        double maxDist = dir.length();
        dir.multiply(1.0 / maxDist);

        // 1) DIRECT (air diabaikan di sini; medium probe terpisah agar tiang+air tetap LOS jelas).
        RayTraceResult direct = safeRayTrace(world, from, dir, maxDist, FluidCollisionMode.NEVER);
        boolean directClear = direct == null || direct.getHitBlock() == null;

        // 2) TRANSMISSION: tebal + material solid sepanjang direct ray.
        Transmission tr = measureTransmission(start, dir, maxDist, world);

        // 3) DIFFRACTION: jalur edge sekitar obstacle bila direct tertutup.
        double diffStrength = clampD(config.diffractionStrength(), 0.0, 2.0);
        float diffractionGain = 0f;
        float diffractionBias = 0f;
        if (!directClear && diffStrength > 0) {
            DiffractionResult dif = probeDiffraction(from, to, dir, maxDist, world, direct);
            diffractionGain = (float) Math.min(1.0, dif.gain * diffStrength);
            diffractionBias = dif.distanceBias;
        }

        // 4) MEDIUM: air/lava (per meter, eksponensial).
        MediumResult medium = probeMedium(from, to, start, dir, maxDist, world);
        double medStrength = clampD(config.mediumStrength(), 0.0, 2.0);

        // 5) REVERB: density sekitar listener (sparse) + probe atap.
        ReverbResult rev = probeReverb(to, world);

        // ---- Akumulasi gain ----
        float directGain = directClear ? 1f : 0f;
        // transmission leak: e^(-absorption * thickness * strength) (Beer-Lambert).
        double trStrength = clampD(config.transmissionStrength(), 0.0, 2.0);
        float transmitGain;
        if (tr.thickness <= 0) {
            transmitGain = 0f;
        } else if (tr.thickness >= config.maxOcclusionBlocks()) {
            // Dinding terlalu tebal: tidak ada transmisi sama sekali (difraksi masih mungkin).
            transmitGain = 0f;
        } else {
            transmitGain = (float) Math.exp(-tr.absorption * tr.thickness * trStrength);
        }
        // Gain akhir = jalur terkuat (direct > difraksi > transmisi).
        float gain = Math.max(directGain, Math.max(transmitGain, diffractionGain));

        // ---- Lowpass ----
        float lowpass = Float.MAX_VALUE;
        // Air: lowpass dalam + makin jauh makin rendah (absorption high-freq eksponensial per meter).
        if (medium.waterMeters > 0 && medStrength > 0) {
            // 700 Hz baseline di air, turun ~exp per meter (air menyerap high-freq kuat).
            float waterCut = (float) (700f * Math.exp(-medium.waterMeters * 0.35 * medStrength));
            lowpass = Math.min(lowpass, Math.max(180f, waterCut));
        }
        // Lava: jauh lebih rapat dari air -> baseline rendah + absorption lebih cepat.
        if (medium.lavaMeters > 0 && medStrength > 0) {
            float lavaCut = (float) (400f * Math.exp(-medium.lavaMeters * 0.5 * medStrength));
            lowpass = Math.min(lowpass, Math.max(120f, lavaCut));
        }
        if (tr.thickness >= 1 && !directClear && trStrength > 0) {
            // dinding: cutoff turun dengan tebal + material (wool/leaves lebih rendah).
            float base = tr.maxOpaque ? 350f : 2200f;
            float wallCut = Math.max(base, 2200f - tr.thickness * (tr.softMaterial ? 700f : 500f));
            lowpass = Math.min(lowpass, wallCut);
        }
        if (diffractionGain > 0f && diffractionGain < directGain) {
            // difraksi melemahkan high-freq sedikit (edge scattering).
            lowpass = Math.min(lowpass, 2600f);
        }
        // Distance air absorption: high-freq hilang di udara juga, per meter (efek halus > 32 block).
        double airAbsorb = rawDistance * 0.3;
        if (airAbsorb > 0 && rawDistance > 32) {
            lowpass = Math.min(lowpass, (float) (16000f - airAbsorb * 40f));
            if (lowpass < 4000f) lowpass = 4000f;
        }

        // ---- Distance bias (halus) ----
        float distanceBias = 0f;
        if (!directClear) {
            distanceBias += tr.thickness * (float) config.occlusionPenaltyPerBlock() * 0.7f;
            distanceBias += diffractionBias;
        }
        if (rev.enclosed) {
            distanceBias += (float) config.reverbPenalty() * rev.density;
        }
        if (medium.waterMeters > 0) {
            // air memperpanjang jarak efektif (absorb energi) - tambahan halus per meter.
            distanceBias += (float) (medium.waterMeters * 1.2 * medStrength);
        }
        if (medium.lavaMeters > 0) {
            distanceBias += (float) (medium.lavaMeters * 2.5 * medStrength);
        }

        // ---- Flags ----
        boolean muffled = lowpass < 2400f || (!directClear && gain < 0.9f);
        boolean clear = directClear && medium.waterMeters == 0 && medium.lavaMeters == 0 && tr.thickness == 0;
        float audibilityThreshold = 0.05f;
        // Audible bila gain cukup DAN dalam jangkauan efektif (range + bias + toleransi).
        double effectiveReach = config.shoutRange() * 1.5 + distanceBias + config.voiceRange();
        boolean audible = gain >= audibilityThreshold && rawDistance <= effectiveReach;

        return new SoundPath(gain, lowpass, rev.enclosed ? rev.density : 0f, distanceBias, muffled, audible, clear);
    }

    // ---------- Smoothing temporal per pasangan ----------
    private SoundPath smooth(String pairKey, SoundPath target, long now) {
        double s = clampD(config.smoothingFactor(), 0.05, 1.0);
        if (s >= 1.0) return target;
        SmoothState st = smoothStates.get(pairKey);
        if (st == null || now - st.lastMs > SMOOTH_STALE_MS) {
            // pasangan baru / basi (teleport, respawn): snap langsung ke target.
            smoothStates.put(pairKey, new SmoothState(target.gain, boundedLowpass(target.lowpassHz),
                    target.distanceBias, now));
            return target;
        }
        float f = (float) s;
        st.gain += (target.gain - st.gain) * f;
        st.lowpass += (boundedLowpass(target.lowpassHz) - st.lowpass) * f;
        st.bias += (target.distanceBias - st.bias) * f;
        st.lastMs = now;
        // Lowpass mendekati nyquist = dianggap tanpa filter lagi.
        float lp = st.lowpass >= 23000f ? Float.MAX_VALUE : st.lowpass;
        boolean muffled = lp < 2400f || (target.muffled && st.gain < 0.9f);
        return new SoundPath(st.gain, lp, target.reverb, st.bias, muffled, target.audible, target.clear);
    }

    /** Float.MAX_VALUE tidak bisa di-lerp; wakili "tanpa filter" sebagai 24 kHz. */
    private static float boundedLowpass(float hz) {
        return Math.min(hz, 24000f);
    }

    private void sweepIfNeeded(long now) {
        if (pathCache.size() > SWEEP_THRESHOLD) {
            pathCache.entrySet().removeIf(e -> e.getValue().expireAt <= now);
        }
        if (smoothStates.size() > SWEEP_THRESHOLD) {
            smoothStates.entrySet().removeIf(e -> now - e.getValue().lastMs > SMOOTH_STALE_MS * 5);
        }
    }

    /** Buang cache + state smoothing milik player (dipanggil saat quit/disconnect). */
    public void clearFor(UUID player) {
        String prefix = player + ">";
        String suffix = ">" + player;
        pathCache.keySet().removeIf(k -> k.startsWith(prefix) || k.endsWith(suffix));
        smoothStates.keySet().removeIf(k -> k.startsWith(prefix) || k.endsWith(suffix));
    }

    private RayTraceResult safeRayTrace(World world, Location from, Vector dir, double maxDist, FluidCollisionMode fluid) {
        try {
            return world.rayTraceBlocks(from, dir, maxDist, fluid, true);
        } catch (Throwable t) {
            // raytrace boleh lempar saat world unload/dll -> anggap tertutup (aman).
            return null;
        }
    }

    // ---------- Transmission ----------
    private static final class Transmission {
        final int thickness;        // block solid distinct
        final float absorption;     // per-block absorption (material avg)
        final boolean softMaterial; // wool/leaves/snow -> lowpass lebih agresif
        final boolean maxOpaque;    // stone/obsidian -> opaque penuh
        Transmission(int thickness, float absorption, boolean softMaterial, boolean maxOpaque) {
            this.thickness = thickness;
            this.absorption = absorption;
            this.softMaterial = softMaterial;
            this.maxOpaque = maxOpaque;
        }
    }

    private Transmission measureTransmission(Vector start, Vector dir, double maxDist, World world) {
        int thickness = 0;
        float absorptionSum = 0f;
        boolean soft = false;
        boolean opaque = false;
        double step = 0.5;
        int prevBx = Integer.MIN_VALUE, prevBy = 0, prevBz = 0;
        for (double t = 0; t < maxDist; t += step) {
            double x = start.getX() + dir.getX() * t;
            double y = start.getY() + dir.getY() * t;
            double z = start.getZ() + dir.getZ() * t;
            int bx = floor(x), by = floor(y), bz = floor(z);
            if (bx == prevBx && by == prevBy && bz == prevBz) continue;
            prevBx = bx; prevBy = by; prevBz = bz;
            Block b = world.getBlockAt(bx, by, bz);
            Material m = b.getType();
            if (!isBlocking(m)) continue;
            thickness++;
            MatAcoustics ma = acoustics(m);
            absorptionSum += ma.absorption;
            if (ma.soft) soft = true;
            if (ma.opaque) opaque = true;
        }
        float absorption = thickness == 0 ? 0f : absorptionSum / thickness;
        return new Transmission(thickness, absorption, soft, opaque);
    }

    private static final class MatAcoustics {
        final float absorption; // per-block (0 = tembus, tinggi = redam besar)
        final boolean soft;     // wool/leaves -> lowpass agresif
        final boolean opaque;   // stone/obsidian -> opaque
        MatAcoustics(float absorption, boolean soft, boolean opaque) {
            this.absorption = absorption;
            this.soft = soft;
            this.opaque = opaque;
        }
    }

    private static MatAcoustics acoustics(Material m) {
        String n = m.name();
        // wool & carpet: sangat menyerap
        if (n.contains("WOOL") || n.contains("CARPET")) return new MatAcoustics(2.2f, true, false);
        // leaves, snow, hay, moss, sponge: menyerap sedang
        if (n.contains("LEAVES") || n.contains("SNOW") || n.contains("HAY") || n.contains("MOSS") || n.contains("SPONGE"))
            return new MatAcoustics(1.6f, true, false);
        // wood/planks/logs/stems/fences: redam sedang, tidak opaque
        if (n.contains("PLANKS") || n.contains("LOG") || n.contains("WOOD") || n.contains("STEM") || n.contains("FENCE"))
            return new MatAcoustics(0.9f, false, false);
        // glass: redam kecil, tembus pandang tapi blok suara sebagian
        if (n.contains("GLASS")) return new MatAcoustics(0.4f, false, false);
        // dirt/grass/sand/gravel/podzol: redam sedang
        if (n.contains("DIRT") || n.contains("GRASS") || n.contains("SAND") || n.contains("GRAVEL") || n.contains("PODZOL"))
            return new MatAcoustics(1.0f, false, false);
        // stone/obsidian/bedrock/iron/deepslate: opaque penuh, redam besar
        if (n.contains("STONE") || n.contains("OBSIDIAN") || n.contains("BEDROCK") || n.contains("IRON") || n.contains("DEEPSLATE"))
            return new MatAcoustics(1.8f, false, true);
        // default solid
        return new MatAcoustics(1.0f, false, false);
    }

    // ---------- Diffraction ----------
    private static final class DiffractionResult {
        final float gain;
        final float distanceBias;
        DiffractionResult(float gain, float distanceBias) {
            this.gain = gain;
            this.distanceBias = distanceBias;
        }
    }

    private DiffractionResult probeDiffraction(Location from, Location to, Vector dir, double maxDist,
                                               World world, RayTraceResult directHit) {
        Vector up = new Vector(0, 1, 0);
        Vector perp = dir.clone().getCrossProduct(up);
        if (perp.lengthSquared() < 1e-6) perp = new Vector(1, 0, 0);
        perp.normalize();
        Vector vertPerp = dir.clone().getCrossProduct(perp).normalize();

        float bestGain = 0f;
        float bestBias = 0f;
        double hitT = (directHit != null && directHit.getHitPosition() != null)
                ? from.toVector().distance(directHit.getHitPosition()) : maxDist / 2.0;

        // Probe beberapa offset edge (makin jauh offset, makin mahal jalur = gain turun).
        double[] offsets = {0.6, 1.1, 1.7, 2.4, 3.2};
        for (double off : offsets) {
            for (int s = -1; s <= 1; s += 2) {
                Vector viaH = from.toVector().clone().add(dir.clone().multiply(hitT)).add(perp.clone().multiply(off * s));
                Vector viaV = from.toVector().clone().add(dir.clone().multiply(hitT)).add(vertPerp.clone().multiply(off * s));
                float g = pathGain(from, viaH, to, world);
                float gV = pathGain(from, viaV, to, world);
                if (g > bestGain) { bestGain = g; bestBias = (float) (off * 1.5); }
                if (gV > bestGain) { bestGain = gV; bestBias = (float) (off * 1.5); }
            }
            // Cukup bila sudah ketemu jalur cukup jernih.
            if (bestGain > 0.7f) break;
        }
        return new DiffractionResult(bestGain, bestBias);
    }

    private float pathGain(Location from, Vector via, Location to, World world) {
        Location viaLoc = new Location(world, via.getX(), via.getY(), via.getZ());
        Vector d1 = via.clone().subtract(from.toVector());
        double len1 = d1.length();
        if (len1 < 1e-3) return 0f;
        d1.multiply(1.0 / len1);
        RayTraceResult r1 = safeRayTrace(world, from, d1, len1, FluidCollisionMode.NEVER);
        if (r1 != null && r1.getHitBlock() != null) return 0f;
        Vector d2 = to.toVector().clone().subtract(via);
        double len2 = d2.length();
        if (len2 < 1e-3) return 0f;
        d2.multiply(1.0 / len2);
        RayTraceResult r2 = safeRayTrace(world, viaLoc, d2, len2, FluidCollisionMode.NEVER);
        if (r2 != null && r2.getHitBlock() != null) return 0f;
        double extra = (len1 + len2) - from.distance(to);
        // Gain curve halus: 1/(1 + extra*k). extra = jarak ekstra jalur bengkok.
        return (float) (1.0 / (1.0 + extra * 0.08));
    }

    // ---------- Medium (air/lava) - per meter, eksponensial ----------
    private static final class MediumResult {
        final double waterMeters; // panjang jalur di air (meter)
        final double lavaMeters;  // panjang jalur di lava (meter)
        MediumResult(double waterMeters, double lavaMeters) {
            this.waterMeters = waterMeters;
            this.lavaMeters = lavaMeters;
        }
    }

    private MediumResult probeMedium(Location from, Location to, Vector start, Vector dir, double maxDist, World world) {
        double waterLen = 0;
        double lavaLen = 0;
        // Endpoint di dalam medium dihitung minimal 1 meter (kepala terendam).
        Material fromM = world.getBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ()).getType();
        Material toM = world.getBlockAt(to.getBlockX(), to.getBlockY(), to.getBlockZ()).getType();
        if (isWater(fromM) || isWater(toM)) waterLen = 1;
        if (isLava(fromM) || isLava(toM)) lavaLen = 1;
        double step = 1.0;
        for (double t = 0; t < maxDist; t += step) {
            double x = start.getX() + dir.getX() * t;
            double y = start.getY() + dir.getY() * t;
            double z = start.getZ() + dir.getZ() * t;
            Material m = world.getBlockAt(floor(x), floor(y), floor(z)).getType();
            if (isWater(m)) waterLen += step;      // akumulasi meter air (= block).
            else if (isLava(m)) lavaLen += step;
        }
        return new MediumResult(waterLen, lavaLen);
    }

    // ---------- Reverb ----------
    private static final class ReverbResult {
        final boolean enclosed;
        final float density;
        ReverbResult(boolean enclosed, float density) {
            this.enclosed = enclosed;
            this.density = density;
        }
    }

    private ReverbResult probeReverb(Location at, World world) {
        int r = config.reverbProbeRadius();
        if (r <= 0) return new ReverbResult(false, 0f);
        int lx = at.getBlockX(), ly = at.getBlockY(), lz = at.getBlockZ();
        // Sparse sampling (step 2): akurasi density hampir sama, biaya ~1/8.
        int solid = 0, total = 0;
        for (int x = -r; x <= r; x += 2) {
            for (int y = -r; y <= r; y += 2) {
                for (int z = -r; z <= r; z += 2) {
                    total++;
                    if (isBlocking(world.getBlockAt(lx + x, ly + y, lz + z).getType())) solid++;
                }
            }
        }
        float density = total == 0 ? 0f : (float) solid / total;
        // enclosed bila density cukup & ADA atap di atas listener dalam 2r block
        // (probe kolom, bukan satu titik - lebih akurat untuk langit-langit tinggi).
        boolean ceiling = false;
        int maxUp = Math.max(r * 2, 4);
        for (int dy = 1; dy <= maxUp; dy++) {
            if (isBlocking(world.getBlockAt(lx, ly + dy, lz).getType())) {
                ceiling = true;
                break;
            }
        }
        return new ReverbResult(density > 0.45f && ceiling, density);
    }

    // ---------- Helpers ----------
    private static boolean isBlocking(Material m) {
        if (m == null || m.isAir()) return false;
        if (m.isOccluding() && m.isSolid()) return true;
        String n = m.name();
        if (n.contains("GLASS") || n.contains("GLASS_PANE")) return true;
        if (n.equals("BARRIER")) return true;
        if (n.contains("LEAVES")) return true;
        if (n.contains("FENCE") || n.contains("WALL")) return true;
        return false;
    }

    private static boolean isWater(Material m) {
        return m == Material.WATER || m == Material.BUBBLE_COLUMN
                || m == Material.KELP || m == Material.KELP_PLANT
                || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
    }

    private static boolean isLava(Material m) {
        return m == Material.LAVA;
    }

    private static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
