package id.astolfo.voicechat.physics;

import id.astolfo.voicechat.config.AstolfoConfig;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * SoundPhysicsEngine — simulasi gelombang suara realistis (bukan gate keras).
 *
 * Model fisika:
 *  - DIRECT path: line-of-sight. Kalau jelas → transfer penuh (jernih).
 *  - DIFFRACTION: suara membungkuk di tepi obstacle. Untuk tiang 1-block tidak
 *    sepenuhnya memblok — kita probe edge sekitar obstacle dan hitung "leak"
 *    berbasis geometri edge-diffraction (Fresnel-ish). Tiang = bocor besar,
 *    dinding 2-wide = bocor kecil tapi masih kedengar (agak mendam).
 *  - TRANSMISSION: suara tembus dinding tipis dengan redaman material-dependent.
 *  - MEDIUM: air → lowpass (mendam dalam) + jarak efek lebih jauh (cepat rambut
 *    suara di air ~4.3x udara, tapi absorbansi tinggi → mendam dalam).
 *  - REVERB: ruang tertutup → density probe sekitar listener + sender.
 *
 * Output: SoundPath (gain, muffleHz, reverb, distanceBias, whisperFlag, audible).
 * Dipakai ProximityResolver untuk modulasi distance + category + (Tier2) DSP bake.
 */
public final class SoundPhysicsEngine {

    private final AstolfoConfig config;

    public SoundPhysicsEngine(AstolfoConfig config) {
        this.config = config;
    }

    /** Hasil transfer suara sender→listener. */
    public static final class SoundPath {
        /** Gain linear 0..1 sebelum distance attenuation (akumulasi semua jalur). */
        public final float gain;
        /** Cutoff lowpass Hz untuk muffle (Float.MAX = no filter / jernih). */
        public final float lowpassHz;
        /** Reverb amount 0..1 (enclosed). */
        public final float reverb;
        /** Penambahan jarak efektif (block) — simulasi redaman jalur. */
        public final float distanceBias;
        /** True bila suara mendap/mendam dominan. */
        public final boolean muffled;
        /** True bila terdengar sama sekali (di atas ambang). */
        public final boolean audible;
        /** True bila direct LOS jelas. */
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

