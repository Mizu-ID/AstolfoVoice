package id.astolfo.voicechat.voice.common;

import java.util.regex.Pattern;

/**
 * VolumeCategory — kategori volume yang dikirim ke client via AddCategoryPacket.
 * Byte-exact SVC VolumeCategoryImpl.
 *
 * ID regex: ^[a-z_]{1,16}$. Icon = int[16][16] (ARGB) opsional.
 */
public final class VolumeCategory {

    public static final Pattern ID_REGEX = Pattern.compile("^[a-z_]{1,16}$");

    private final String id;
    private final String name;
    private final String nameTranslationKey;
    private final String description;
    private final String descriptionTranslationKey;
    private final int[][] icon; // null atau int[16][16]

    public VolumeCategory(String id, String name, String nameTranslationKey, String description,
                          String descriptionTranslationKey, int[][] icon) {
        if (id == null || !ID_REGEX.matcher(id).matches()) {
            throw new IllegalArgumentException("Volume category ID can only contain a-z and _ (max 16 chars)");
        }
        this.id = id;
        this.name = name;
        this.nameTranslationKey = nameTranslationKey;
        this.description = description;
        this.descriptionTranslationKey = descriptionTranslationKey;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameTranslationKey() {
        return nameTranslationKey;
    }

    public String getDescription() {
        return description;
    }

    public String getDescriptionTranslationKey() {
        return descriptionTranslationKey;
    }

    public int[][] getIcon() {
        return icon;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeString(id, 16);
        buf.writeString(name, 16);
        writeOptionalString(buf, nameTranslationKey);
        writeOptionalString(buf, description);
        writeOptionalString(buf, descriptionTranslationKey);
        buf.writeBoolean(icon != null);
        if (icon != null) {
            if (icon.length != 16) {
                throw new IllegalStateException("Icon must be 16x16");
            }
            for (int x = 0; x < 16; x++) {
                if (icon[x].length != 16) {
                    throw new IllegalStateException("Icon must be 16x16");
                }
                for (int y = 0; y < 16; y++) {
                    buf.writeInt(icon[x][y]);
                }
            }
        }
    }

    public static VolumeCategory fromBytes(FriendlyByteBuf buf) {
        String id = buf.readString(16);
        String name = buf.readString(16);
        String nameTranslationKey = readOptionalString(buf);
        String description = readOptionalString(buf);
        String descriptionTranslationKey = readOptionalString(buf);
        int[][] icon = null;
        if (buf.readBoolean()) {
            icon = new int[16][16];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    icon[x][y] = buf.readInt();
                }
            }
        }
        return new VolumeCategory(id, name, nameTranslationKey, description, descriptionTranslationKey, icon);
    }

    private static void writeOptionalString(FriendlyByteBuf buf, String s) {
        buf.writeBoolean(s != null);
        if (s != null) {
            buf.writeString(s, 32767);
        }
    }

    private static String readOptionalString(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return buf.readString(32767);
        }
        return null;
    }
}
