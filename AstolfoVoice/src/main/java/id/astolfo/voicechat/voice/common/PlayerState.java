package id.astolfo.voicechat.voice.common;

import java.util.UUID;

/**
 * PlayerState — status player yang di-broadcast ke client via plugin channel.
 * Byte-exact SVC. Urutan byte:
 *   boolean disabled, boolean disconnected, UUID uuid, String name(32767),
 *   boolean hasGroup, [UUID group].
 */
public final class PlayerState {

    private UUID uuid;
    private String name;
    private boolean disabled;
    private boolean disconnected;
    private UUID group;

    public PlayerState() {
    }

    public PlayerState(UUID uuid, String name, boolean disabled, boolean disconnected) {
        this.uuid = uuid;
        this.name = name;
        this.disabled = disabled;
        this.disconnected = disconnected;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isDisconnected() {
        return disconnected;
    }

    public void setDisconnected(boolean disconnected) {
        this.disconnected = disconnected;
    }

    public UUID getGroup() {
        return group;
    }

    public void setGroup(UUID group) {
        this.group = group;
    }

    public boolean hasGroup() {
        return group != null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(disabled);
        buf.writeBoolean(disconnected);
        buf.writeUuid(uuid);
        buf.writeString(name, 32767);
        buf.writeBoolean(hasGroup());
        if (hasGroup()) {
            buf.writeUuid(group);
        }
    }

    public static PlayerState fromBytes(FriendlyByteBuf buf) {
        boolean disabled = buf.readBoolean();
        boolean disconnected = buf.readBoolean();
        UUID uuid = buf.readUuid();
        String name = buf.readString(32767);
        PlayerState state = new PlayerState(uuid, name, disabled, disconnected);
        if (buf.readBoolean()) {
            state.setGroup(buf.readUuid());
        }
        return state;
    }
}