    /**
     * Hitung transfer suara dari sender ke listener.
     * @param rawDistance jarak Euclidean eye-to-eye (block)
     */
    public SoundPath compute(Location from, Location to, double rawDistance, World world) {
        if (rawDistance < 1e-3) {
            return new SoundPath(1f, Float.MAX_VALUE, 0f, 0f, false, true, true);
        }
        if (!config.soundPhysicsEnabled() || !config.raytraceEnabled()) {
            return new SoundPath(1f, Float.MAX_VALUE, 0f, 0f, false, true, true);
        }

        Vector start = from.toVector();
        Vector end = to.toVector();
        Vector dir = end.clone().subtract(start);
        double maxDist = dir.length();
        dir.multiply(1.0 / maxDist);

        // 1) DIRECT: raytrace LOS (fluid NEVER agar air tidak blok direct di sini;
        //    air ditangani terpisah sebagai medium).
        RayTraceResult direct = world.rayTraceBlocks(from, dir, maxDist, FluidCollisionMode.NEVER, true);
        boolean directClear = direct == null || direct.getHitBlock() == null;

        // 2) TRANSMISSION: sepanjang direct ray, hitung tebal material solid yang ditembus.
        Transmission tr = measureTransmission(start, dir, maxDist, world);

        // 3) DIFFRACTION: kalau direct tertutup, cari jalur edge sekitar obstacle.
        //    Bungkungan suara ≈ √(2·d·λ) geos; kita aproksimasi dengan probe ray
        //    offset tegak lurus direct ray di sekitar titik hit.
        float diffractionGain = 0f;
        float diffractionDistanceBias = 0f;
        if (!directClear) {
            DiffractionResult dif = probeDiffraction(from, to, dir, maxDist, world, direct);
            diffractionGain = dif.gain;
            diffractionDistanceBias = dif.distanceBias;
        }

        // 4) MEDIUM: air di sekitar sender/listener atau sepanjang jalur → lowpass + mendam.
        MediumResult medium = probeMedium(from, to, start, dir, maxDist, world);

        // 5) REVERB: density block solid sekitar listener (ruang tertutup).
        ReverbResult rev = probeReverb(to, world);

        // ---- Akumulasi transfer ----
        float directGain = directClear ? 1f : 0f;
        // Transmission leak: dinding tipis (1-2 block) masih tembus dengan redaman.
        float transmitGain = tr.thickness <= 0 ? 0f
                : (float) Math.exp(-0.6 * tr.thickness); // e^-0.6 ≈ 0.55 per block
        // Total gain = max(direct, transmit, diffract) — jalur terkuat menang (energi aditif tapi ambil maks utk stabilitas).
        float gain = Math.max(directGain, Math.max(transmitGain, diffractionGain));

        // Muffle: air + transmission tebal + diffraksi → rendam frekuensi tinggi.
        float lowpass = Float.MAX_VALUE;
        if (medium.inWater) {
            lowpass = Math.min(lowpass, 800f);      // air: sangat mendam dalam
        }
        if (tr.thickness >= 1) {
            float wallCut = Math.max(400f, 2200f - tr.thickness * 500f);
            lowpass = Math.min(lowpass, wallCut);
        }
        if (diffractionGain > 0f && diffractionGain < directGain) {
            lowpass = Math.min(lowpass, 2600f);     // diffraksi redam tinggi sedikit
        }

        // Distance bias: redaman jalur tidak-line-of-sight → jarak efektif lebih jauh.
        float distanceBias = 0f;
        if (!directClear) {
            // transmission: tebal × penalti (tapi masih kedengar, tidak full mute)
            distanceBias += tr.thickness * (float) config.occlusionPenaltyPerBlock() * 0.7f;
            // diffraksi: jalan memutar → sedikit extra distance
            distanceBias += diffractionDistanceBias;
        }
        // reverb: ruang tertutup → sedikit bias + reverb amount
        if (rev.enclosed) {
            distanceBias += (float) config.reverbPenalty() * rev.density;
        }

        boolean muffled = lowpass < 2400f || (!directClear && gain < 0.9f);
        boolean clear = directClear && !medium.inWater && tr.thickness == 0;
        // Audibility: gain masih di atas ambang DAN tidak sepenuhnya tertutup rapat.
        // Tiang 1-block: transmission thickness mungkin 0 (ray nyangkut tiang) tapi
        // diffraksi gain tinggi → tetap audible. Dinding 2-wide rapat: gain kecil tapi >0.
        float audibilityThreshold = 0.06f;
        boolean audible = gain >= audibilityThreshold
                && rawDistance <= (config.shoutRange() * 1.5 + distanceBias + config.voiceRange());

        return new SoundPath(gain, lowpass, rev.enclosed ? rev.density : 0f, distanceBias, muffled, audible, clear);
    }

    // ---------- Transmission ----------
    private static final class Transmission {
        final int thickness;       // jumlah block solid sepanjang direct ray
        final boolean fullySealed; // true bila dinding rapat (no gap)
        Transmission(int thickness, boolean fullySealed) {
            this.thickness = thickness;
            this.fullySealed = fullySealed;
        }
    }

    private Transmission measureTransmission(Vector start, Vector dir, double maxDist, World world) {
        int thickness = 0;
        boolean inSolid = false;
        boolean anyGap = false;
        double step = 0.5;
        double traveled = 0;
        int maxBlocks = config.maxOcclusionBlocks() + 4;
        int prevBX = Integer.MIN_VALUE, prevBY = 0, prevBZ = 0;
        while (traveled < maxDist && thickness < maxBlocks) {
            double x = start.getX() + dir.getX() * traveled;
            double y = start.getY() + dir.getY() * traveled;
            double z = start.getZ() + dir.getZ() * traveled;
            int bx = floor(x), by = floor(y), bz = floor(z);
            if (bx != prevBX || by != prevBY || bz != prevBZ) {
                Block b = world.getBlockAt(bx, by, bz);
                Material m = b.getType();
                boolean solid = isBlocking(m);
                if (solid) {
                    if (!inSolid) { inSolid = true; }
                    // tiap block solid baru = +1 thickness
                    if (bx != prevBX || by != prevBY || bz != prevBZ) {
                        // hanya hitung saat masuk block baru yang solid dan beda dari sebelumnya
                    }
                    thickness = countDistinctSolid(world, start, dir, traveled, maxDist);
                    break;
                } else {
                    if (inSolid) { inSolid = false; anyGap = true; }
                }
                prevBX = bx; prevBY = by; prevBZ = bz;
            }
            traveled += step;
        }
        return new Transmission(thickness, !anyGap && thickness > 0);
    }

