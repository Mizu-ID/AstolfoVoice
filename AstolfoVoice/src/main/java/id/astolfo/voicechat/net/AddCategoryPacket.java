package id.astolfo.voicechat.net;

import id.astolfo.voicechat.voice.common.FriendlyByteBuf;
import id.astolfo.voicechat.voice.common.VolumeCategory;

/**
 * AddCategoryPacket (outgoing, voicechat:add_category) — tambah kategori volume.
 */
public class AddCategoryPacket implements Packet {

    public static final Key ADD_CATEGORY = new Key("add_category");

    private VolumeCategory category;

    public AddCategoryPacket() {
    }

    public AddCategoryPacket(VolumeCategory category) {
        this.category = category;
    }

    public VolumeCategory getCategory() {
        return category;
    }

    @Override
    public Key getID() {
        return ADD_CATEGORY;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        category.toBytes(buf);
    }

    public static AddCategoryPacket fromBytes(FriendlyByteBuf buf) {
        AddCategoryPacket p = new AddCategoryPacket();
        p.category = VolumeCategory.fromBytes(buf);
        return p;
    }
}
