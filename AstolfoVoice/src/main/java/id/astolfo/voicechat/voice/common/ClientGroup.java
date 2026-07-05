package id.astolfo.voicechat.voice.common;

import java.util.Objects;
import java.util.UUID;

/**
 * ClientGroup — representasi grup yang dikirim ke client. Byte-exact SVC.
 * Urutan byte: UUID id, String name(512), boolean hasPassword, boolean persistent,
 *              boolean hidden, short typeOrdinal.
 */
public final class ClientGroup {

    private final UUID id;
    private final String name;
    private final boolean hasPassword;
    private final boolean persistent;
    private final boolean hidden;
    private final GroupType type;

    public ClientGroup(UUID id, String name, boolean hasPassword, boolean persistent, boolean hidden, GroupType type) {
        this.id = id;
        this.name = name;
        this.hasPassword = hasPassword;
        this.persistent = persistent;
        this.hidden = hidden;
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean hasPassword() {
        return hasPassword;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public boolean isHidden() {
        return hidden;
    }

    public GroupType getType() {
        return type;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUuid(id);
        buf.writeString(name, 512);
        buf.writeBoolean(hasPassword);
        buf.writeBoolean(persistent);
        buf.writeBoolean(hidden);
        buf.writeShort(type.toInt());
    }

    public static ClientGroup fromBytes(FriendlyByteBuf buf) {
        UUID id = buf.readUuid();
        String name = buf.readString(512);
        boolean hasPassword = buf.readBoolean();
        boolean persistent = buf.readBoolean();
        boolean hidden = buf.readBoolean();
        GroupType type = GroupType.fromInt(buf.readShort());
        return new ClientGroup(id, name, hasPassword, persistent, hidden, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientGroup)) return false;
        return Objects.equals(id, ((ClientGroup) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
