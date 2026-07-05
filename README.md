<table width="100%"><tr>
<td width="110" valign="middle" align="center">
  <img src="astolfo.png" alt="Astolfo" width="96" style="border-radius:14px; box-shadow:0 0 0 3px #ffb6c1, 0 6px 18px rgba(255,92,168,.35);" />
</td>
<td valign="middle">

# <span style="color:#ff5ca8">Astolfo</span> <sub style="color:#d8b4e2; font-size:.5em">proximity voice chat, but make it cute ♡</sub>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-ff5ca8?style=flat-square&logo=gnu)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Mizu-ID/Astolfo?include_prereleases&style=flat-square&color=ff9ecf&label=release)](https://github.com/Mizu-ID/Astolfo/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-00B2FF?style=flat-square&logo=papermc)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk)](https://openjdk.org)
[![Discussions](https://img.shields.io/badge/discuss-pink-ffb6c1?style=flat-square)](https://github.com/Mizu-ID/Astolfo/discussions)

</td>
</tr></table>

&nbsp;

Astolfo itu monorepo ekosistem plugin Minecraft, dan plugin pertamanya **AstolfoVoice**:
proximity voice chat yang **kompatibel protokol** dengan [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat),
tapi dibuat ulang dari nol dengan rasa — client SVC biasa connect tanpa ganti mod, tanpa
patcher. Server-nya yang lebih pintar: sound physics raytrace, voice range dinamis, noise
suppression, playback mp3/ogg/wav plus preset efek suara, dan UI pink candy yang ramah.

&nbsp;

<table width="100%"><tr>
<td valign="top">

## Kenapa Astolfo ✿

Simple Voice Chat udah bagus, tapi server-nya minimal. Astolfo bikin sisi server
lebih hidup tanpa bikin client pecah:

- **Kompatibel, bukan ganti.** Client pakai mod SVC biasa. Tidak ada mod tambahan,
  tidak ada patcher. Server bertindak persis seperti server SVC di sisi transport.
- **Suara yang berperilaku kayak gelombang.** Difraksi (tiang 1-block tetap kedengar),
  transmisi tembus dinding tipis (wool menyerap, stone opaque), air bikin mendam
  eksponensial, ruang tertutup bergema. Bukan gate on/off.
- **Range dinamis.** Bisik kecil, teriak jauh — dihitung dari RMS tiap frame.
- **Playback beneran.** mp3/ogg/wav pure-Java, posisional 3D lewat `location`, preset
  PHONE/RADIO/MEGA/CAVE/KAWAII/LOFI, pitch shift, list file yang bisa diklik.
- **UI pink cerah, hand-crafted.** Palet candy konsisten, dekorasi imut yang gak
  generik, bell feedback tiap command sukses.
- **Modern di engine.** Async penuh virtual thread (Java 25), NMS-free Paper API,
  single fat-jar.

</td>
<td width="92" valign="middle" align="center">
  <img src="astolfo2.png" alt="" width="58" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
</tr></table>

&nbsp;

## Plugin

<table width="100%"><tr>
<td valign="top">

| Folder | Versi | Status | Apa ini |
|--------|-------|--------|---------|
| [`AstolfoVoice`](AstolfoVoice) | 0.2.2 | **Playable** | Proximity voice chat kompatibel SVC · sound physics raytrace · range dinamis · noise suppression · playback mp3/ogg/wav + preset (PHONE/RADIO/MEGA/CAVE/**KAWAII**/**LOFI**) · location playback · list clickable · UI pink candy |

</td>
<td width="84" valign="middle" align="center">
  <img src="astolfo.png" alt="" width="56" style="border-radius:11px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
</tr></table>

&nbsp;

## Layout

<table width="100%"><tr>
<td width="54" valign="top" align="center">
  <img src="astolfo2.png" alt="" width="48" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
<td valign="top">

```
Astolfo/
├─ build.gradle          root · shared toolchain Java 25
├─ settings.gradle       include tiap subproject
├─ gradle.properties     Gradle 9.6.1
├─ astolfo.png           banner
├─ astolfo2.png          dekorasi
├─ LICENSE               GPL-3.0
├─ CONTRIBUTING.md       cara kontribusi
├─ SECURITY.md           lapor kerentanan
├─ .github/              issue & PR template
└─ <PluginName>/
   ├─ README.md
   ├─ docs/              implementation plan, protocol reference
   ├─ build.gradle
   └─ src/main/...
```

</td>
</tr></table>

&nbsp;

## Quick start ♡

<table width="100%"><tr>
<td width="92" valign="top" align="center">
  <img src="astolfo.png" alt="" width="58" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
<td valign="top">

**Build fat-jar:**
```bash
./gradlew :AstolfoVoice:shadowJar
# → AstolfoVoice/build/libs/AstolfoVoice-0.2.2.jar
```
Butuh **JDK 25** + **Gradle 9.6.1** (wrapper disertakan). Build pertama online
(tarik Paper API + deps dari maven), setelah itu bisa `--offline`.

**Pasang:**
1. Drop `AstolfoVoice-0.2.2.jar` ke `plugins/`.
2. Start server → `config.yml` + folder `audio/` dibuat otomatis.
3. Client pakai mod Simple Voice Chat biasa. Buka port UDP `24454`.

**Atau langsung ambil jar** dari release: https://github.com/Mizu-ID/Astolfo/releases

</td>
</tr></table>

&nbsp;

## Kompatibilitas SVC

<table width="100%"><tr>
<td valign="top">

Hal paling krusial: client SVC biasa harus tetap connect tanpa diapa-apain. Hal yang
dijaga byte-exact:

- Channel namespace `voicechat:*` (bukan `astolfo:*`).
- UDP envelope `[0xFF][UUID][IV+AES-GCM]` + 10 tipe paket + urutan field.
- Konstanta audio: Opus 48k / 20ms / 960, MTU 1275.

Detail wire format lengkap: [`AstolfoVoice/docs/PROTOCOL_REFERENCE.md`](AstolfoVoice/docs/PROTOCOL_REFERENCE.md).

</td>
<td width="92" valign="middle" align="center">
  <img src="astolfo2.png" alt="" width="58" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
</tr></table>

&nbsp;

## Kontribusi

Mau bantu? Lihat [CONTRIBUTING.md](CONTRIBUTING.md). Intinya: jaga kompatibilitas wire
format SVC di atas, NMS-free, async virtual thread, zero TODO/stub, dan visual pink
candy lewat `AstolfoStyle` — dekorasi imut hand-crafted, bukan emoji-spam generik.

Lapor bug atau usul fitur lewat issue (template udah disiapin di `.github/`).
Kerentanan? Lapor privat — lihat [SECURITY.md](SECURITY.md).

&nbsp;

## Lisensi

GPL-3.0 — lihat [LICENSE](LICENSE). `AstolfoVoice` adalah reimplementasi protokol
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) (henkelmax); wajib
tetap GPL-3.0. Protokol direimplementasi bersih, bukan copy-paste.

<p align="right">
  <img src="astolfo2.png" alt="" width="150" style="border-radius:16px; box-shadow:0 0 0 3px #ffb6c1, 0 8px 22px rgba(255,92,168,.30);" />
</p>
<p align="center">
  <sub style="color:#d8b4e2">made with ♡ · stay soft</sub>
</p>
