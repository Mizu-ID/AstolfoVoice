<table width="100%"><tr>
<td width="86" valign="middle" align="center">
  <img src="../astolfo.png" alt="Astolfo" width="72" style="border-radius:12px; box-shadow:0 0 0 2px #ffb6c1, 0 4px 14px rgba(255,92,168,.30);" />
</td>
<td valign="middle">

# <span style="color:#ff5ca8">AstolfoVoice</span>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-ff5ca8?style=flat-square&logo=gnu)](../LICENSE)
[![Release](https://img.shields.io/github/v/release/Mizu-ID/Astolfo?include_prereleases&style=flat-square&color=ff9ecf&label=✨%20release)](https://github.com/Mizu-ID/Astolfo/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-00B2FF?style=flat-square&logo=papermc)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk)](https://openjdk.org)

</td>
</tr></table>

&nbsp;

**Proximity voice chat** untuk Paper/Spigot/Bukkit, **kompatibel protokol** dengan
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat). Client SVC biasa
connect tanpa ganti mod, tanpa patcher — server bertindak seperti server SVC pada
sisi transport (UDP `24454` + plugin channel `voicechat:*`, AES-128-GCM, Opus 48k/20ms).

> **v0.2.2 — femboyish pink UI, new presets, smart tab-completion.** Semua pesan
> plugin sekarang pakai palet pink candy yang konsisten (✿ ♡ ⋆), hand-crafted bukan
> generik. Command punya tab completion context-aware (file audio, playlist, world,
> preset, player). Preset baru KAWAII + LOFI. Bell feedback saat command sukses.

&nbsp;

## ✿ Fitur

### Kompatibilitas Simple Voice Chat (client-side, no extra mod)
- Handshake `voicechat:request_secret` -> `voicechat:secret` (16-byte AES-128-GCM
  secret, port, UUID, codec, MTU, distance, keepalive, groups, voiceHost, recording).
- Raw UDP envelope `[0xFF][UUID][IV+AES-GCM ciphertext]`, 10 tipe paket byte-exact
  (`Mic`, `PlayerSound`, `GroupSound`, `LocationSound`, `Authenticate(+Ack)`,
  `Ping`, `KeepAlive`, `ConnectionCheck(+Ack)`).
- Roster client (`PlayerStatesPacket`/`PlayerStatePacket`/`RemovePlayerStatePacket`)
  di-broadcast saat connect/quit.
- Negosiasi `compatibilityVersion` per koneksi; default 20 (target Paper 1.21+).

&nbsp;

<table width="100%"><tr>
<td valign="top">

### Sound physics (server-side, raytrace Paper API)
Model gelombang suara, bukan gate keras:
- **Diffraction** — suara membungkung di tepi obstacle. Tiang 1-block tetap kedengar;
  dinding 2-wide mendam, bukan bisu. Gain curve halus `1/(1+extra*k)`.
- **Transmission** — suara tembus dinding tipis, redaman material-dependent
  (wool/leaves menyerap besar, glass sedang, stone/obsidian opaque). Beer-Lambert.
- **Medium (air)** — air di jalur -> lowpass dalam + absorption high-freq eksponensial
  per meter (`700*e^(-m*0.35)` Hz). Makin jauh di air, makin mendam.
- **Reverb** — density block solid + cek atap -> indikasi ruang tertutup (bergema).
- **Tier 1** (default, murah): modulasi `distance` + whisper flag.
- **Tier 2** (opt-in per world): decode Opus -> DSP bake (low/high/band-pass biquad
  + reverb FDN + noise gate) -> re-encode. Efek "bergema/mendam beneran".

### Voice range dinamis
Decode Opus -> RMS dB -> interpolasi whisper -> normal -> shout. Bisik kecil, teriak
jauh. `/av voicerange <player> [range]` + API override. Shout dibatasi permission.

### Noise cancellation (server-side, opt-in)
Noise gate pada PCM + jalur Tier 2. RNNoise/Speex native = roadmap (lihat batasan).

</td>
<td width="58" valign="middle" align="center">
  <img src="../astolfo.png" alt="✿" width="40" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.9;" />
</td>
</tr></table>

&nbsp;

<table width="100%"><tr>
<td width="58" valign="middle" align="center">
  <img src="../astolfo2.png" alt="♡" width="48" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.9;" />
</td>
<td valign="top">

### Playback audio (`/astolfo play`)
- Format: **mp3** (JLayer), **ogg/vorbis** (JOrbis, pure-Java — tanpa SPI), **wav**
  (javax.sound). Resample ke 48k (Lanczos HIGH).
- Target: `player <p1> <p2> ...`, `world <w>`, `all`, `group <g>`, **`location`**.
- **Location playback** — `play <file> location <world> <x> <y> <z>` kirim
  `LocationSoundPacket` per-listener dengan posisi dunia + distance per-listener.
  Client SVC memposisikan suara di koordinat itu + attenuasi natural.
- **Sound-effect preset** — flag `preset=`:
  - `PHONE` — bandpass 300-3400 Hz (radio/telepon)
  - `RADIO` — bandpass narrow 500-2800 Hz + noise gate
  - `MEGA` — megaphone: highpass 400 Hz + gain + soft clip
  - `CAVE` — gema ruang besar: lowpass 5000 + strong reverb
  - `KAWAII` ✿ — highpass lembut 200 + reverb kecil + feel cerah (v0.2.2)
  - `LOFI` — lowpass 3500 + reverb kecil + warm cozy (v0.2.2)
- **Pitch** — flag `pitch=P` (rate-based shift, <1 rendah/lambat, >1 tinggi/cepat).
- Folder audio otomatis dibuat di `plugins/AstolfoVoice/audio/`.
- Playlist persisten (`playlist.yml`): create/add/remove/play/shuffle/list/delete.

</td>
</tr></table>

&nbsp;

### List clickable
- `/av list` menampilkan file audio sebagai teks yang bisa diklik — klik nama file
  -> otomatis `/av play <file> all`.
- `/av playlist list` idem — klik playlist -> putar playlist, klik file -> putar file.
- Console fallback ke plain text.

&nbsp;

<table width="100%"><tr>
<td valign="top">

### ♡ Visual & UX (v0.2.2)
- **Palet pink candy konsisten** lewat `AstolfoStyle` — candy `#FF9ECF`, hot pink
  `#FF5CA8` aksen, blush `#FFB6C1`, lavender `#D8B4E2`, cream, deep rose. Dipakai
  semua command + pesan, bukan `ChatColor` mentah.
- **Dekorasi hand-crafted** — prefix `✿ Astolfo ♡`, bunga kecil asimetris, garis
  pink putus-putus sebagai pemisah. Bukan emoji-spam generik.
- **Sound feedback** — bell note lembut saat command sukses (volume 0.6, pitch 1.6).
- **Tab completion context-aware**:
  - `play <TAB>` -> list file di folder audio.
  - `play <file> player <TAB>` -> nama player online.
  - `play <file> world|location <TAB>` -> nama world.
  - `play ... <TAB>` (akhir) -> saran `preset=KAWAII`, `pitch=1.0`.
  - `playlist add/remove <TAB>` -> nama playlist lalu file audio.
  - `playlist play <name> <TAB>` -> `player|world|all|shuffle`.

### API & integrasi
- **Public API** `AstolfoApi` lewat Bukkit `ServicesManager`. `playAtLocation`
  locational beneran.
- **PlaceholderAPI** expansion (range, whisper, status, group, broadcast, dll).
- **PrivateChannel API** — player 1 & 2 bicara tak terbatas antar anggota (distance
  finite), sekitar tidak dengar (atau dengar bila `audibleNearby`).
- Soft-hook Denizen/Skript via ServicesManager.

### Concurrency
Async penuh, virtual threads (Java 25): UDP reader -> bounded queue -> virtual thread
per paket. Raytrace main-thread (aman di Bukkit), di-cache per tick, DSP worker baca
cache. ThreadLocal Opus. TTL drop + bounded `readByteArray`. O(1) lookup address->UUID.

</td>
<td width="44" valign="middle" align="center">
  <img src="../astolfo.png" alt="✿" width="36" style="border-radius:9px; box-shadow:0 0 0 2px #ffd6e7; opacity:.9;" />
</td>
</tr></table>

&nbsp;

## Target platform
- **Paper 1.21+** — fitur penuh (raytrace `World#rayTraceBlocks`, Adventure
  `Component`, clickable text). NMS-free.
- Spigot fallback — fitur dasar (raytrace off, UI mungkin plain).
- **Bukan mod client.** Bukan support NMS-reflection 1.8–1.20 penuh seperti SVC.

## Build
```bash
./gradlew :AstolfoVoice:shadowJar
# -> AstolfoVoice/build/libs/AstolfoVoice-0.2.2.jar
```
Butuh JDK 25 + Gradle 9.6.1 (wrapper disertakan). Build pertama online, lalu `--offline`.

## Pasang
1. Drop `AstolfoVoice-0.2.2.jar` ke `plugins/`.
2. Start server -> `config.yml` + folder `audio/` dibuat otomatis.
3. Client pakai mod Simple Voice Chat biasa. Buka port UDP `24454`.

## Command (`/astolfovoice` · alias `/av`, `/asv`, `/astolfo`)
```
play <file> player <p1> <p2> ... [volume] [pitch=P] [preset=KAWAII]
play <file> world <world> [volume] [pitch=P] [preset=KAWAII]
play <file> all [volume] [pitch=P] [preset=KAWAII]
play <file> location <world> <x> <y> <z> [distance] [volume] [pitch=P] [preset=KAWAII]
play <file> group <group>
stop [all | player <name>]
list                       (klik file untuk putar ♡)
voicerange <player> [range]
status [player]
reset <player>
reload
playlist create|add|remove|play|list|delete|shuffle ...
Preset: NONE PHONE RADIO MEGA CAVE KAWAII LOFI  ·  pitch=1.0 normal
```

&nbsp;

## Status & batasan (v0.2.2 — jujur)
**Sudah solid:** transport UDP + handshake byte-exact, roster client, proximity
broadcast + sound physics Tier 1 (difraksi/transmisi/medium air eksponensial/reverb),
dynamic range, decode mp3/ogg/wav + streaming playback, location playback per-listener,
sound-effect preset (PHONE/RADIO/MEGA/CAVE/**KAWAII**/**LOFI**) + pitch, list audio &
playlist clickable, **UI pink candy konsisten + sound feedback + tab completion
context-aware**, playlist, public API + PlaceholderAPI + PrivateChannel, build fat-jar.

**Masih kasar / roadmap:**
- Noise cancellation penuh (RNNoise/Speex native) belum di-bundle — saat ini noise
  gate + Tier 2 bake + preset.
- "Kompatibel semua versi client" = aspirasi; default 20, adapter legacy menyusul.
- Reverb/low-pass "asli" di mixer client SVC fixed -> efek beneran cuma lewat Tier 2.
- Belum ada test otomatis / regression fixture byte-exact vs SVC.
- Pitch rate-based (ubah durasi juga); pitch-shift tanpa ubah durasi = iterasi berikutnya.

## Dokumen
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — arsitektur, lifecycle,
  concurrency, handshake, sound physics, roadmap, edge case.
- [`docs/PROTOCOL_REFERENCE.md`](docs/PROTOCOL_REFERENCE.md) — wire format verbatim
  untuk kompatibilitas byte-exact.

## Lisensi
GPL-3.0 — lihat [LICENSE](../LICENSE). Mengikuti
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) (henkelmax);
protokol direimplementasi bersih.

<p align="right">
  <img src="../astolfo2.png" alt="♡" width="150" style="border-radius:16px; box-shadow:0 0 0 3px #ffb6c1, 0 8px 22px rgba(255,92,168,.30);" />
</p>

<p align="center"><sub style="color:#d8b4e2">made with ♡ · stay soft</sub></p>
