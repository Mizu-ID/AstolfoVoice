package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;

/**
 * RemoveCategoryPacket (outgoing, voicechat:remove_category).
 */
public class RemoveCategoryPacket implements Packet {

    public static final Key REMOVE_CATEGORY = new Key("remove_category");

    private String categoryId;

    public RemoveCategoryPacket() {
    }

    public RemoveCategoryPacket(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    @Override
    public Key getID() {
        return REMOVE_CATEGORY;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeString(categoryId, 16);
    }

    public static RemoveCategoryPacket fromBytes(FriendlyByteBuf buf) {
        RemoveCategoryPacket p = new RemoveCategoryPacket();
        p.categoryId = buf.readString(16);
        return p;
    }
}
