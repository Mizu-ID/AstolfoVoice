package id.astolfo.voicechat.api.impl;

import id.astolfo.voicechat.api.AstolfoApi;
import id.astolfo.voicechat.api.AstolfoListener;
import id.astolfo.voicechat.api.PlaybackHandle;
import id.astolfo.voicechat.api.PlaybackOptions;
import id.astolfo.voicechat.api.PrivateChannel;
import id.astolfo.voicechat.api.PrivateChannelOptions;
import id.astolfo.voicechat.api.VoiceStatus;
import id.astolfo.voicechat.audio.AudioEngine;
import id.astolfo.voicechat.voice.common.PlayerState;
import id.astolfo.voicechat.voice.server.GroupManager;
import id.astolfo.voicechat.voice.server.PlayerStateManager;
import id.astolfo.voicechat.voice.server.ServerVoiceEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AstolfoApiImpl — implementasi API publik. Di-register via ServicesManager.
 */
public final class AstolfoApiImpl implements AstolfoApi {

    private final ServerVoiceEvents server;
    private final PlayerStateManager playerStateManager;
    private final File audioDir;
    private final double defaultRange;
    private final AudioEngine audioEngine;

    private final ConcurrentHashMap<UUID, Double> voiceRangeOverrides = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> muted = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PrivateChannelImpl> privateChannels = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<AstolfoListener> listeners = new CopyOnWriteArrayList<>();

    public AstolfoApiImpl(ServerVoiceEvents server, File audioDir, double defaultRange, AudioEngine audioEngine) {
        this.server = server;
        this.playerStateManager = server.getPlayerStateManager();
        this.audioDir = audioDir;
        this.defaultRange = defaultRange;
        this.audioEngine = audioEngine;
    }

    // ---- Voice range ----
    @Override
    public void setVoiceRange(UUID player, double range) {
        double old = getVoiceRange(player);
        voiceRangeOverrides.put(player, range);
        for (AstolfoListener l : listeners) l.onPlayerVoiceRangeChanged(player, old, range);
    }

    @Override
    public double getVoiceRange(UUID player) {
        Double override = voiceRangeOverrides.get(player);
        return override != null ? override : defaultRange;
    }

    @Override
    public void resetPlayer(UUID player) {
        voiceRangeOverrides.remove(player);
        muted.remove(player);
    }

    // ---- Status ----
    @Override
    public VoiceStatus getPlayerStatus(UUID player) {
        PlayerState state = playerStateManager.getState(player);
        Player p = Bukkit.getPlayer(player);
        String name = p != null ? p.getName() : (state != null ? state.getName() : "?");
        String world = p != null && p.getWorld() != null ? p.getWorld().getName() : null;
        boolean connected = state != null && !state.isDisconnected();
        UUID group = state != null ? state.getGroup() : null;
        return new VoiceStatusImpl(player, name, connected, state != null && state.isDisabled(),
                muted.getOrDefault(player, false), false, getVoiceRange(player), group, world);
    }

    // ---- Playback ----
    @Override
    public PlaybackHandle playToPlayer(UUID player, String file, PlaybackOptions options) {
        if (audioEngine == null) return null;
        Player p = Bukkit.getPlayer(player);
        if (p == null) return null;
        return audioEngine.playToTargets(List.of(p), file, options);
    }

    @Override
    public PlaybackHandle broadcastWorld(String world, String file, PlaybackOptions options) {
        if (audioEngine == null) return null;
        var w = Bukkit.getWorld(world);
        if (w == null) return null;
        return audioEngine.playToTargets(List.copyOf(w.getPlayers()), file, options);
    }

    @Override
    public PlaybackHandle broadcastAll(String file, PlaybackOptions options) {
        if (audioEngine == null) return null;
        return audioEngine.playToTargets(List.copyOf(Bukkit.getOnlinePlayers()), file, options);
    }

    @Override
    public PlaybackHandle playAtLocation(String world, double x, double y, double z, String file, PlaybackOptions options) {
        // LocationSoundPacket playback = Fase 2 lanjutan; sementara broadcast ke world (static).
        return broadcastWorld(world, file, options);
    }

    @Override
    public PlaybackHandle playToGroup(String group, String file, PlaybackOptions options) {
        if (audioEngine == null) return null;
        GroupManager.Group g = server.getGroupManager().getGroupByName(group);
        if (g == null) return null;
        List<UUID> members = server.getGroupManager().getMembers(g.id);
        List<Player> targets = new java.util.ArrayList<>();
        for (UUID m : members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) targets.add(p);
        }
        return audioEngine.playToTargets(targets, file, options);
    }

    @Override
    public void stopPlayback(PlaybackHandle handle) {
        if (audioEngine != null) audioEngine.stop(handle);
    }

    // ---- Private channel ----
    @Override
    public PrivateChannel createPrivateChannel(List<UUID> members, PrivateChannelOptions options) {
        PrivateChannelImpl ch = new PrivateChannelImpl(members, options);
        privateChannels.put(ch.getId(), ch);
        return ch;
    }

    @Override
    public void removePrivateChannel(PrivateChannel channel) {
        if (channel instanceof PrivateChannelImpl impl) {
            privateChannels.remove(impl.getId());
        }
    }

    public PrivateChannelImpl getPrivateChannel(UUID id) {
        return privateChannels.get(id);
    }

    // ---- Events ----
    @Override
    public void registerListener(AstolfoListener listener) {
        listeners.addIfAbsent(listener);
    }

    @Override
    public void unregisterListener(AstolfoListener listener) {
        listeners.remove(listener);
    }

    public void fireConnected(UUID player) {
        for (AstolfoListener l : listeners) l.onPlayerConnected(player);
    }

    public void fireDisconnected(UUID player) {
        for (AstolfoListener l : listeners) l.onPlayerDisconnected(player);
    }

    public void setMuted(UUID player, boolean muted) {
        this.muted.put(player, muted);
    }

    public boolean isMuted(UUID player) {
        return muted.getOrDefault(player, false);
    }
}
