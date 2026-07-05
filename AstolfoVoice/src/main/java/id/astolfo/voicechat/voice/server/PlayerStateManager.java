package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.voice.common.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerStateManager — melacak state player (disabled, disconnected, group).
 * UpdateStatePacket (incoming) mengatur disabled. Broadcast state via plugin channel.
 */
public final class PlayerStateManager {

    private final ConcurrentHashMap<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public void onPlayerJoin(Player player) {
        PlayerState state = new PlayerState(player.getUniqueId(), player.getName(), false, false);
        states.put(player.getUniqueId(), state);
    }

    public void onPlayerQuit(Player player) {
        states.remove(player.getUniqueId());
    }

    public void onUpdateStatePacket(Player player, boolean disabled) {
        PlayerState state = states.get(player.getUniqueId());
        if (state != null) {
            state.setDisabled(disabled);
        }
    }

    public void setDisconnected(UUID uuid, boolean disconnected) {
        PlayerState state = states.get(uuid);
        if (state != null) {
            state.setDisconnected(disconnected);
        }
    }

    public PlayerState getState(UUID uuid) {
        return states.get(uuid);
    }

    /** Bangun state untuk player tertentu (dengan nama terbaru). */
    public PlayerState buildState(UUID uuid) {
        PlayerState state = states.get(uuid);
        if (state == null) return null;
        Player player = Bukkit.getPlayer(uuid);
        String name = player != null ? player.getName() : state.getName();
        PlayerState copy = new PlayerState(uuid, name, state.isDisabled(), state.isDisconnected());
        copy.setGroup(state.getGroup());
        return copy;
    }

    public java.util.Collection<PlayerState> allStates() {
        return states.values();
    }
}
