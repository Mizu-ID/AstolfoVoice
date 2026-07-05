# AstolfoVoice

**Proximity voice chat** untuk Paper/Spigot/Bukkit, **kompatibel protokol** dengan
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat). Client SVC biasa
connect tanpa ganti mod, tanpa patcher — server bertindak seperti server SVC pada
sisi transport (UDP `24454` + plugin channel `voicechat:*`, AES-128-GCM, Opus 48k/20ms).

> **v0.2.1 — location playback, sound-effect presets, clickable list.** Core
> transport + handshake + proximity + sound physics raytrace + playback sudah
> berjalan. Lihat *Status & batasan* di bawah.

---

## Fitur

### Kompatibilitas Simple Voice Chat (client-side, no extra mod)
- Handshake `voicechat:request_secret` -> `voicechat:secret` (16-byte AES-128-GCM
  secret, port, UUID, codec, MTU, distance, keepalive, groups, voiceHost, recording).
- Raw UDP envelope `[0xFF][UUID][IV+AES-GCM ciphertext]`, 10 tipe paket byte-exact
  (`Mic`, `PlayerSound`, `GroupSound`, `LocationSound`, `Authenticate(+Ack)`,
  `Ping`, `KeepAlive`, `ConnectionCheck(+Ack)`).
- Roster client (`PlayerStatesPacket`/`PlayerStatePacket`/`RemovePlayerStatePacket`)
  di-broadcast saat connect/quit.
- Negosiasi `compatibilityVersion` per koneksi; default 20 (target Paper 1.21+).

### Sound physics (server-side, raytrace Paper API)
Model gelombang suara, bukan gate keras:
- **Diffraction** — suara membungkung di tepi obstacle. Tiang 1-block tetap kedengar;
  dinding 2-wide mendam, bukan bisu. Gain curve halus `1/(1+extra*k)`.
- **Transmission** — suara tembus dinding tipis, redaman material-dependent
  (wool/leaves menyerap besar, glass sedang, stone/obsidian opaque). Beer-Lambert.
- **Medium (air)** — air di jalur -> lowpass dalam + absorption high-freq eksponensial
  per meter (`700*e^(-m*0.35)` Hz). Makin jauh di air, makin mendam.
- **Reverb** — density block solid + cek atap -> indikasi ruang tertutup (bergema),
  dipakai untuk distance bias + (Tier 2) reverb bake.
- **Tier 1** (default, murah): modulasi `distance` + whisper flag.
- **Tier 2** (opt-in per world): decode Opus -> DSP bake (low/high/band-pass biquad
  + reverb FDN + noise gate) -> re-encode. Efek "bergema/mendam beneran".

### Voice range dinamis
Decode Opus -> RMS dB -> interpolasi whisper -> normal -> shout. Bisik kecil, teriak
jauh. `/av voicerange <player> [range]` + API override. Shout dibatasi permission.

### Noise cancellation (server-side, opt-in)
Noise gate pada PCM + jalur Tier 2. RNNoise/Speex native = roadmap (lihat batasan).

### Playback audio (`/astolfo play`)
- Format: **mp3** (JLayer), **ogg/vorbis** (JOrbis low-level public API, pure-Java —
  jalan tanpa SPI server), **wav** (javax.sound). Resample ke 48k (Lanczos HIGH).
- Target: `player <p1> <p2> ...`, `world <w>`, `all`, `group <g>`, **`location`**.
- **Location playback (v0.2.1)** — `play <file> location <world> <x> <y> <z>` kirim
  `LocationSoundPacket` per-listener dengan posisi dunia + distance per-listener
  (jarak eye ke sumber). Client SVC memposisikan suara di koordinat itu + attenuasi
  natural -> terdengar "dari arah X", meredam sesuai jarak. (Sebelumnya broadcast
  ke seluruh world non-locational.)
- **Sound-effect preset (v0.2.1)** — flag `preset=PHONE|RADIO|MEGA|CAVE`:
  - PHONE: bandpass 300-3400 Hz (radio/telepon).
  - RADIO: bandpass narrow 500-2800 Hz + noise gate.
  - MEGA: megaphone — highpass 400 Hz + gain + soft clip.
  - CAVE: gema ruang besar — lowpass 5000 + strong reverb.
- **Pitch (v0.2.1)** — flag `pitch=P` (rate-based shift, <1 rendah/lambat, >1
  tinggi/cepat).
- Folder audio otomatis dibuat di `plugins/AstolfoVoice/audio/`.
- Playlist persisten (`playlist.yml`): create/add/remove/play/shuffle/list/delete.