    /** Hitung block solid distinct sepanjang ray (tebal dinding). */
    private int countDistinctSolid(World world, Vector start, Vector dir, double fromT, double maxDist) {
        int count = 0;
        double step = 0.5;
        double t = 0;
        int prevBx = Integer.MIN_VALUE, prevBy = 0, prevBz = 0;
        while (t < maxDist) {
            double x = start.getX() + dir.getX() * t;
            double y = start.getY() + dir.getY() * t;
            double z = start.getZ() + dir.getZ() * t;
            int bx = floor(x), by = floor(y), bz = floor(z);
            if (bx != prevBx || by != prevBy || bz != prevBz) {
                Material m = world.getBlockAt(bx, by, bz).getType();
                if (isBlocking(m)) {
                    count++;
                }
                prevBx = bx; prevBy = by; prevBz = bz;
            }
            t += step;
        }
        return count;
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

    /**
     * Probe difraksi: coba ray offset tegak lurus direct ray pada beberapa offset.
     * Kalau ada ray offset yang LOS-nya jelas, suara membungkung lewat sana.
     * Tiang 1-block: offset 1 block sudah lolos → gain tinggi.
     * Dinding 2-wide rapat: perlu offset lebih besar, gain lebih kecil.
     */
    private DiffractionResult probeDiffraction(Location from, Location to, Vector dir, double maxDist,
                                               World world, RayTraceResult directHit) {
        // Vektor tegak lurus direct ray (di plane horizontal + vertikal).
        Vector up = new Vector(0, 1, 0);
        Vector perp = dir.clone().getCrossProduct(up);
        if (perp.lengthSquared() < 1e-6) {
            perp = new Vector(1, 0, 0);
        }
        perp.normalize();
        Vector vertPerp = dir.clone().getCrossProduct(perp).normalize();

        float bestGain = 0f;
        float bestBias = 0f;
        // Titik hit obstacle untuk fokus probe sekitarnya.
        double hitT = (directHit != null && directHit.getHitPosition() != null)
                ? from.toVector().distance(directHit.getHitPosition()) : maxDist / 2.0;

        // Offset probe: kombinasi horizontal + vertikal, naik bertahap.
        double[] offsets = {0.6, 1.1, 1.7, 2.4, 3.2};
        for (double off : offsets) {
            for (int s = -1; s <= 1; s += 2) {
                // jalur melengkung via titik offset di sekitar hit.
                Vector via = from.toVector().clone()
                        .add(dir.clone().multiply(hitT))
                        .add(perp.clone().multiply(off * s));
                // juga coba vertikal
                Vector viaV = from.toVector().clone()
                        .add(dir.clone().multiply(hitT))
                        .add(vertPerp.clone().multiply(off * s));
                float g = pathGain(from, via, to, world);
                float gV = pathGain(from, viaV, to, world);
                if (g > bestGain) {
                    bestGain = g;
                    // jarak memutar ≈ 2*off (extra path length)
                    bestBias = (float) (off * 1.5);
                }
                if (gV > bestGain) {
                    bestGain = gV;
                    bestBias = (float) (off * 1.5);
                }
            }
            if (bestGain > 0.7f) break; // sudah cukup jelas, stop probe lebih jauh
        }
        // Difraksi meredam frekuensi tinggi; gain sudah mencerminkan energy leak.
        return new DiffractionResult(bestGain, bestBias);
    }

    /** Gain jalur from→via→to: 1.0 jika kedua segmen LOS, 0 jika tertutup. */
    private float pathGain(Location from, Vector via, Location to, World world) {
        Location viaLoc = new Location(world, via.getX(), via.getY(), via.getZ());
        Vector d1 = via.clone().subtract(from.toVector());
        double len1 = d1.length();
        if (len1 < 1e-3) return 0f;
        d1.multiply(1.0 / len1);
        RayTraceResult r1 = world.rayTraceBlocks(from, d1, len1, FluidCollisionMode.NEVER, true);
        if (r1 != null && r1.getHitBlock() != null) return 0f;

        Vector d2 = to.toVector().clone().subtract(via);
        double len2 = d2.length();
        if (len2 < 1e-3) return 0f;
        d2.multiply(1.0 / len2);
        RayTraceResult r2 = world.rayTraceBlocks(viaLoc, d2, len2, FluidCollisionMode.NEVER, true);
        if (r2 != null && r2.getHitBlock() != null) return 0f;

        // gain turun sedikit oleh panjang jalur memutar
        double extra = (len1 + len2) - from.distance(to);
        return (float) Math.max(0.2, 1.0 - extra * 0.05);
    }

    // ---------- Medium (air) ----------
    private static final class MediumResult {
        final boolean inWater;
        MediumResult(boolean inWater) { this.inWater = inWater; }
    }

    private MediumResult probeMedium(Location from, Location to, Vector start, Vector dir, double maxDist, World world) {
        boolean inWater = false;
        // cek block eye sender & listener
        if (isWater(from.getWorld().getBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ()).getType())
                || isWater(to.getWorld().getBlockAt(to.getBlockX(), to.getBlockY(), to.getBlockZ()).getType())) {
            inWater = true;
        }
        // cek sepanjang jalur (sampling kasar)
        if (!inWater) {
            double step = 1.0;
            for (double t = 0; t < maxDist; t += step) {
                double x = start.getX() + dir.getX() * t;
                double y = start.getY() + dir.getY() * t;
                double z = start.getZ() + dir.getZ() * t;
                if (isWater(world.getBlockAt(floor(x), floor(y), floor(z)).getType())) {
                    inWater = true;
                    break;
                }
            }
        }
        return new MediumResult(inWater);
    }

