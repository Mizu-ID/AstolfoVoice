<table width="100%"><tr>
<td width="110" valign="middle" align="center">
  <img src="astolfo.png" alt="Astolfo" width="96" style="border-radius:14px; box-shadow:0 0 0 3px #ffb6c1, 0 6px 18px rgba(255,92,168,.35);" />
</td>
<td valign="middle">

# <span style="color:#ff5ca8">Astolfo</span> <sub style="color:#d8b4e2; font-size:.5em">✿ proximity voice chat, but make it cute</sub>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-ff5ca8?style=flat-square&logo=gnu)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Mizu-ID/Astolfo?include_prereleases&style=flat-square&color=ff9ecf&label=✨%20release)](https://github.com/Mizu-ID/Astolfo/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-00B2FF?style=flat-square&logo=papermc)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk)](https://openjdk.org)
[![Discussions](https://img.shields.io/badge/discuss-pink%20%E2%99%A1-ffb6c1?style=flat-square)](https://github.com/Mizu-ID/Astolfo/discussions)

</td>
</tr></table>

&nbsp;

> Monorepo untuk ekosistem plugin Minecraft **Astolfo** — proximity voice chat yang
> kompatibel protokol Simple Voice Chat, tapi dibuat dengan rasa: sound physics raytrace,
> voice range dinamis, playback mp3/ogg/wav + preset efek suara, dan UI pink cerah yang
> ramah. Tiap plugin tinggal di folder sendiri; root Gradle meng-include semua sebagai
> subproject dengan toolchain Java 25 yang shared.

&nbsp;

## ✿ Plugin

<table width="100%"><tr>
<td valign="top">

| Folder | Versi | Status | Apa ini |
|--------|-------|--------|---------|
| [`AstolfoVoice`](AstolfoVoice) | 0.2.2 | **Playable** | Proximity voice chat kompatibel SVC · sound physics raytrace · range dinamis · noise suppression · playback mp3/ogg/wav + preset (PHONE/RADIO/MEGA/CAVE/**KAWAII**/**LOFI**) · location playback · list clickable · UI pink candy |

</td>
<td width="90" valign="middle" align="center">
  <img src="astolfo.png" alt="✿" width="40" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
</tr></table>

&nbsp;

## ⋆ Layout

<table width="100%"><tr>
<td width="54" valign="top" align="center">
  <img src="astolfo2.png" alt="✿" width="48" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
<td valign="top">

```
Astolfo/
├─ build.gradle          root · shared toolchain Java 25
├─ settings.gradle       include tiap subproject
├─ gradle.properties     Gradle 9.6.1
├─ astolfo.png           banner ✿
├─ astolfo2.png          dekorasi ♡
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

## ♡ Build

```bash
./gradlew :AstolfoVoice:shadowJar
# → AstolfoVoice/build/libs/AstolfoVoice-0.2.2.jar
```
Butuh **JDK 25** + **Gradle 9.6.1** (wrapper disertakan). Build pertama online, lalu
bisa `--offline`.

&nbsp;

## ✦ Download
Release terbaru (jar siap pakai): https://github.com/Mizu-ID/Astolfo/releases

&nbsp;

## ✿ Kontribusi
Lihat [CONTRIBUTING.md](CONTRIBUTING.md). Inti: jaga kompatibilitas wire format SVC
(channel `voicechat:*`, UDP envelope, 10 tipe paket byte-exact), NMS-free, async
virtual thread, dan zero TODO/stub.

&nbsp;

## Lisensi
GPL-3.0 — lihat [LICENSE](LICENSE). `AstolfoVoice` adalah reimplementasi protokol
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) (henkelmax); wajib
tetap GPL-3.0. Protokol direimplementasi bersih, bukan copy-paste.

<p align="right">
  <img src="astolfo2.png" alt="♡" width="150" style="border-radius:16px; box-shadow:0 0 0 3px #ffb6c1, 0 8px 22px rgba(255,92,168,.30);" />
</p>

<p align="center">
  <sub style="color:#d8b4e2">made with ♡ · stay soft</sub>
</p>
