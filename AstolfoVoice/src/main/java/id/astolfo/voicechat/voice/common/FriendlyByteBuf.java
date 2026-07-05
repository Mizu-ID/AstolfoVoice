package id.astolfo.voicechat.voice.common;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * Re-implementasi tipis dari FriendlyByteBuf netty yang dipakai Simple Voice Chat.
 * Tujuan: serialisasi byte-exact kompatibel dengan client SVC, TANPA dependensi
 * langsung ke netty (netty tetap transitif lewat Paper, tapi kita self-contained
 * supaya format wire tidak bergantung versi netty).
 *
 * Format yang direplikasi (sama dengan netty FriendlyByteBuf):
 *  - VarInt        : 7 bit per byte, continuation bit 0x80, max 5 byte.
 *  - UUID          : long MSB + long LSB.
 *  - String (UTF)  : VarInt length(byte) + UTF-8 bytes.
 *  - ByteArray     : VarInt length + bytes.
 *  - ShortArray    : VarInt count + count x short (big-endian).
 *  - Long/Int/Float/Double/Boolean : big-endian primitif netty.
 *
 * Alias netty-style (writeUUID/readUUID/writeUtf/readUtf) disediakan agar port
 * kode SVC mudah, semua mendelegasi ke implementasi utama.
 */
public final class FriendlyByteBuf {

    private final ByteArrayOutputStream out;
    private byte[] in;
    private int inPos;
    private int inLen;

    public FriendlyByteBuf() {
        this.out = new ByteArrayOutputStream(256);
        this.in = null;
    }

    public FriendlyByteBuf(byte[] data) {
        this.in = data;
        this.inPos = 0;
        this.inLen = data.length;
        this.out = null;
    }

    public boolean isReader() {
        return in != null;
    }

    public byte[] toByteArray() {
        if (out != null) {
            return out.toByteArray();
        }
        return Arrays.copyOfRange(in, inPos, inLen);
    }

    // ---- VarInt ----

    public FriendlyByteBuf writeVarInt(int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                writeByte(value);
                return this;
            }
            writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    public int readVarInt() {
        int value = 0;
        int length = 0;
        int b;
        do {
            b = readUnsignedByte();
            value |= (b & 0x7F) << (length * 7);
            length++;
            if (length > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((b & 0x80) != 0);
        return value;
    }

    // ---- Primitives (big-endian, sesuai netty) ----

    public FriendlyByteBuf writeByte(int value) {
        out.write(value & 0xFF);
        return this;
    }

    public byte readByte() {
        return (byte) in[inPos++];
    }

    public int readUnsignedByte() {
        return in[inPos++] & 0xFF;
    }

    public FriendlyByteBuf writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
        return this;
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public FriendlyByteBuf writeShort(int value) {
        writeByte((value >>> 8) & 0xFF);
        writeByte(value & 0xFF);
        return this;
    }

    public short readShort() {
        return (short) ((readUnsignedByte() << 8) | readUnsignedByte());
    }

    public FriendlyByteBuf writeInt(int value) {
        writeByte((value >>> 24) & 0xFF);
        writeByte((value >>> 16) & 0xFF);
        writeByte((value >>> 8) & 0xFF);
        writeByte(value & 0xFF);
        return this;
    }

    public int readInt() {
        return (readUnsignedByte() << 24) | (readUnsignedByte() << 16)
                | (readUnsignedByte() << 8) | readUnsignedByte();
    }

    public FriendlyByteBuf writeLong(long value) {
        writeByte((int) (value >>> 56));
        writeByte((int) (value >>> 48));
        writeByte((int) (value >>> 40));
        writeByte((int) (value >>> 32));
        writeByte((int) (value >>> 24));
        writeByte((int) (value >>> 16));
        writeByte((int) (value >>> 8));
        writeByte((int) value);
        return this;
    }

    public long readLong() {
        return ((long) readUnsignedByte() << 56)
                | ((long) readUnsignedByte() << 48)
                | ((long) readUnsignedByte() << 40)
                | ((long) readUnsignedByte() << 32)
                | ((long) readUnsignedByte() << 24)
                | ((long) readUnsignedByte() << 16)
                | ((long) readUnsignedByte() << 8)
                | (long) readUnsignedByte();
    }

    public FriendlyByteBuf writeFloat(float value) {
        writeInt(Float.floatToIntBits(value));
        return this;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public FriendlyByteBuf writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
        return this;
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    // ---- UUID (MSB + LSB) ----

    public FriendlyByteBuf writeUuid(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
        return this;
    }

    public UUID readUuid() {
        return new UUID(readLong(), readLong());
    }

    // alias netty-style
    public FriendlyByteBuf writeUUID(UUID uuid) {
        return writeUuid(uuid);
    }

    public UUID readUUID() {
        return readUuid();
    }

    // ---- ByteArray (VarInt len + bytes) ----

    public FriendlyByteBuf writeByteArray(byte[] value) {
        writeVarInt(value.length);
        writeBytes(value);
        return this;
    }

    public byte[] readByteArray() {
        int len = readVarInt();
        return readBytes(len);
    }

    /** Sama SVC: readByteArray dengan batas maksimum ( VarInt len dibatasi ). */
    public byte[] readByteArray(int maxLength) {
        int len = readVarInt();
        if (len > maxLength) {
            throw new RuntimeException("ByteArray length too big: " + len + " > " + maxLength);
        }
        return readBytes(len);
    }

    public FriendlyByteBuf writeBytes(byte[] value) {
        for (byte b : value) {
            writeByte(b);
        }
        return this;
    }

    public byte[] readBytes(int len) {
        byte[] result = new byte[len];
        System.arraycopy(in, inPos, result, 0, len);
        inPos += len;
        return result;
    }

    public int readableBytes() {
        return inLen - inPos;
    }

    // ---- String (VarInt len + UTF-8) ----

    public FriendlyByteBuf writeString(String value) {
        byte[] utf = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(utf.length);
        writeBytes(utf);
        return this;
    }

    /** Overload dengan maxLength (diabaikan saat write; netty hanya validasi saat read). */
    public FriendlyByteBuf writeString(String value, int maxLength) {
        return writeString(value);
    }

    public String readString() {
        return readString(32767);
    }

    public String readString(int maxLength) {
        int len = readVarInt();
        if (len > maxLength * 4) {
            throw new RuntimeException("String length too big: " + len);
        }
        byte[] utf = readBytes(len);
        return new String(utf, StandardCharsets.UTF_8);
    }

    // alias netty-style
    public FriendlyByteBuf writeUtf(String value) {
        return writeString(value);
    }

    public FriendlyByteBuf writeUtf(String value, int maxLength) {
        return writeString(value);
    }

    public String readUtf() {
        return readString(32767);
    }

    public String readUtf(int maxLength) {
        return readString(maxLength);
    }

    // ---- ShortArray (VarInt count + big-endian shorts) ----

    public FriendlyByteBuf writeShortArray(short[] value) {
        writeVarInt(value.length);
        for (short s : value) {
            writeShort(s);
        }
        return this;
    }

    public short[] readShortArray() {
        int count = readVarInt();
        short[] result = new short[count];
        for (int i = 0; i < count; i++) {
            result[i] = readShort();
        }
        return result;
    }
}
