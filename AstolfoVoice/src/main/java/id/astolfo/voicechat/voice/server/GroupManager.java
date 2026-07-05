package id.astolfo.voicechat.voice.server;

import id.astolfo.voicechat.voice.common.ClientGroup;
import id.astolfo.voicechat.voice.common.GroupType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GroupManager — melacak grup yang ada + keanggotaan player.
 * Persistensi & password disederhanakan untuk MVP (Fase 1).
 */
public final class GroupManager {

    private final ConcurrentHashMap<UUID, Group> groups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> playerGroup = new ConcurrentHashMap<>(); // player → groupId

    public static final class Group {
        public final UUID id;
        public final String name;
        public final String password;
        public final boolean persistent;
        public final boolean hidden;
        public final GroupType type;

        public Group(UUID id, String name, String password, boolean persistent, boolean hidden, GroupType type) {
            this.id = id;
            this.name = name;
            this.password = password;
            this.persistent = persistent;
            this.hidden = hidden;
            this.type = type;
        }

        public ClientGroup toClientGroup() {
            return new ClientGroup(id, name, password != null, persistent, hidden, type);
        }
    }

    public Group createGroup(String name, String password, boolean persistent, boolean hidden, GroupType type) {
        UUID id = UUID.randomUUID();
        Group g = new Group(id, name, password, persistent, hidden, type);
        groups.put(id, g);
        return g;
    }

    public Group getGroup(UUID id) {
        return groups.get(id);
    }

    public Group getGroupByName(String name) {
        for (Group g : groups.values()) {
            if (g.name.equalsIgnoreCase(name)) return g;
        }
        return null;
    }

    public void removeGroup(UUID id) {
        groups.remove(id);
        playerGroup.values().removeIf(gid -> gid.equals(id));
    }

    public java.util.Collection<Group> allGroups() {
        return groups.values();
    }

    public boolean joinGroup(UUID player, UUID groupId, String password) {
        Group g = groups.get(groupId);
        if (g == null) return false;
        if (g.password != null && !g.password.isEmpty()) {
            if (password == null || !password.equals(g.password)) {
                return false;
            }
        }
        playerGroup.put(player, groupId);
        return true;
    }

    public void leaveGroup(UUID player) {
        playerGroup.remove(player);
    }

    public UUID getGroupOf(UUID player) {
        return playerGroup.get(player);
    }

    public java.util.List<UUID> getMembers(UUID groupId) {
        java.util.List<UUID> members = new java.util.ArrayList<>();
        playerGroup.forEach((p, gid) -> {
            if (gid.equals(groupId)) members.add(p);
        });
        return members;
    }
}
