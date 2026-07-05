# AstolfoVoice

**Proximity voice chat** untuk Paper/Spigot/Bukkit, **kompatibel protokol** dengan
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat). Client SVC biasa
connect tanpa ganti mod, tanpa patcher — server bertindak seperti server SVC pada
sisi transport (UDP `24454` + plugin channel `voicechat:*`, AES‑128‑GCM, Opus 48k/20ms).

> **v0.1.0 — playable prototype.** Core transport + handshake + proximity + sound
> physics + playback sudah berjalan dan ter‑build. Lihat *Status & batasan* di bawah
> untuk apa yang sudah solid vs yang masih kasar.

---

## Fitur

### Kompatibilitas Simple Voice Chat (client-side, no extra mod)
- Handshake `voicechat:request_secret` → `voicechat:secret` (16‑byte AES‑128‑GCM
  secret, port, UUID, codec, MTU, distance, keepalive, groups, voiceHost, recording).
- Raw UDP envelope `[0xFF][UUID][IV+AES-GCM ciphertext]`, 10 tipe paket byte‑exact
  (`Mic`, `PlayerSound`, `GroupSound`, `LocationSound`, `Authenticate(+Ack)`,
  `Ping`, `KeepAlive`, `ConnectionCheck(+Ack)`).
- Roster client (`PlayerStatesPacket`/`PlayerStatePacket`/`RemovePlayerStatePacket`)
  di‑broadcast saat connect/quit, jadi client tahu siapa yang punya voice.
- Negosiasi `compatibilityVersion` per koneksi; default 20 (target Paper 1.21+).

### Sound physics (server‑side, raytrace Paper API)
Model gelombang suara, bukan gate keras:
- **Diffraction** — suara membungkung di tepi obstacle. Tiang 1‑block tetap kedengar
  (leak besar); dinding 2‑wide mendam, bukan bisu.
- **Transmission** — suara tembus dinding tipis, redaman material‑dependent
  (wool/leaves menyerap besar, glass sedang, stone/obsidian opaque).
- **Medium** — air di jalur → lowpass dalam + absorption high‑freq per block,
  suara terdengar muffled dan lebih "jauh".
- **Reverb** — density block solid sekitar listener → indikasi ruang tertutup
  (bergema), dipakai untuk distance bias + (Tier 2) reverb bake.
- **Tier 1** (default, murah): hasil `SoundPath` → modulasi `distance` + whisper flag.
  Cukup untuk "tertutup / mendam / jernih / bergema" via field distance client SVC.
- **Tier 2** (opt‑in per world): decode Opus → DSP bake (low‑pass biquad + reverb FDN
  + noise gate) → re‑encode. "Bergema/mendam beneran" di audio, trade latency/CPU.

### Voice range dinamis
Decode Opus → RMS dB → interpolasi whisper → normal → shout. Bisik kecil, teriak jauh.
`/av voicerange <player> [range]` + API override per player. Shout dibatasi permission.

### Noise cancellation (server‑side, opt‑in)
Noise gate pada PCM + jalur Tier 2. RNNoise/Speex native = roadmap (lihat batasan).

### Playback audio (`/astolfo play`)
- Format: **mp3** (JLayer), **ogg/vorbis** (JOrbis low‑level public API, pure‑Java —
  jalan tanpa SPI server), **wav** (javax.sound). Semua di‑resample ke 48k (Lanczos HIGH)
  sebelum Opus.
- Target: `player <p1> <p2> ...`, `world <w>`, `all`, `group <g>`, `location`.
- Folder audio otomatis dibuat di `plugins/AstolfoVoice/audio/`.
- Playlist persisten (`playlist.yml`): create/add/remove/play/shuffle/list/delete.

### API & integrasi
- **Public API** `AstolfoApi` lewat Bukkit `ServicesManager` — plugin lain
  `getServicesManager().load(AstolfoApi.class)`.
- **PlaceholderAPI** expansion (range, whisper, status, group, broadcast, dll).
- **PrivateChannel API** — player 1 & 2 bicara tak terbatas antar anggota, sekitar
  tidak dengar (atau dengar bila `audibleNearby`).
- Soft‑hook Denizen/Skript via ServicesManager.

### Concurrency
Async penuh, virtual threads (Java 25): UDP reader → bounded queue → virtual thread
per paket. Raytrace di‑schedule main‑thread (aman di Bukkit), hasil di‑cache per tick,
DSP worker baca cache. ThreadLocal Opus (encoder tidak thread‑safe). TTL drop + bounded
`readByteArray` (packet hygiene). O(1) lookup address→UUID.

---

## Target platform
- **Paper 1.21+** — fitur penuh (raytrace `World#rayTraceBlocks`, Adventure
  `Component.translatable`). NMS‑free, tidak butuh reflection soup per versi.
- Spigot fallback — fitur dasar (raytrace off).
- **Bukan mod client.** Bukan support NMS‑reflection 1.8–1.20 penuh seperti SVC.

## Build
```bash
./gradlew :AstolfoVoice:shadowJar
# → AstolfoVoice/build/libs/AstolfoVoice-0.1.0.jar
```
Butuh JDK 25 + Gradle 9.6.1 (wrapper disertakan). Build pertama online (tarik deps),
setelah itu `--offline`.

## Pasang
1. Drop `AstolfoVoice-0.1.0.jar` ke `plugins/`.
2. Start server → `plugins/AstolfoVoice/config.yml` + folder `audio/` dibuat otomatis.
3. Client pakai mod Simple Voice Chat biasa. Pastikan port UDP `24454` terbuka.

## Command (`/astolfovoice` · alias `/av`, `/asv`, `/astolfo`)
```
play <file> player <p1> <p2> ... [volume]
play <file> world <world> [volume]
play <file> all [volume]
play <file> location <world> <x> <y> <z> [distance] [volume]
play <file> group <group>
stop [all | player <name>]
list
voicerange <player> [range]
status [player]
reset <player>
reload
playlist create|add|remove|play|list|delete|shuffle ...
```

## Status & batasan (v0.1.0 — jujur)
**Sudah solid:** transport UDP + handshake byte‑exact, roster client, proximity
broadcast + sound physics Tier 1, dynamic range, decode mp3/ogg/wav + streaming
playback, playlist, public API + PlaceholderAPI + PrivateChannel, build fat‑jar.

**Masih kasar / roadmap:**
- `play ... location` saat ini broadcast ke seluruh world (non‑locational); pemutaran
  posisi per‑listener dengan `LocationSoundPacket` + attenuasi jarak = iterasi berikutnya.
- List audio clickable (klik → `/av play`) belum selesai.
- Sound effect preset (pitch/speed) belum ada.
- Noise cancellation penuh (RNNoise/Speex native) belum di‑bundle — saat ini noise gate
  + Tier 2 bake.
- "Kompatibel semua versi client" = aspirasi; mekanisme `compatibilityVersion` + adapter
  per versi ada, default 20, adapter legacy menyusul bertahap.
- Reverb/low‑pass "asli" di mixer client SVC fixed → efek beneran cuma lewat Tier 2
  (opt‑in per world, ada cost latency/CPU).
- Belum ada test otomatis / regression fixture byte‑exact vs SVC.

## Dokumen
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — arsitektur, lifecycle,
  concurrency, handshake, sound physics, roadmap, edge case.
- [`docs/PROTOCOL_REFERENCE.md`](docs/PROTOCOL_REFERENCE.md) — wire format verbatim
  untuk kompatibilitas byte‑exact.

## Lisensi
GPL‑3.0 (mengikuti Simple Voice Chat, henkelmax). Protokol direimplementasi bersih.
