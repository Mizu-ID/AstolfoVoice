# AstolfoVoice — Implementation Plan

> Fork/reimplementation modern dari Simple Voice Chat (https://github.com/henkelmax/simple-voice-chat)
> untuk Bukkit/Spigot/Paper. Protokol UDP + handshake **identik & kompatibel** dengan client
> Simple Voice Chat biasa, jadi pemain tidak perlu ganti mod.

---

## 0. Tujuan & batas yang jujur dulu

Yang bisa dilakukan server-side murni (tanpa mod client tambahan):
- Gate audibility (pakah terdengar / tidak) — bisa, server tinggal tidak mengirim paket.
- Modulasi `distance` pada `PlayerSoundPacket`/`LocationSoundPacket` untuk simulasi redam/mendam — bisa, karena client memakai field `distance` untuk kurva attenuation.
- Noise cancellation pada PCM sebelum re-encode — bisa tapi mahal (decode→DSP→encode per frame per speaker).
- Voice range dinamis dari RMS (bisik→teriak) — bisa, hitung dari opus yang didecode.

Yang **tidak bisa** dilakukan server-side murni (perlu diakui):
- Reverb/EQ/low-pass "asli" — mixer client SVC punya OpenAL fixed pipeline. Server hanya bisa (a) menaikkan `distance` agar terdengar lebih pelan/mendam, atau (b) mode opt-in "PCM DSP bake": decode → low-pass + convolution reverb → re-encode, lalu kirim paket "static/locational" ke listener. Mode (b) menambah latency + CPU, jadi opt-in, bukan default.

Jadi sound physics dirancang dua tier:
- **Tier 1 (default, murah):** raytrace occlusion → naikkan `distance` efektif + gate audibility. Simulasi mendam via jarak.
- **Tier 2 (opt-in, mahal):** PCM DSP bake (low-pass muffle + reverb) untuk listener yang ingin "bergema/jelas" beneran. Aktif per-world/per-listener via config.

---

## 1. Ringkasan protokol yang dipelajari dari SVC

Dua jalur transport, keduanya wajib dipertahankan:

### 1.1 Plugin channel (TCP, via Bukkit Messenger) — handshake & state
- `voicechat:request_secret` (incoming): `int compatibilityVersion`
- `voicechat:secret` (outgoing): `Secret(16 byte)` + `int port` + `UUID playerUUID` + `byte codecOrdinal` + `int mtuSize` + `double voiceChatDistance` + `int keepAlive` + `boolean groupsEnabled` + `String voiceHost` + `boolean allowRecording`
- `voicechat:player_states`, `voicechat:player_state`, `voicechat:remove_player_state`, `voicechat:add_group`, `voicechat:remove_group`, `voicechat:joined_group`, `voicechat:add_category`, `voicechat:remove_category`, `voicechat:update_state`, `voicechat:create_group`, `voicechat:join_group`, `voicechat:leave_group`

### 1.2 Raw UDP socket (default port 24454) — audio + auth
Wire format per paket:
```
[0xFF magic] [UUID playerID] [len][byte[] encryptedPayload]
encryptedPayload = IV(12) + AES-128-GCM ciphertext(128-bit tag)
decrypted = [byte packetType] [packet fields...]
```
Registry tipe paket (HARUS sama):
| ID  | Paket                  | Field                                                                 | TTL    |
|-----|------------------------|-----------------------------------------------------------------------|--------|
| 0x1 | MicPacket              | byte[] opus, long seq, bool whispering                                | 500ms  |
| 0x2 | PlayerSoundPacket      | UUID channelId, UUID sender, byte[] opus, long seq, float distance, byte flags, [String category] | 10s |
| 0x3 | GroupSoundPacket       | UUID channelId, UUID sender, byte[] opus, long seq, byte flags, [category] | 10s |
| 0x4 | LocationSoundPacket    | UUID channelId, UUID sender, Vec3 loc, byte[] opus, long seq, float distance, byte flags, [category] | 10s |
| 0x5 | AuthenticatePacket     | UUID playerUUID, Secret                                               | 10s    |
| 0x6 | AuthenticateAckPacket  | (empty)                                                               |        |
| 0x7 | PingPacket             | UUID id, long timestamp                                               |        |
| 0x8 | KeepAlivePacket        | (empty)                                                               |        |
| 0x9 | ConnectionCheckPacket  | (empty)                                                               |        |
| 0xA | ConnectionCheckAckPacket | (empty)                                                            |        |

Flag byte: `0b1 = whispering`, `0b10 = hasCategory`.

Audio: Opus 48 kHz, 20 ms = 960 sample mono short. `MAX_OPUS_PAYLOAD_SIZE = 1275`, `MAX_VOICE_CHAT_PACKET_SIZE = 2048`.

### 1.3 Handshake (urutan wajib dipertahankan)
1. Client → server: `RequestSecretPacket(compatVersion)` lewat plugin channel.
2. Server generate `Secret` (AES-128-GCM 16 byte), simpan per UUID, balas `SecretPacket(...)`.
3. Client buka UDP, kirim `AuthenticatePacket(uuid, secret)`.
4. Server cek secret cocok → buat `ClientConnection` di `unCheckedConnections` → balas `AuthenticateAckPacket`.
5. Client kirim `ConnectionCheckPacket`.
6. Server pindah koneksi ke `connections`, fire connect event, balas `ConnectionCheckAckPacket`.
7. KeepAlive tiap `keepAlive` ms; timeout `keepAlive * 10`.

### 1.4 Kompatibilitas multi-versi client
Kunci negosiasi versi = `int compatibilityVersion` di `RequestSecretPacket` (SVC saat ini = 20).
Rancangan: **VersionedPacketRegistry** — tiap koneksi menyimpan `compatVersion`-nya, dan serializer/deserializer UDP dipilih per-koneksi berdasarkan versi itu. Versi 20 = implementasi penuh (di atas). Versi legacy ditambahkan sebagai adapter terpisah (config + code), bukan rewrite. Catatan jujur: "semua versi" berarti maintenance registry per versi; default target = versi terbaru, adapter legacy menyusul bertahap.

---

## 2. Arsitektur module (Gradle multiproject, ringkas dari SVC)

```
astolfovoice/
├─ api/                 # API publik untuk plugin lain / Denizen / Skript
├─ common/              # protokol UDP, packet, crypto, audio utils, DSP
├─ bukkit/              # implementasi Bukkit/Paper + commands + integrasi
└─ build.gradle, settings.gradle, gradle/
```

Target: Java 25 (virtual threads), Paper 1.21+, Bukkit API. Build = shadow jar tunggal `AstolfoVoice.jar`.

---

## 3. Struktur package inti

```
id.astolfo.voicechat
├─ AstolfoVoice.java                 # main plugin
├─ config/ AstolfoConfig.java        # baca config.yml (voice/whisper/shout range, DSP, dll)
├─ net/
│  ├─ NetManager.java                # registrasi plugin channel (handshake/state)
│  ├─ RequestSecretPacket.java
│  ├─ SecretPacket.java
│  ├─ PlayerStatesPacket.java ... dst (semua channel SVC direplikasi)
│  └─ PacketRateLimiter.java
├─ voice/
│  ├─ common/  (UDP packet: Mic, PlayerSound, GroupSound, LocationSound, Auth, AuthAck, Ping, KeepAlive, ConnCheck, ConnCheckAck, Secret, NetworkMessage, AudioUtils, Utils)
│  ├─ server/
│  │  ├─ VoiceServer.java            # UDP socket thread + async packet processor
│  │  ├─ ClientConnection.java
│  │  ├─ PlayerStateManager.java
│  │  ├─ ServerGroupManager.java
│  │  ├─ ServerCategoryManager.java
│  │  ├─ PingManager.java
│  │  └─ ProximityResolver.java      # logika broadcast + distance
│  └─ work/
│     ├─ AudioPipeline.java          # decode→DSP→encode (async, pooled)
│     ├─ NoiseSuppression.java       # RNNoise wrapper
│     ├─ SoundPhysics.java           # raytrace occlusion + muffle + reverb gate
│     └─ LoudnessMeter.java          # RMS→distance dinamis
├─ audio/
│  ├─ AudioFileRegistry.java         # scan plugins/AstolfoVoice/audio/
│  ├─ DecoderFactory.java            # mp3/ogg/wav → 48k mono short[]
│  ├─ Mp3Decoder.java, OggDecoder.java, WavDecoder.java
│  └─ StreamingAudioPlayer.java      # bungkus AudioPlayer API SVC, frame 960
├─ command/
│  ├─ AstolfoCommand.java            # /astolfo ...
│  └─ subcommands: PlayCommand, VoiceRangeCommand, BroadcastCommand, StatusCommand, ResetCommand, ReloadCommand
├─ integration/
│  ├─ PlaceholderAPIExpansion.java   # %astolfo_voice_status%, %astolfo_range%, dll
│  ├─ DenizenBridge.java, SkriptBridge.java
│  └─ PublicApi.java                 # facade untuk plugin lain (setRange, broadcast, privateChannel, status, reset)
└─ plugins/impl/  (implementasi VoicechatServerApi/audio channel/audio player agar API SVC tetap jalan untuk addon)
```

---

## 4. Async & performance design

- **Virtual threads (Java 25):** satu `Executors.newVirtualThreadPerTaskExecutor()` untuk packet processing & DSP per speaker. UDP reader = single thread (blocking queue), lalu `submit()` ke virtual-thread pool.
- **Non-blocking UDP:** `DatagramChannel` blocking + queue (sama kayak SVC, terbukti OK) atau NIO selector untuk throughput tinggi. Default: blocking + queue (sederhana,可靠).
- **DSP pipeline async per active speaker:** MicPacket → decode opus (pooled OpusDecoder) → LoudnessMeter → NoiseSuppression (opt-in) → SoundPhysics (raytrace, cached per tick) → encode opus (pooled OpusEncoder) → broadcast. Semua di virtual thread; broadcast send tetap UDP non-blocking.
- **Raytrace cache:** hasil occlusion per pasangan (sender,receiver) di-cache 1 tick (50ms) karena posisi pemain gak berubah tiap frame 20ms secara berarti. Raytrace pakai Bukkit `World#rayTraceBlocks` (Paper) — async-safe untuk baca world snapshot.
- **Opus decoder/encoder pool:** jangan bikin per packet (mahal). Pool per thread via ThreadLocal atau queue.
- **TTL & drop:** pertahankan TTL 500ms MicPacket, 10s sound; drop paket kedaluwarsa + log cooldown.
- **Rate limit:** pertahankan `PacketRateLimiter` per UUID.

---

## 5. Sound physics (raytrace) — detail

Untuk setiap pasangan speaker→listener per frame:
1. **Raytrace occlusion (apakah tertutup?):** cast ray dari `speaker.eyePosition` ke `listener.eyePosition`. Jika kena solid block → `occluded = true`. Hitung jumlah block solid di sepanjang ray untuk tebal penutup.
2. **Audibility gate (apakah terdengar?):** jika `occluded && tebal > maxOcclusion` → skip kirim paket ke listener (tidak terdengar). Jika jarak > distance efektif → skip.
3. **Muffle/damp (apakah mendam?):** tiap block solid yang ditembus → naikkan `effectiveDistance` sebesar `occlusionPenaltyPerBlock * blockThickness` (clamped). Efeknya di client: suara lebih pelan/mendam karena distance lebih besar. Tier 2 opt-in: ganti dengan low-pass filter pada PCM.
4. **Reverb/echo (apakah bergema?):** deteksi listener berada di volume tertutup (hitung density block di radius `reverbProbeRadius`). Jika enclosed → Tier 1: tambah sedikit `effectiveDistance` penalty; Tier 2 opt-in: convolution reverb pada PCM.
5. **Clarity (apakah jelas?):** Line-of-sight penuh + jarak dekat → tidak ada penalty. Clarity score = `1 - occlusionFactor` (dipakai Tier 2 untuk mix dry/wet).

Output akhir: `effectiveDistance` dimasukkan ke field `distance` `PlayerSoundPacket` (Tier 1) atau PCM DSP bake lalu kirim `LocationalSoundPacket`/static (Tier 2).

Config: `sound_physics.enabled`, `raytrace`, `max_occlusion_blocks`, `occlusion_penalty_per_block`, `reverb_probe_radius`, `tier2_pcm_dsp` (opt-in), `cache_ticks`.

---

## 6. Voice range dinamis (bisik → teriak)

`MicPacket` hanya bawa `whispering: bool`. Untuk range dinamis kontinu:
1. Decode opus → `short[960]`.
2. Hitung RMS → dB. `AudioUtils.getHighestAudioLevel` udah ada; pakai RMS lebih stabil.
3. Map dB → distance: `distance = lerp(whisperRange, shoutRange, clamp((db - whisperDb) / (shoutDb - whisperDb), 0, 1))`. Untuk "teriak jauh" boleh melebihi `voice_range` sampai `shout_range` (dengan permission).
4. Override `whispering` bool: jika distance < threshold → `true` (supaya icon whisper client muncul), else `false`.
5. Field `distance` di `PlayerSoundPacket` diisi hasil ini (bukan konstan).

Config: `voice_range` (normal), `whisper_range`, `shout_range`, `shout_permission`, `loudness_whisper_db`, `loudness_shout_db`, `dynamic_range_enabled`.

Per-player override via API/command: `setVoiceRange(uuid, range)` menimpa default; `shout_range` tetap batas atas global.

---

## 7. Audio playback (mp3/ogg/wav) + command

Command:
```
/astolfo play player <player> <file>        # kirim ke player spesifik (static channel, infinite range)
/astolfo play world <world> <file>          # semua player di world itu (locational atau static)
/astolfo play all <file>                    # semua player online
/astolfo play location <world> <x> <y> <z> <file> <distance>   # locational
/astolfo play group <groupName> <file>      # ke grup SVC
/astolfo stop [player|world|all|id]
/astolfo list                               # list file di folder audio
```
- Folder: `plugins/AstolfoVoice/audio/` (dibuat otomatis, ada `README.txt` penjelasan).
- Format: mp3 (JLayer), ogg (JOrbis/Tritonus), wav (javax.sound.sampled). Resample → 48k mono short[].
- Streaming: bungkus `AudioPlayer` API (supplier `short[960]` per 20ms). Pakai `OpusEncoder` VOIP/AUDIO.
- Quality: decode pada sample rate asli → resampler kualitas tinggi (Lanczos/Sinc) → 48k. Normalize peak optional.
- Volume & distance bisa di-set per playback.

Config: `audio.enabled`, `audio.default_volume`, `audio.default_distance`, `audio.max_concurrent_playbacks`, `audio.resample_quality` (LOW/MEDIUM/HIGH), `audio.formats` (mp3,ogg,wav).

---

## 8. PlaceholderAPI + Denizen/Skript + Public API

`PublicApi` (di module `api/`, di-publish ke Maven local + bundled):
```
AstolfoApi.get().setVoiceRange(UUID player, double range)
AstolfoApi.get().getVoiceRange(UUID player)
AstolfoApi.get().broadcastAll(short[]/file)
AstolfoApi.get().broadcastWorld(String world, ...)
AstolfoApi.get().playToPlayer(UUID player, String file)
AstolfoApi.get().createPrivateChannel(List<UUID> members, boolean audibleNearby, double range /* -1 = infinite */)
AstolfoApi.get().getPlayerStatus(UUID) -> {connected, muted, whispering, range, group}
AstolfoApi.get().resetPlayer(UUID)  // reset range/status ke default
AstolfoApi.get().registerEventListener(...)
```
- PrivateChannel: anggota saling dengar; `audibleNearby=false` → hanya anggota (pakai category SVC + distance = -1 / pakai static channel per listener); `audibleNearby=true` → anggota + orang sekitar dalam `range`.
- PlaceholderAPI: `%astolfo_connected%`, `%astolfo_range%`, `%astolfo_status%`, `%astolfo_group%`, `%astolfo_muted%`.
- Denizen: `astolfo_voice_range`/`astolfo_broadcast` via custom event/bridge. Skript: lewat PublicApi + small Skript addon (terpisah).

---

## 9. Roadmap fase

**Fase 1 — Kompatibilitas dasar (MVP)**
- Replikasi persis: plugin channel (handshake+state), UDP server, semua 10 tipe paket, Secret AES-GCM, keepalive, proximity broadcast standar. Target: client SVC connect + ngobrol biasa jalan.
- config.yml + plugin.yml + folder audio.

**Fase 2 — Audio playback & command**
- Decoder mp3/ogg/wav + StreamingAudioPlayer + `/astolfo play ...` + `/astolfo stop/list`.

**Fase 3 — Voice range dinamis + sound physics Tier 1**
- LoudnessMeter → distance dinamis; raytrace occlusion + audibility gate + muffle via distance.

**Fase 4 — Noise cancellation + Tier 2 DSP**
- RNNoise server-side (opt-in) + low-pass/reverb PCM bake (opt-in).

**Fase 5 — API publik + integrasi**
- PublicApi jar, PlaceholderAPI, Denizen/Skript bridge, private channel.

**Fase 6 — Multi-versi client adapter + polish**
- VersionedPacketRegistry, adapter legacy, benchmark, profile.

---

## 10. Dependencies (rekomendasi)
- Paper API 1.21+, Bukkit
- Concentrus / Opus4J (Opus), RNNoise4J (noise), JLayer (mp3), JOrbis (ogg), javax.sound (wav)
- PlaceholderAPI (soft)
- Shadow plugin untuk fat jar
- Java 25 toolchain (virtual threads)

---

## 11. Catatan etis/legal
- SVC berlisensi GPL-3.0. Fork wajib tetap GPL-3.0 + preserve copyright notice. Protokol direimplementasi bersih; sebut kompatibilitas, bukan klaim afiliasi.
- "Kompatibel semua versi client" = goal aspirasional; mekanisme riil = negosiasi `compatibilityVersion` per koneksi + adapter per versi. Default jalan di versi terbaru dulu.
