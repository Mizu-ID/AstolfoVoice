package id.astolfo.voicechat.physics;

import id.astolfo.voicechat.config.AstolfoConfig;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * SoundPhysicsEngine — simulasi gelombang suara realistis (bukan gate keras).
 *
 * Model fisika:
 *  - DIRECT path: line-of-sight (fluid NEVER di sini; air ditangani medium).
 *  - DIFFRACTION: suara membungkung di tepi obstacle (Fresnel-ish probe).
 *    Tiang 1-block tetap kedengar (leak besar), dinding 2-wide agak mendam.
 *  - TRANSMISSION: suara tembus dinding tipis dengan redaman material-dependent
 *    (wool/leaves redam besar; glass redam sedang; stone/obsidian redam penuh).
 *  - MEDIUM: air di eye/jalur → lowpass dalam + absorption high-freq per jarak.
 *  - REVERB: density block solid sekitar listener + sender (ruang tertutup).
 *
 * Output SoundPath: gain, lowpassHz, reverb, distanceBias, muffled, audible, clear.
 */
public final class SoundPhysicsEngine {

    private final AstolfoConfig config;

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

        // 1) DIRECT (air diabaikan di sini; medium probe terpisah agar tiang+air tetap LOS jelas).
        RayTraceResult direct = safeRayTrace(world, from, dir, maxDist, FluidCollisionMode.NEVER);
        boolean directClear = direct == null || direct.getHitBlock() == null;

        // 2) TRANSMISSION: tebal + material solid sepanjang direct ray.
        Transmission tr = measureTransmission(start, dir, maxDist, world);

        // 3) DIFFRACTION: jalur edge sekitar obstacle bila direct tertutup.
        float diffractionGain = 0f;
        float diffractionBias = 0f;
        if (!directClear) {
            DiffractionResult dif = probeDiffraction(from, to, dir, maxDist, world, direct);
            diffractionGain = dif.gain;
            diffractionBias = dif.distanceBias;
        }

        // 4) MEDIUM: air.
        MediumResult medium = probeMedium(from, to, start, dir, maxDist, world);

        // 5) REVERB: density sekitar listener + sender.
        ReverbResult rev = probeReverb(to, world);

        // ---- Akumulasi ----
        float directGain = directClear ? 1f : 0f;
        // transmission leak: e^(-absorption * thickness). absorption material-dependent.
        float transmitGain = tr.thickness <= 0 ? 0f : (float) Math.exp(-tr.absorption * tr.thickness);
        float gain = Math.max(directGain, Math.max(transmitGain, diffractionGain));

        // lowpass
        float lowpass = Float.MAX_VALUE;
        if (medium.inWater) {
            // air: sangat mendam dalam + absorption high-freq naik per jarak.
            lowpass = Math.min(lowpass, 700f);
        }
        if (tr.thickness >= 1) {
            // dinding: cutoff turun dengan tebal + material (wool/leaves lebih rendah).
            float base = tr.maxOpaque ? 350f : 2200f;
            float wallCut = Math.max(base, 2200f - tr.thickness * (tr.softMaterial ? 700f : 500f));
            lowpass = Math.min(lowpass, wallCut);
        }
        if (diffractionGain > 0f && diffractionGain < directGain) {
            lowpass = Math.min(lowpass, 2600f);
        }
        // air absorption: makin jauh makin mendam (tambahan per block).
        if (medium.waterBlocks > 0) {
            lowpass = Math.min(lowpass, Math.max(200f, 700f - medium.waterBlocks * 30f));
        }

        // distance bias
        float distanceBias = 0f;
        if (!directClear) {
            distanceBias += tr.thickness * (float) config.occlusionPenaltyPerBlock() * 0.7f;
            distanceBias += diffractionBias;
        }
        if (rev.enclosed) {
            distanceBias += (float) config.reverbPenalty() * rev.density;
        }
        if (medium.waterBlocks > 0) {
            distanceBias += medium.waterBlocks * 1.5f; // air memperpanjang jarak efektif (absorb)
        }

