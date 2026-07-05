# AstolfoVoice — Protocol Reference

Wire format verbatim dari Simple Voice Chat (https://github.com/henkelmax/simple-voice-chat),
direkonstruksi dari sumber `common/src/main/java/de/maxhenkel/voicechat/voice/common/*`
dan `bukkit/src/main/java/de/maxhenkel/voicechat/net/*`.
Tujuan: dokumen acuan agar AstolfoVoice byte-exact kompatibel.

Lisensi sumber: GPL-3.0 (henkelmax).

---

## 1. Dua jalur transport

### 1.1 Plugin channel (TCP, Bukkit Messenger)
Namespace: `voicechat`. Daftar channel lihat `IMPLEMENTATION_PLAN.md` §D.
Format biner: tiap class `Packet` punya `toBytes(FriendlyByteBuf)` / `fromBytes(FriendlyByteBuf)`.
Dikirim via `player.sendPluginMessage(plugin, "voicechat:<name>", bytes)`.

### 1.2 Raw UDP (default port 24454)
Tidak melalui Bukkit Messenger. `DatagramSocket` murni.

---

## 2. UDP wire format pesan

```
+--------+-------------------+--------------------------------+
| 0xFF   | UUID playerID     | byte[] encryptedPayload        |
| (1B)   | (16B, 2 long MSB/LSB) | (VarInt len + bytes)       |
+--------+-------------------+--------------------------------+
```
- Byte pertama = MAGIC `0b11111111` (0xFF). Jika bukan → drop.
- `playerID` = UUID pemain (routing key, di luar enkripsi). Server cari `secrets[playerID]`.
- `encryptedPayload` = `IV(12B) + AES-128-GCM ciphertext (tag 128-bit)` dari:
  ```
  +--------------+---------------------+
  | byte typeID  | packet fields...    |
  +--------------+---------------------+
  ```
- Konstanta: `MAX_VOICE_CHAT_PACKET_SIZE = 2048`, `MAX_OPUS_PAYLOAD_SIZE = 1275`, UDP recv buffer 4096.

`Secret` (AES/GCM/NoPadding, key 16B, IV 12B random per encrypt, tag 128-bit):
- encrypt: `payload = iv(12) + cipher.doFinal(plaintext)`.
- decrypt: split `iv = payload[0..12]`, `data = payload[12..]`, `cipher.doFinal(data)`.

---

## 3. Registry tipe paket UDP

| ID   | Class                    | TTL    | Field (urutan toBytes)                                                                                |
|------|--------------------------|--------|-------------------------------------------------------------------------------------------------------|
| 0x1  | MicPacket                | 500ms  | byte[] opus (VarInt+bytes, max 1275), long seq, boolean whispering                                    |
| 0x2  | PlayerSoundPacket        | 10s    | UUID channelId, UUID sender, byte[] opus, long seq, float distance, byte flags, [String category(16)]|
| 0x3  | GroupSoundPacket         | 10s    | UUID channelId, UUID sender, byte[] opus, long seq, byte flags, [category]                           |
| 0x4  | LocationSoundPacket      | 10s    | UUID channelId, UUID sender, double x, double y, double z, byte[] opus, long seq, float distance, byte flags, [category] |
| 0x5  | AuthenticatePacket       | 10s    | UUID playerUUID, Secret(16B raw)                                                                      |
| 0x6  | AuthenticateAckPacket    | -      | (empty)                                                                                               |
| 0x7  | PingPacket               | -      | UUID id, long timestamp                                                                               |
| 0x8  | KeepAlivePacket          | -      | (empty)                                                                                               |
| 0x9  | ConnectionCheckPacket    | -      | (empty)                                                                                               |
| 0xA  | ConnectionCheckAckPacket | -      | (empty)                                                                                               |

Flag byte (PlayerSound/Group/Location): bit0 `0b1` = whispering, bit1 `0b10` = hasCategory (jika set, baca `String category` max 16 char). Location/Group tidak pakai whisper bit (hanya hasCategory).

`isFromClientAudioChannel()` = `sequenceNumber < 0` (channel audio pakai seq -1).

---

## 4. Plugin-channel packet detail

### 4.1 `voicechat:request_secret` (incoming)
`int compatibilityVersion`.

### 4.2 `voicechat:secret` (outgoing)
Urutan `toBytes`:
```
Secret(16B raw) | int serverPort | UUID playerUUID | byte codecOrdinal |
int mtuSize | double voiceChatDistance | int keepAlive | boolean groupsEnabled |
String voiceHost (UTF, VarInt) | boolean allowRecording
```
`codec` = enum `{VOIP=0, AUDIO=1, RESTRICTED_LOWDELAY=2}` ordinal byte.

### 4.3 `voicechat:states` (outgoing) — full snapshot
`int count` lalu `count × PlayerState`.

### 4.4 `voicechat:state` / `voicechat:remove_state`
`PlayerState` (lihat §5).

### 4.5 `PlayerState` (biner, dipakai states/state/remove)
```
boolean disabled | boolean disconnected | UUID uuid | String name (UTF max 32767) |
boolean hasGroup | (jika true) UUID group
```

### 4.6 Group/category packets
`AddGroupPacket`: ClientGroup (UUID id, boolean persistent, boolean hasPassword, boolean protectedOrType..., String name, dst. — replikasi verbatim SVC `ClientGroup`).
`CreateGroupPacket`/`JoinGroupPacket`/`LeaveGroupPacket`: nama group + password opsional.
`AddCategoryPacket`/`RemoveCategoryPacket`: `String categoryId` + ikon.
`JoinedGroupPacket`: ClientGroup.
`UpdateStatePacket`: `boolean disabled`, `boolean disconnected`.

(Catatan: implementasi AstolfoVoice menyalin `ClientGroup` & struktur group packet persis dari SVC `voice/common/ClientGroup.java` + `bukkit/net/AddGroupPacket.java` saat Fase 1.)

---

## 5. Audio konstanta

- Sample rate: 48000 Hz.
- Frame: 20 ms = 960 sample mono `short`.
- Opus application: VOIP (default), AUDIO, RESTRICTED_LOWDELAY.
- `AudioUtils`: `bytesToShorts` little-endian, `floatsToShortsNormalized`, `shortsToFloatsNormalized`, `getHighestAudioLevel` (peak dB), `dbToLinear`/`linearToDb`, `combineAudio` (mix dengan clip).

---

## 6. Handshake sequence (urutan)

1. Client → (plugin channel) `RequestSecretPacket(compatVersion)`.
2. Server: `clientCompatibilities[uuid] = compat`. Jika `compat != server compat` → kirim pesan incompatible (compat<=6: plain string; >=7: translatable). Tetap `generateNewSecret(uuid)`.
3. Server → `SecretPacket(secret, port, uuid, codec, mtu, distance, keepalive, groups, host, allowRecording)`.
4. Client buka UDP → kirim `AuthenticatePacket(uuid, secret)` (terenkripsi dengan secret tsb).
5. Server: cek `secrets[uuid].equals(packet.secret)`. Buat `ClientConnection(uuid, addr)` → `unCheckedConnections`. Balas `AuthenticateAckPacket`.
6. Client → `ConnectionCheckPacket`. Server pindah → `connections`, fire `PlayerConnectedEvent`, balas `ConnectionCheckAckPacket`.
7. Keepalive: server kirim `KeepAlivePacket` tiap `keep_alive` ms. Client balas `KeepAlivePacket` (update `lastKeepAliveResponse`). Timeout = `keep_alive * 10` → remove secret + kirim SecretPacket baru (reconnect).
8. Quit: `disconnectClient(uuid)`.

---

## 7. Multi-version compatibility note

`compatibilityVersion` (int) di `RequestSecretPacket` = mekanisme negosiasi. SVC saat ini 20.
AstolfoVoice: default target 20; adapter per versi via `VersionedPacketRegistry` (Fase 6).
Untuk client lama dengan format berbeda, serializer UDP dipilih per-koneksi berdasarkan `compat`.
Wire format di atas valid untuk compat 20.
