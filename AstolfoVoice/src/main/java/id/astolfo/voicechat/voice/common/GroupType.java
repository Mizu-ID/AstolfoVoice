package id.astolfo.voicechat.voice.common;

/**
 * Tipe grup, serialisasi byte-exact SVC (short): NORMAL=0, OPEN=1, ISOLATED=2.
 */
public enum GroupType {

    NORMAL,
    OPEN,
    ISOLATED;

    public short toInt() {
        return switch (this) {
            case OPEN -> 1;
            case ISOLATED -> 2;
            default -> 0;
        };
    }

    public static GroupType fromInt(short i) {
        return switch (i) {
            case 1 -> OPEN;
            case 2 -> ISOLATED;
            default -> NORMAL;
        };
    }
}
