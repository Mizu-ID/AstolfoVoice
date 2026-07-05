<table width="100%"><tr>
<td width="64" valign="middle" align="center">
  <img src="../../astolfo.png" alt="Astolfo" width="54" style="border-radius:11px; box-shadow:0 0 0 2px #ffb6c1, 0 4px 12px rgba(255,92,168,.25);" />
</td>
<td valign="middle">

# AstolfoVoice - Implementation Plan <sub style="color:#d8b4e2">fokus Bukkit/Paper</sub>

</td>
</tr></table>

&nbsp;
> Fork/reimplementasi modern dari Simple Voice Chat (https://github.com/henkelmax/simple-voice-chat),
> **khusus platform Bukkit/Spigot/Paper**. Protokol UDP + handshake **identik & kompatibel** dengan
> client Simple Voice Chat biasa — pemain tidak perlu ganti mod.

Dokumen pendamping: `docs/PROTOCOL_REFERENCE.md` (wire format lengkap, verbatim dari sumber SVC).

---

## A. Prinsip desain & non-goals (jujur dulu)

### Goals
- Drop-in compatible: client SVC standar connect tanpa mod tambahan, tanpa konfigurasi client.
- Lebih efektif & modern di sisi server: async penuh, virtual threads, sound physics raytrace, range dinamis, playback, API publik.
- Single fat-jar untuk Paper 1.21+ (target utama) dengan fallback graceful Spigot.

### Non-goals (diakui supaya tidak menyesatkan)
- **Bukan** mod client. Semua fitur "spesial" (muffle/reverb "asli") dibatasi oleh mixer OpenAL client SVC yang fixed. Lihat §G untuk batas teknis eksak.
- **Bukan** support 1.8–1.20 via reflection NMS penuh seperti SVC. Target native Paper 1.21+. Fallback Spigot = fitur dasar saja (raytrace nonaktif, message non-translatable). Alasan: reflection-per-version adalah sumber bug terbesar SVC dan tidak "modern".
- **Bukan** ganti transport. UDP + plugin-channel dipertahankan persis. "Modern" di lapisan aplikasi, bukan lapisan wire.

### Pernyataan jujur tentang "kompatibel semua versi client"
Mekanisme riil = negosiasi `compatibilityVersion` (int) di `RequestSecretPacket` per koneksi. Server SVC saat ini = 20. Untuk AstolfoVoice: **default target = compat 20** (implementasi penuh). Versi client lain ditangani via `VersionedPacketRegistry` + adapter. Catatan: dukung "semua versi" berarti memelihara adapter per versi seiring SVC berubah; bukan janji otomatis. Plan menetapkan kerangka adapter, implementasi per versi menyusul bertahap mulai dari versi terbaru.

---

## B. Arsitektur teknis — Bukkit-native, NMS-free

### B.1 Lapisan transport (dipertahankan persis dari SVC)
Dua jalur, keduanya pakai API publik Bukkit — **tanpa reflection NMS**:
1. **Plugin channels (TCP via Bukkit Messenger)** — handshake + state. Channel namespace WAJIB `voicechat:*` (bukan `astolfo:*`) karena client SVC mengirim ke sana. Registrasi via `Bukkit.getMessenger().registerIncomingPluginChannel/registerOutgoingPluginChannel` + `player.sendPluginMessage`.
2. **Raw UDP** — audio + auth. `DatagramSocket` murni (blocking) + antrian. Tidak ada NMS.

Catatan teknis: `FriendlyByteBuf` SVC adalah thin wrapper netty `ByteBuf` (VarInt, UUID, UTF). AstolfoVoice re-implementasi tipis di atas netty buffer (netty adalah dependency transitif Paper) atau di atas `ByteBuffer` murni. Keputusan: pakai netty `Unpooled` (sudah ada di classpath Paper) untuk kompatibilitas byte-exact dengan SVC.

### B.2 Lapisan NMS/compatibility — minim
SVC pakai `BukkitCompatibilityManager` + ~20 class reflection per versi (1.8–1.21.5) untuk: register custom packet channel, translatable chat component, `addChannel`/`removeChannel`, player hide/show event.

AstolfoVoice **mengganti** lapisan ini:
- Channel registration → Bukkit Messenger (publik, semua versi).
- Translatable message → Adventure `Component.translatable` (Paper 1.21+ native). Fallback Spigot → plain string.
- Raytrace → Paper API `World#rayTraceBlocks(Location, Vector, double, FluidCollisionMode, boolean)` (publik, no NMS).
- Player visibility → event `PlayerShowEvent`/`PlayerHideEvent` hanya ada di Paper; fallback = skip (semua saling terlihat).

Hasil: 1 class `PaperCompat` + 1 class `SpigotFallbackCompat`, bukan 20 class reflection. Trade-off: fitur penuh hanya Paper 1.21+.

### B.3 Struktur module (Gradle multiproject, ringkas dari SVC)
```
astolfovoice/
├─ api/         # API publik (AstolfoApi) untuk plugin lain / Denizen / Skript
├─ common/      # protokol UDP, packet, crypto (Secret AES-GCM), audio utils, DSP
├─ bukkit/      # implementasi Bukkit/Paper + lifecycle + commands + integrasi
└─ build.gradle, settings.gradle, gradle/
```
Target toolchain Java 25 (virtual threads stable). Fat-jar tunggal `AstolfoVoice.jar` via shadow.

---

## C. Lifecycle plugin yang benar (Bukkit)

Urutan `onEnable` yang aman (pelajari dari SVC `Voicechat.onEnable`):
1. Set `INSTANCE`, init logger.
2. Load `AstolfoConfig` dari `plugins/AstolfoVoice/config.yml` (SnakeYAML, bawaan Bukkit).
3. Auto-buat folder: `getDataFolder()`, `getDataFolder()/audio/`, copy default `config.yml` + `audio/README.txt` via `saveResource(...)`.
4. Inisialisasi `Compatibility` (deteksi Paper vs Spigot via `Bukkit.getVersion()` / class check `PaperAdventure`).
5. `NetManager.onEnable()` — registrasi semua plugin channel (§D). WAJIB sebelum player join handler.
6. Register `AstolfoVoice` sebagai listener (`PlayerJoinEvent`, `PlayerQuitEvent`, `PlayerWorldChangeEvent`, `PlayerToggleSneakEvent` untuk whisper, dll).
7. Register service `AstolfoApi` via `ServicesManager` (priority Normal) supaya plugin lain bisa `Bukkit.getServicesManager().load(AstolfoApi.class)`.
8. Register command `/astolfo` (brigadier via Paper jika ada, fallback bukkit `CommandExecutor`).
9. Soft-hook: PlaceholderAPI, Denizen, Skript (cek `Bukkit.getPluginManager().getPlugin(...)`).
10. **Schedule UDP server start di task sync pertama** (`Bukkit.getScheduler().runTask(this, () -> voiceServer.start())`) — karena beberapa operasi butuh main thread sudah siap. SVC melakukan pola sama via `compatibility.runTask(...)`.

`onDisable` (urutan penting, anti deadlock):
1. Stop semua `StreamingAudioPlayer` (interrupt + join timeout).
2. `voiceServer.close()` → tutup `DatagramSocket` (membebaskan reader thread), stop process pool.
3. Unregister semua plugin channel (`Bukkit.getMessenger().unregisterIncoming/OutgoingPluginChannel`).
4. Unregister service + listeners.
5. Shutdown executor pools (virtual thread executor: `shutdown()` + `awaitTermination(5s)`).

**Reload:** tidak didukung penuh (UDP socket + secret map sulit dipulihkan bersih). Tampilkan warning seperti SVC. `/astolfo reload` hanya reload config (tidak restart socket kecuali port berubah).

---

## D. Plugin channels — registrasi persis

Channel names (namespace `voicechat`, verbatim dari SVC `NetManager`):
| Arah      | Channel                          | Class (AstolfoVoice)         |
|-----------|----------------------------------|------------------------------|
| Incoming  | `voicechat:request_secret`       | `RequestSecretPacket`        |
| Incoming  | `voicechat:update_state`         | `UpdateStatePacket`          |
| Incoming  | `voicechat:create_group`         | `CreateGroupPacket`          |
| Incoming  | `voicechat:join_group`           | `JoinGroupPacket`            |
| Incoming  | `voicechat:leave_group`          | `LeaveGroupPacket`           |
| Outgoing  | `voicechat:secret`               | `SecretPacket`               |
| Outgoing  | `voicechat:states`               | `PlayerStatesPacket`         |
| Outgoing  | `voicechat:state`                | `PlayerStatePacket`          |
| Outgoing  | `voicechat:remove_state`         | `RemovePlayerStatePacket`    |
| Outgoing  | `voicechat:add_group`            | `AddGroupPacket`             |
| Outgoing  | `voicechat:remove_group`         | `RemoveGroupPacket`          |
| Outgoing  | `voicechat:joined_group`         | `JoinedGroupPacket`          |
| Outgoing  | `voicechat:add_category`         | `AddCategoryPacket`          |
| Outgoing  | `voicechat:remove_category`      | `RemoveCategoryPacket`       |

`Key` = `new NamespacedKey(plugin, "name")`. Format biner setiap packet = replica `toBytes/fromBytes` SVC (lihat `docs/PROTOCOL_REFERENCE.md`).

Pada `PlayerJoinEvent`: untuk tiap channel outgoing, kirim "register" via `player.sendPluginMessage(plugin, "minecraft:register", channelNames\n-joined bytes)`. SVC lakukan ini via `compatibility.addChannel`. Di Paper modern cukup `player.addChannel(channel)` (kalau exposed) atau pattern register. Rate-limit incoming per UUID (`PacketRateLimiter`, default 16/s).

---

## E. Concurrency & thread safety (detail teknis)

Aturan keras di Bukkit: **akses Bukkit API yang mutasi state (world, entity, player telepathy) harus di main thread**. **Baca read-only** (posisi player, world name, location) umumnya thread-safe di Paper untuk snapshot, tapi raytrace resmi main-thread. UDP send/recv aman async.

### E.1 Thread model
- **Main thread (Bukkit scheduler):** listener event, command, state broadcast ke plugin channel (`sendPluginMessage` — harus main thread aman; SVC bungkus `compatibility.runTask`).
- **UDP reader thread** (1, daemon): blocking `DatagramSocket.receive` → enqueue `RawUdpPacket` ke `LinkedBlockingQueue`.
- **Packet processor:** virtual thread per packet (Java 25 `newVirtualThreadPerTaskExecutor`). Tiap packet: decrypt → dispatch. Handshake/keepalive ringan; MicPacket → submit ke DSP.
- **DSP worker pool:** virtual thread per active speaker per frame. Decode → loudness → noise (opt-in) → physics → encode → enqueue broadcast.
- **Broadcast sender:** UDP send non-blocking; bisa dari thread apa pun (socket.send thread-safe).

### E.2 Kenapa virtual thread, bukan fixed pool
UDP audio = bursty, banyak task pendek (20ms). Virtual thread murah, tidak membebani saat idle, auto-scale. Fixed pool bisa jadi bottleneck saat semua speaker aktif. Catatan: virtual thread GA sejak Java 21; target Java 25 aman. Fallback platform thread pool jika `performance.virtual_threads=false`.

### E.3 Sinkronisasi
- `connections`, `unCheckedConnections`, `secrets`, `clientCompatibilities` → `ConcurrentHashMap` (sama SVC).
- `packetQueue` → `LinkedBlockingQueue` (blocking, bounded optional).
- Opus encoder/decoder → **per-virtual-thread instance** via `ThreadLocal<OpusEncoder>` (encoder tidak thread-safe; recreate mahal). Pool cap `opus_pool_size` untuk bounding memori.
- Raytrace cache → `ConcurrentHashMap<Pair<UUID,UUID>, CachedOcclusion>` dengan TTL tick; diisi dari main-thread tick task, dibaca DSP worker (read-only).

### E.4 Main-thread bridge untuk raytrace
Raytrace `World#rayTraceBlocks` ideal main thread. Solusi: **tick task (sync, setiap 1 tick = 50ms)** yang pre-compute occlusion untuk semua pasangan speaker→listener aktif, simpan ke cache. DSP worker (async) hanya baca cache. Ini memenuhi thread-safety Bukkit + tetap async untuk path berat (decode/encode). Trade-off: occlusion lag max 50ms — acceptable.

Alternatif Paper: `World#rayTraceBlocks` dokumentasi Paper menyatakan dapat dipanggil dari thread apapun untuk world snapshot baca. Kalau terbukti aman → skip main-thread bridge, raytrace langsung async (lebih cepat). Flag config `sound_physics.async_raytrace` (default false = main-thread cache; true = async langsung, uji dulu).

### E.5 TTL & drop
- MicPacket TTL 500ms (verifiklebihan/buru-buru), sound packet TTL 10s. Drop di processor jika `now - timestamp > TTL` + log cooldown (`overload_log_cooldown_ms`). Default `drop_expired_packets=true`.

---

## F. Handshake — state machine lengkap

State per `playerUUID` (di `ServerVoiceEvents` + `Server`):

```
[LOGGED_IN]
   │ RequestSecretPacket (compatVersion)  ← plugin channel
   ▼
[SECRET_SENT]  clientCompatibilities[uuid]=compat
   │   if compat != server compat → kirim pesan incompatible (untuk compat<=6: plain string; >=7: translatable)
   │                                tetap generate secret (client lama mungkin tetap connect)
   │   generateNewSecret(uuid) → SecretPacket(secret, port, uuid, codec, mtu, distance, keepalive, groups, host, allowRecording)
   ▼
[AWAITING_UDP_AUTH]
   │ AuthenticatePacket(uuid, secret)  ← UDP (encrypted: magic+uuid+payload)
   │   server cek secrets[uuid].equals(packet.secret)
   │   buat ClientConnection(uuid, address) → unCheckedConnections
   │   balas AuthenticateAckPacket (UDP)
   ▼
[UNCHECKED]
   │ ConnectionCheckPacket  ← UDP
   │   pindah unCheckedConnections → connections
   │   fire PlayerConnectedEvent (API)
   │   balas ConnectionCheckAckPacket
   ▼
[CONNECTED]
   │ MicPacket → onMicPacket → processProximity/processGroup
   │ KeepAlivePacket → update lastKeepAliveResponse
   │ PingPacket → pingManager.onPongPacket
   │ keepalive loop: kirim KeepAlivePacket tiap keep_alive ms
   │   jika lastKeepAliveResponse > keep_alive*10 → remove secret + reconnect (kirim SecretPacket baru)
   ▼ (quit/timeout)
[DISCONNECTED]  disconnectClient(uuid): remove connections/secrets, fire PlayerDisconnectedEvent
```

Detail penting dari sumber:
- Secret 16 byte AES-128-GCM, IV 12 byte, tag 128 bit, `AES/GCM/NoPadding`.
- `generateNewSecret` hanya kembalikan secret jika belum ada (anti double-request).
- UDP decrypt gagal → return null (silent, debug log) — jangan throw.
- Magic byte `0xFF` di awal; UUID player di luar enkripsi (sebagai routing key); payload terenkripsi.
- `MAX_VOICE_CHAT_PACKET_SIZE = 2048`, `MAX_OPUS_PAYLOAD_SIZE = 1275`, UDP buffer 4096.
- Spectator: `processProximityPacket` cek `sender.isSpectator()` → location sound atau spectator-possession group sound.

---

## G. Sound physics (raytrace) — detail implementasi Paper

Tier 1 (default, murah) vs Tier 2 (opt-in, PCM DSP bake). Lihat §A non-goal tentang batas mixer client.

### G.1 Tier 1 — occlusion gate + distance modulation
Per pasangan (speaker S, listener L) per frame, di main-thread tick task (atau async bila `async_raytrace`):
1. **Ray cast** `S.eyeLocation → L.eyeLocation`. Pakai `World#rayTraceBlocks(start, dir, maxDist, FluidCollisionMode.NEVER, ignorePassableBlocks=true)`. `RayTraceResult` → hit block + jarak.
2. **Line of sight (apakah jelas?)** `los = (result == null || result.getHitBlock() == null)`.
3. **Occlusion tebal (apakah tertutup?)** Jika kena block, hitung tebal: untuk dinding tebal pakai bounding box overlap sepanjang ray. Pendekatan ekonomis: tiap block solid di sepanjang ray = +1. Iterasi step 1 block sepanjang ray (max `max_occlusion_blocks`) cek `block.getType().isOccluding()`.
4. **Audibility gate (apakah terdengar?)** `audible = distance <= effectiveDistance && occlusionCount <= max_occlusion_blocks`. Jika tidak audible → **skip kirim paket** ke L (server-side gate, hemat bandwidth). Catatan: client SVC akan fade-out natural bila paket berhenti.
5. **Muffle/damp (apakah mendam?)** `effectiveDistance = baseDistance + occlusionCount * occlusion_penalty_per_block`. Field `distance` di `PlayerSoundPacket` diisi ini → di client attenuation terlihat lebih pelan/mendam.
6. **Reverb/enclosed (apakah bergema?)** Probe: hitung density block occluding di box `±reverb_probe_radius` sekitar L. `enclosed = density > threshold`. Tier 1: jika enclosed → tambah penalty kecil `+ reverb_penalty` ke distance (simulasi). Tier 2: bake reverb pada PCM.

Output: `PlayerSoundPacket(channelId=sender, sender, opus, seq, whispering, effectiveDistance, category)`.

### G.2 Tier 2 — PCM DSP bake (opt-in, per-world `tier2_worlds`)
Path berat (latency + CPU), hanya jika config aktif:
1. Decode opus `packet.data` → `short[960]` mono.
2. **Muffle:** low-pass FIR/IIR cutoff `muffle_lowpass_hz` (default 1200) jika occluded.
3. **Reverb:** convolution dengan IR singkat (atau feedback delay network) gain `reverb_gain`, decay `reverb_decay_seconds` jika enclosed.
4. **Noise suppression:** RNNoise pada PCM (§H).
5. Re-encode opus → kirim. Tipe paket: untuk lokasi → `LocationSoundPacket`; untuk broadcast non-locational → `StaticSoundPacket` per listener (atau `PlayerSoundPacket` dengan distance besar). Catatan: re-encode menambah 1 generasi loss — kualitas turun sedikit; trade-off "bergema beneran".

### G.3 Bypass
`bypass_for_groups=true`: group chat skip physics (jernih, seperti SVC). Spectator possession skip physics.

### G.4 Config relevan
`sound_physics.{enabled, tier, raytrace, async_raytrace, max_occlusion_blocks, occlusion_penalty_per_block, reverb_probe_radius, reverb_penalty, cache_ticks, muffle_lowpass_hz, reverb_gain, reverb_decay_seconds, tier2_worlds, bypass_for_groups}`.

---

## H. Noise cancellation server-side

SVC client sudah punya RNNoise bawaan. Server-side NC = opsional ganda (hemat jika client sudah pakai).

- Engine `RNNOISE` via RNNoise4J (native lib per OS/arch). **Wajib handling load**: cek native lib availability saat onEnable; jika gagal → log warning + auto-disable `noise_cancellation.enabled` (jangan crash plugin).
- Hanya proses frame dengan RMS > `silent_threshold_db` (`skip_silent_frames`) untuk hemat CPU.
- Placement: di DSP pipeline, setelah decode, sebelum physics re-encode (Tier 2) atau sebelum broadcast (Tier 1 jika ingin NC tanpa DSP bake — tapi Tier 1 tidak re-encode, jadi NC hanya bermakna di Tier 2). **Konsekuensi:** NC server-side efektif hanya bila Tier 2 aktif (karena butuh re-encode). Jika hanya Tier 1, NC client bawaan yang dipakai — dokumentasikan jelas.
- Fallback engine `SPEEX` (SpeexDSP preprocessor: noise suppression + AGC) jika RNNOISE native gagal — pure Java lebih portabel.

Config: `noise_cancellation.{enabled, engine (RNNOISE|SPEEX|NONE), skip_silent_frames, silent_threshold_db}`. Tambah `load_warning` runtime check.

---

## I. Voice range dinamis (bisik → teriak)

`MicPacket` hanya bawa `whispering: bool`. Range kontinu butuh decode + RMS.

### I.1 Math
- Decode opus → `short[960]` (di DSP worker).
- RMS dB: `db = 20*log10(rms/32768)`, `rms = sqrt(mean(sample^2))`. (Lebih stabil dari peak `getHighestAudioLevel`.)
- Normalize: `t = clamp((db - loudness_whisper_db) / (loudness_shout_db - loudness_whisper_db), 0, 1)`.
- Distance: `distance = lerp(whisper_range, shout_range, t)`. Tapi normal range = `voice_range`. Mapping sebenarnya:
  - `t < 0.5` → `lerp(whisper_range, voice_range, t*2)` (bisik→normal)
  - `t >= 0.5` → `lerp(voice_range, shout_range, (t-0.5)*2)` (normal→teriak), butuh permission `astolfo.shout` untuk t>0.5 (tanpa izin clamp ke voice_range).
- Whisper icon: set `whispering=true` jika `t < whisperIconThreshold` (misal distance < whisper_range*1.5) supaya icon bisik client muncul.
- Field `distance` di `PlayerSoundPacket` diisi `distance` ini. Base distance dynamic, lalu SoundPhysics G.5 menambah occlusion penalty di atasnya.

### I.2 Per-player override
`setVoiceRange(uuid, range)` menimpa `voice_range` (clamp `max_player_range_override`). `shout_range` tetap batas atas global (tidak bisa di-override lebih besar kecuali permission).

### I.3 Edge: VAD gagal / frame silent
Jika RMS sangat rendah (< silent_threshold) → skip broadcast (hemat). Tapi `whispering=true` mic packet tetap mungkin berisi napas — broadcast dengan `whisper_range` jika di atas threshold, else skip.

Config: `voice_range.{voice_range, whisper_range, shout_range, dynamic_range_enabled, loudness_whisper_db, loudness_shout_db, broadcast_range, max_player_range_override}` + `shout_permission`.

---

## J. Audio playback (mp3/ogg/wav) + command

### J.1 Command tree (perintah utama `/astolfo`, alias `av`)
```
/astolfo play player <player> <file> [volume]
/astolfo play world <world> <file> [volume]
/astolfo play all <file> [volume]
/astolfo play location <world> <x> <y> <z> <file> [distance] [volume]
/astolfo play group <groupName> <file> [volume]
/astolfo stop [player|world|all|id]
/astolfo list
/astolfo voicerange <player> [range]      # get/set override
/astolfo broadcast <player|world|all> <file>   # alias play (ke placeholder API)
/astolfo status [player]
/astolfo reset <player>                   # reset range/status override
/astolfo reload                           # reload config (no socket restart)
```
Contoh permintaan user: `/astolfo play player agus magis1 atp` → `player` lalu nama player `agus`, file `magis1.atp`? Catatan: `.atp` bukan format audio standar — didukung mp3/ogg/wav. Aku asumsikan `magis1` adalah file (misal `magis1.mp3`). Argumen: `/astolfo play player agus magis1.mp3`.

### J.2 Pipeline playback
1. Resolve file di `plugins/AstolfoVoice/audio/` (case-insensitive, boleh tanpa ekstensi → cari mp3/ogg/wav).
2. Decode format:
   - mp3 → JLayer (`MP3AudioFileReader` + `FrameDecoder`) → PCM 16-bit.
   - ogg → JOrbis/VorbisJava → PCM.
   - wav → `javax.sound.sampled.AudioSystem.getAudioInputStream` → PCM.
3. Resample ke 48kHz mono: kualitas `resample_quality` (LOW=linear, MEDIUM=lanczos-8, HIGH=lanczos/sinc-16). Pakai library resampler atau implementasi lanczos ringan.
4. Normalize peak (optional, `audio.normalize`) agar tidak clipping.
5. Streaming: bungkus API AudioPlayer SVC-style — supplier `short[960]` per 20ms, `OpusEncoder` mode AUDIO, channel:
   - `play player` → `StaticAudioChannel` per target (infinite range, hanya listener itu).
   - `play world` → loop player di world, `LocationalAudioChannel` di posisi player atau `StaticAudioChannel` per player.
   - `play all` → semua player online, static per player.
   - `play location` → `LocationalAudioChannel` di koordinat dengan `distance`.
   - `play group` → kirim ke anggota group (group sound path).
6. Volume: aplikasi gain linear pada PCM sebelum encode.
7. Concurrency: `max_concurrent_playbacks` global; tiap playback jalan di virtual thread dengan pacing 20ms (sama `AudioPlayerImpl.run` SVC).

### J.3 Stop & list
`stop` interrupt streaming player + channel flush. `list` scan folder + tampilkan nama file.

Config: `audio.{enabled, folder, formats, default_volume, default_distance, max_concurrent_playbacks, resample_quality, normalize}`.

---

## K. API publik + integrasi

### K.1 `AstolfoApi` (module `api`, published + bundled)
```java
interface AstolfoApi {
  // range
  void setVoiceRange(UUID player, double range);
  double getVoiceRange(UUID player);
  void resetPlayer(UUID player);                 // reset range + status override

  // status
  VoiceStatus getPlayerStatus(UUID player);      // connected, muted, whispering, range, group, world

  // playback (file name atau short[] PCM 48k mono)
  PlaybackHandle playToPlayer(UUID player, String file, PlaybackOptions opts);
  PlaybackHandle broadcastWorld(String world, String file, PlaybackOptions opts);
  PlaybackHandle broadcastAll(String file, PlaybackOptions opts);
  PlaybackHandle playAtLocation(String world, double x,double y,double z, String file, PlaybackOptions opts);
  PlaybackHandle playToGroup(String group, String file, PlaybackOptions opts);
  void stopPlayback(PlaybackHandle handle);

  // private channel (untuk skenario user: player 1 & 2 bicara, nearby tidak dengar)
  PrivateChannel createPrivateChannel(List<UUID> members, PrivateChannelOptions opts);
  // opts: audibleNearby (bool), range (double, -1 = tak terbatas), includeOutsidersWithinRange

  // events
  void registerListener(AstolfoListener listener);
  void unregisterListener(AstolfoListener listener);
}
```
`PrivateChannelOptions.audibleNearby=false, range=-1` = anggota saling dengar, luar tidak (implementasi: category SVC unik + static channel per member; paket tidak di-broadcast proximity). `audibleNearby=true, range=50` = anggota + orang dalam 50 block dengar.

### K.2 PlaceholderAPI
`%astolfo_connected%`, `%astolfo_range%`, `%astolfo_status%`, `%astolfo_group%`, `%astolfo_muted%`, `%astolfo_world%`, `%astolfo_shout%`. Ekspansi = class extends `PlaceholderExpansion` (soft-depend).

### K.3 Denizen / Skript
- Denizen: register custom event/property via `DenizenAPI` (soft). Bridge expose `astolfo_voice_range`, `astolfo_broadcast`, `astolfo_private_channel`.
- Skript: PublicApi accessible via small Skript addon (terpisah) atau via `Bukkit.getServicesManager().load(AstolfoApi.class)` dari Java dalam Skript effect. Documented pattern.

Config: `integration.{placeholderapi, denizen, skript, public_api}`.

---

## L. Edge cases & failure modes (matang = memikirkan ini)

1. **Port UDP sudah dipakai** → `BindException` → log fatal + `disablePlugin` (tidak crash server). Sama SVC.
2. **Player quit saat handshake** → `disconnectClient` di `PlayerQuitEvent` + `clientCompatibilities.remove`.
3. **Player teleport cross-world / dimension** → proximity resolver re-evaluate per frame (tidak cache world). Broadcast hanya same-world.
4. **Spectator** → location sound di eye posisi spectator (jika `spectator_interaction`) atau possession ke target. Physics bypass untuk possession.
5. **Packet replay / seq** → MicPacket seq naik; tidak ada anti-replay server-side di SVC (stateless). Tetap pertahankan; Tambah rate limit + drop out-of-order tua (TTL).
6. **Unverified UDP packet** (uuid tanpa secret, atau decrypt gagal) → silent drop (debug log). Jangan reveal info.
7. **Reload plugin** → warning; config reload tanpa restart socket (kecuali port/MTU berubah → butuh restart socket, tampilkan instruksi).
8. **Native RNNoise gagal load** → auto-disable NC, log, lanjut jalan.
9. **Opus decode error** (corrupt) → drop frame, log cooldown, jangan crash worker.
10. **DSP overload** (queue penuh) → drop frame lama (TTL), log overload cooldown. Pertahankan latency rendah > kelengkapan.
11. **Player tanpa mod SVC** → tidak pernah kirim `RequestSecretPacket`; tetap di `clientCompatibilities` default -1; jika `force_voice_chat` → kick setelah `login_timeout`.
12. **World unload saat playback** → cancel playback, log.
13. **Audio file hilang saat play** → error message ke sender, no-op.
14. **Concurrent `/astolfo play all` besar** → batas `max_concurrent_playbacks`; queue atau tolak dengan pesan.
15. **Offline mode** → warning "encryption not secure" (sama SVC); secret tetap random per session.
16. **Cross-version client** → adapter per compat; default 20; unknown → `reject_unknown_version` config (default false = coba fallback).

---

## M. Build & dependencies

Gradle multiproject (Kotlin DSL opsional, Groovy default):
- `api/` — publish ke maven local + bundled. Dep: annotation-only.
- `common/` — netty-buffer (transitif Paper), Concentrus/Opus4J (Opus pure-Java), RNNoise4J (native, optional), SpeexDSP4J (fallback), JLayer (mp3), JOrbis (ogg), javax.sound (wav, JDK).
- `bukkit/` — Paper API `1.21.x` (paperweight userdev opsional, atau plain `spigot-api`), PlaceholderAPI (soft, Maven), shadow plugin.

Native lib packaging: bundle RNNoise native untuk linux-x86_64, linux-aarch64, windows-x86_64, macos di fat jar `/natives/`; load via temp extract. Fallback Speex pure-Java jika gagal.

Toolchain Java 25. `shadowJar` output `AstolfoVoice-<version>.jar`. `plugin.yml` `api-version: '1.21'`.

---

## N. Testing strategy

- **Unit:** `Secret` encrypt/decrypt roundtrip; `NetworkMessage` serialize semua 10 packet byte-exact vs SVC (regression fixtures dari SVC bytes); `AudioUtils` short/float/byte; `LoudnessMeter` RMS→db→distance curve; resampler output length.
- **Wire compat:** record real client SVC handshake bytes (from a test server) → replay via UDP → assert server parse + ack. Comparator byte-for-byte `SecretPacket`/`PlayerStatePacket`.
- **Bukkit integration:** test server Paper 1.21, dua player mock, assert proximity broadcast count, occlusion skip, range dynamic. Use `MockBukkit` atau Paper test server.
- **Performance:** 50 speaker × 50 listener, profile DSP latency p99 < 5ms (Tier 1), < 25ms (Tier 2). Virtual thread vs platform benchmark.
- **Edge:** semua §L direpresentasikan sebagai test case.

---

## O. Roadmap (urutan implementasi)

**Fase 0 — Skeleton & build** (1–2 hari)
Gradle multiproject, `plugin.yml`, `config.yml`, folder audio, main `AstolfoVoice` lifecycle, `PaperCompat`/`SpigotFallbackCompat`.

**Fase 1 — Kompatibilitas dasar (MVP)** (inti, 5–8 hari)
- `common`: `Secret`, `NetworkMessage`, 10 packet, `AudioUtils`, `Utils`, `FriendlyByteBuf`.
- `bukkit/net`: semua 15 plugin channel class + `NetManager`.
- `bukkit/voice/server`: `VoiceServer` (UDP thread + process), `ClientConnection`, `PlayerStateManager`, `ServerGroupManager`, `ServerCategoryManager`, `PingManager`, `ProximityResolver` standar.
- `ServerVoiceEvents` handshake state machine.
- Target: client SVC connect + proximity voice jalan tanpa fitur spesial.

**Fase 2 — Audio playback & command** (3–5 hari)
Decoder mp3/ogg/wav + resampler + `StreamingAudioPlayer` + command tree + `AudioFileRegistry`.

**Fase 3 — Voice range dinamis + Sound physics Tier 1** (4–6 hari)
`LoudnessMeter` + `SoundPhysics` raytrace (Paper API) + cache + occlusion gate + distance modulation.

**Fase 4 — Noise cancellation + Tier 2 DSP** (3–5 hari)
RNNoise/Speex load + low-pass/reverb bake (opt-in per-world).

**Fase 5 — API publik + integrasi** (3–4 hari)
`AstolfoApi` + service + PlaceholderAPI + Denizen/Skript + PrivateChannel.

**Fase 6 — Multi-version adapter + polish + test** (ongoing)
`VersionedPacketRegistry`, adapter legacy, perf benchmark, wire compat fixtures.

---

## P. Catatan legal
SVC = GPL-3.0. Fork AstolfoVoice **wajib** GPL-3.0 + preserve copyright notice henkelmax. Protokol direimplementasi bersih (tidak copy-paste sumber untuk logika bisnis baru); wire format byte-exact karena itu kontrak interop, bukan expression unik. Sebut "kompatibel dengan Simple Voice Chat", bukan afiliasi/endorsed.


&nbsp;

<p align="right">
  <img src="../../astolfo2.png" alt="heart" width="120" style="border-radius:14px; box-shadow:0 0 0 3px #ffb6c1, 0 6px 18px rgba(255,92,168,.25);" />
</p>
<p align="center"><sub style="color:#d8b4e2">architecture with love - stay soft</sub></p>