    // ---------- Reverb ----------
    private static final class ReverbResult {
        final boolean enclosed;
        final float density; // 0..1
        ReverbResult(boolean enclosed, float density) {
            this.enclosed = enclosed;
            this.density = density;
        }
    }

    private ReverbResult probeReverb(Location at, World world) {
        int r = config.reverbProbeRadius();
        if (r <= 0) return new ReverbResult(false, 0f);
        int lx = at.getBlockX(), ly = at.getBlockY(), lz = at.getBlockZ();
        int solid = 0, total = 0;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    total++;
                    if (isBlocking(world.getBlockAt(lx + x, ly + y, lz + z).getType())) solid++;
                }
            }
        }
        float density = total == 0 ? 0f : (float) solid / total;
        // ruang tertutup = density tinggi di sekeliling (dinding/atk/langit)
        boolean enclosed = density > 0.45f;
        return new ReverbResult(enclosed, density);
    }

    // ---------- Helpers ----------
    private static boolean isBlocking(Material m) {
        if (m == null || m.isAir()) return false;
        // Material.isOccluding() + isSolid() mendeteksi dinding/solid; kaca/isSolid? kaca occluding=false
        // agar kaca tembus pandang tapi tetap blok suara sebagian, kita anggap solid-but-transparent juga blocking.
        if (m.isOccluding() && m.isSolid()) return true;
        // kaca, daun, barrier tetap blok suara sebagian
        String n = m.name();
        if (n.contains("GLASS") || n.contains("GLASS_PANE")) return true;
        if (n.equals("BARRIER") || n.equals("STRUCTURE_VOID")) return false;
        return false;
    }

    private static boolean isWater(Material m) {
        return m == Material.WATER || m == Material.BUBBLE_COLUMN
                || m == Material.KELP || m == Material.KELP_PLANT
                || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