### List clickable (v0.2.1)
- `/av list` menampilkan file audio sebagai teks yang bisa diklik (Adventure
  Component + ClickEvent) — klik nama file -> otomatis `/av play <file> all`.
- `/av playlist list` idem — klik nama playlist -> `/av playlist play <name> all`,
  klik file dalam playlist -> putar file itu. (Console fallback ke plain text.)

### API & integrasi
- **Public API** `AstolfoApi` lewat Bukkit `ServicesManager` — plugin lain
  `getServicesManager().load(AstolfoApi.class)`. `playAtLocation` sekarang locational
  beneran.
- **PlaceholderAPI** expansion (range, whisper, status, group, broadcast, dll).
- **PrivateChannel API** — player 1 & 2 bicara tak terbatas antar anggota (distance
  finite, bukan `Float.MAX_VALUE`), sekitar tidak dengar (atau dengar bila
  `audibleNearby`).
- Soft-hook Denizen/Skript via ServicesManager.

### Concurrency
Async penuh, virtual threads (Java 25): UDP reader -> bounded queue -> virtual thread
per paket. Raytrace main-thread (aman di Bukkit), di-cache per tick, DSP worker baca
cache. ThreadLocal Opus. TTL drop + bounded `readByteArray`. O(1) lookup address->UUID.

---

## Target platform
- **Paper 1.21+** — fitur penuh (raytrace `World#rayTraceBlocks`, Adventure
  `Component.translatable`, clickable text). NMS-free.
- Spigot fallback — fitur dasar (raytrace off, clickable mungkin plain).
- **Bukan mod client.** Bukan support NMS-reflection 1.8–1.20 penuh seperti SVC.

## Build
```bash
./gradlew :AstolfoVoice:shadowJar
# -> AstolfoVoice/build/libs/AstolfoVoice-0.2.1.jar
```
Butuh JDK 25 + Gradle 9.6.1 (wrapper disertakan). Build pertama online, setelah itu
`--offline`.

## Pasang
1. Drop `AstolfoVoice-0.2.1.jar` ke `plugins/`.
2. Start server -> `config.yml` + folder `audio/` dibuat otomatis.
3. Client pakai mod Simple Voice Chat biasa. Buka port UDP `24454`.

## Command (`/astolfovoice` · alias `/av`, `/asv`, `/astolfo`)
```
play <file> player <p1> <p2> ... [volume] [pitch=P] [preset=PHONE]
play <file> world <world> [volume] [pitch=P] [preset=PHONE]
play <file> all [volume] [pitch=P] [preset=PHONE]
play <file> location <world> <x> <y> <z> [distance] [volume] [pitch=P] [preset=PHONE]
play <file> group <group>
stop [all | player <name>]
list                       (klik file untuk putar)
voicerange <player> [range]
status [player]
reset <player>
reload
playlist create|add|remove|play|list|delete|shuffle ...
Preset: NONE, PHONE, RADIO, MEGA, CAVE. pitch=1.0 normal.
```

## Status & batasan (v0.2.1 — jujur)
**Sudah solid:** transport UDP + handshake byte-exact, roster client, proximity
broadcast + sound physics Tier 1 (difraksi/transmisi/medium air eksponensial/reverb),
dynamic range, decode mp3/ogg/wav + streaming playback, **location playback per-listener
(LocationSoundPacket)**, **sound-effect preset (PHONE/RADIO/MEGA/CAVE) + pitch**,
**list audio & playlist clickable**, playlist, public API + PlaceholderAPI +
PrivateChannel, build fat-jar.

**Masih kasar / roadmap:**
- Noise cancellation penuh (RNNoise/Speex native) belum di-bundle — saat ini noise
  gate + Tier 2 bake + preset.
- "Kompatibel semua versi client" = aspirasi; mekanisme `compatibilityVersion` +
  adapter per versi ada, default 20, adapter legacy menyusul bertahap.
- Reverb/low-pass "asli" di mixer client SVC fixed -> efek beneran cuma lewat Tier 2
  (opt-in per world, ada cost latency/CPU).
- Belum ada test otomatis / regression fixture byte-exact vs SVC.
- Pitch saat ini rate-based (ubah durasi juga); pitch-shift tanpa ubah durasi
  (PSOLA/phase vocoder) = iterasi berikutnya bila perlu.

## Dokumen
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — arsitektur, lifecycle,
  concurrency, handshake, sound physics, roadmap, edge case.
- [`docs/PROTOCOL_REFERENCE.md`](docs/PROTOCOL_REFERENCE.md) — wire format verbatim
  untuk kompatibilitas byte-exact.

## Lisensi
GPL-3.0 (mengikuti Simple Voice Chat, henkelmax). Protokol direimplementasi bersih.
