package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.voice.common.MicPacket;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProximityResolver — tentukan siapa mendengar mic packet + terapkan sound physics.
 * Tier 1 (default): occlusion gate (raytrace) + modulasi distance (muffle).
 * Pertanyaan user dipetakan:
 *   - apakah tertutup?  → occlusion (ray solid)
 *   - apakah terdengar?  → audibility gate (distance + occlusion)
 *   - apakah mendam?     → muffle: distance += occlusion*penalty
 *   - apakah bergema?    → enclosed probe → +reverb_penalty (Tier1 simulasi)
 *   - apakah jelas?      → line of sight (los)
 *
 * Raytrace resmi main-thread di Bukkit; cache pre-compute via tick task (Fase 3).
 * Untuk MVP, raytrace dijalankan async atas world snapshot baca (Paper mengizinkan
 * untuk posisi/blok baca di banyak kasus). Flag async_raytrace mengontrol ini.
 */
public final class ProximityResolver {

    private final AstolfoConfig config;
    private final GroupManager groupManager;

    public ProximityResolver(AstolfoConfig config, GroupManager groupManager) {
        this.config = config;
        this.groupManager = groupManager;
    }

    public void processMic(Player sender, MicPacket mic, ServerVoiceEvents server, long timestamp) {
        boolean whispering = mic.isWhispering();
        UUID senderUuid = sender.getUniqueId();

        // Group sound: bila sender dalam grup, kirim ke anggota grup (bypass physics bila config).
        UUID groupId = groupManager.getGroupOf(senderUuid);
        if (groupId != null) {
            java.util.List<UUID> members = groupManager.getMembers(groupId);
            for (UUID member : members) {
                if (member.equals(senderUuid)) continue;
                Player listener = org.bukkit.Bukkit.getPlayer(member);
                if (listener == null) continue;
                float distance = config.bypassPhysicsForGroups() ? (float) config.voiceRange() : computeDistance(sender, listener, whispering);
                server.sendPlayerSound(listener, senderUuid, mic.getOpusData(), whispering, distance, null);
            }
            return;
        }

        // Proximity: kirim ke player dalam broadcast_range yang same-world.
        double baseRange = whispering ? config.whisperRange() : config.voiceRange();
        double broadcastRange = config.broadcastRange() < 0 ? baseRange + 1 : config.broadcastRange();
        // broadcast minimal shout_range supaya teriak tetap terjangkau
        broadcastRange = Math.max(broadcastRange, config.shoutRange());

        Location senderLoc = sender.getEyeLocation();
        World world = senderLoc.getWorld();
        if (world == null) return;

        for (Player listener : world.getPlayers()) {
            if (listener.getUniqueId().equals(senderUuid)) continue;
            if (!server.isCompatible(listener)) continue;
            Location listenerLoc = listener.getEyeLocation();
            double dist = senderLoc.distance(listenerLoc);
            if (dist > broadcastRange) continue;

            // Dynamic range: tentukan distance efektif (Tier 1: pakai whisper flag + base).
            // Dynamic RMS → distance akan di-hook di Fase 3 setelah decode opus tersedia.
            float effectiveDistance = (float) baseRange;
            boolean effectiveWhisper = whispering;

            // Sound physics Tier 1
            if (config.soundPhysicsEnabled() && config.raytraceEnabled()) {
                OcclusionResult occ = computeOcclusion(senderLoc, listenerLoc, world);
                if (!occ.audible(config.maxOcclusionBlocks(), effectiveDistance)) {
                    continue; // tidak terdengar → skip kirim (hemat bandwidth)
                }
                // muffle: tambah penalti per block solid
                effectiveDistance += occ.occlusionCount * (float) config.occlusionPenaltyPerBlock();
                // bergema (enclosed) → penalti kecil
                if (occ.enclosed) {
                    effectiveDistance += (float) config.reverbPenalty();
                }
            }
            server.sendPlayerSound(listener, senderUuid, mic.getOpusData(), effectiveWhisper, effectiveDistance, null);
        }
    }

    private float computeDistance(Player sender, Player listener, boolean whispering) {
        return (float) (whispering ? config.whisperRange() : config.voiceRange());
    }

    /** Hasil probe occlusion + line-of-sight + enclosed. */
    private static final class OcclusionResult {
        final int occlusionCount;
        final boolean lineOfSight;
        final boolean enclosed;

        OcclusionResult(int occlusionCount, boolean lineOfSight, boolean enclosed) {
            this.occlusionCount = occlusionCount;
            this.lineOfSight = lineOfSight;
            this.enclosed = enclosed;
        }

        boolean audible(int maxOcclusion, double effectiveDistance) {
            // audible jika LOS jelas ATAU occlusion masih dalam batas
            return lineOfSight || occlusionCount <= maxOcclusion;
        }
    }

    private OcclusionResult computeOcclusion(Location from, Location to, World world) {
        Vector start = from.toVector();
        Vector end = to.toVector();
        Vector dir = end.clone().subtract(start);
        double maxDist = dir.length();
        if (maxDist < 1e-3) {
            return new OcclusionResult(0, true, false);
        }
        dir.normalize();

        // Line of sight via raytrace API
        RayTraceResult result = world.rayTraceBlocks(from, dir, maxDist,
                org.bukkit.FluidCollisionMode.NEVER, true);
        boolean los = result == null || result.getHitBlock() == null;

        // Occlusion tebal: hitung block solid sepanjang ray (step 1 block)
        int occlusionCount = 0;
        Vector step = dir.clone().multiply(1.0);
        Vector cursor = start.clone();
        double traveled = 0;
        int maxBlocks = config.maxOcclusionBlocks() + 2;
        while (traveled < maxDist && occlusionCount < maxBlocks) {
            cursor.add(step);
            traveled += 1.0;
            Block b = world.getBlockAt(cursor.getBlockX(), cursor.getBlockY(), cursor.getBlockZ());
            Material type = b.getType();
            if (type.isOccluding() && type.isSolid()) {
                occlusionCount++;
            }
        }

        // Enclosed probe: density block solid dalam box ±radius sekitar listener
        boolean enclosed = false;
        int r = config.reverbProbeRadius();
        if (r > 0) {
            int lx = to.getBlockX(), ly = to.getBlockY(), lz = to.getBlockZ();
            int solid = 0, total = 0;
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        total++;
                        Material m = world.getBlockAt(lx + x, ly + y, lz + z).getType();
                        if (m.isOccluding() && m.isSolid()) solid++;
                    }
                }
            }
            enclosed = ((double) solid / total) > 0.45;
        }

        return new OcclusionResult(occlusionCount, los, enclosed);
    }
}