        boolean muffled = lowpass < 2400f || (!directClear && gain < 0.9f);
        boolean clear = directClear && !medium.inWater && tr.thickness == 0;
        float audibilityThreshold = 0.05f;
        boolean audible = gain >= audibilityThreshold
                && rawDistance <= (config.shoutRange() * 1.5 + distanceBias + config.voiceRange());

        return new SoundPath(gain, lowpass, rev.enclosed ? rev.density : 0f, distanceBias, muffled, audible, clear);
    }

    private RayTraceResult safeRayTrace(World world, Location from, Vector dir, double maxDist, FluidCollisionMode fluid) {
        try {
            return world.rayTraceBlocks(from, dir, maxDist, fluid, true);
        } catch (Throwable t) {
            // raytrace boleh lempar di world unload/dll → anggap tertutup (aman).
            return null;
        }
    }

    // ---------- Transmission ----------
    private static final class Transmission {
        final int thickness;        // block solid distinct
        final float absorption;     // per-block absorption (material avg)
        final boolean softMaterial; // wool/leaves/snow → lowpass lebih agresif
        final boolean maxOpaque;    // stone/obsidian → opaque penuh
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
        final boolean soft;     // wool/leaves → lowpass agresif
        final boolean opaque;   // stone/obsidian → opaque
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
        // leaves, snow, hay, moss: menyerap sedang
        if (n.contains("LEAVES") || n.contains("SNOW") || n.contains("HAY") || n.contains("MOSS") || n.contains("SPONGE"))
            return new MatAcoustics(1.6f, true, false);
        // wood/planks/logs: redam sedang, tidak opaque
        if (n.contains("PLANKS") || n.contains("LOG") || n.contains("WOOD") || n.contains("STEM") || n.contains("FENCE"))
            return new MatAcoustics(0.9f, false, false);
        // glass: redam kecil, tembus pandang tapi blok suara sebagian
        if (n.contains("GLASS")) return new MatAcoustics(0.4f, false, false);
        // dirt/grass/sand/gravel: redam sedang
        if (n.contains("DIRT") || n.contains("GRASS") || n.contains("SAND") || n.contains("GRAVEL") || n.contains("PODZOL"))
            return new MatAcoustics(1.0f, false, false);
        // stone/obsidian/bedrock/iron: opaque penuh, redam besar
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
        return (float) Math.max(0.2, 1.0 - extra * 0.05);
    }

    // ---------- Medium (air) ----------
    private static final class MediumResult {
        final boolean inWater;
        final int waterBlocks;
        MediumResult(boolean inWater, int waterBlocks) {
            this.inWater = inWater;
            this.waterBlocks = waterBlocks;
        }
    }

    private MediumResult probeMedium(Location from, Location to, Vector start, Vector dir, double maxDist, World world) {
        boolean inWater = isWater(world.getBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ()).getType())
                || isWater(world.getBlockAt(to.getBlockX(), to.getBlockY(), to.getBlockZ()).getType());
        int waterBlocks = 0;
        double step = 1.0;
        int prevBx = Integer.MIN_VALUE, prevBy = 0, prevBz = 0;
        for (double t = 0; t < maxDist; t += step) {
            double x = start.getX() + dir.getX() * t;
            double y = start.getY() + dir.getY() * t;
            double z = start.getZ() + dir.getZ() * t;
            int bx = floor(x), by = floor(y), bz = floor(z);
            if (bx == prevBx && by == prevBy && bz == prevBz) continue;
            prevBx = bx; prevBy = by; prevBz = bz;
            if (isWater(world.getBlockAt(bx, by, bz).getType())) {
                inWater = true;
                waterBlocks++;
            }
        }
        return new MediumResult(inWater, waterBlocks);
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
        return new ReverbResult(density > 0.45f, density);
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

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
