<table width="100%"><tr>
<td width="72" valign="middle" align="center">
  <img src="astolfo.png" alt="Astolfo" width="60" style="border-radius:11px; box-shadow:0 0 0 2px #ffb6c1, 0 4px 12px rgba(255,92,168,.28);" />
</td>
<td valign="middle">

# Contributing to <span style="color:#ff5ca8">Astolfo</span> ✿

</td>
</tr></table>

&nbsp;

Terima kasih tertarik kontribusi ke ekosistem plugin **Astolfo** ♡

&nbsp;

## ⋆ Lisensi

<table width="100%"><tr>
<td valign="top">

Semua kontribusi jatuh di bawah **GPL-3.0** (lihat [LICENSE](LICENSE)). `AstolfoVoice`
adalah reimplementasi protokol [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat)
(henkelmax); protokol direimplementasi bersih, bukan copy-paste. Dengan kontribusi kamu,
kamu set kode kamu dirilis di bawah GPL-3.0 yang sama.

</td>
<td width="44" valign="middle" align="center">
  <img src="astolfo2.png" alt="✿" width="38" style="border-radius:9px; box-shadow:0 0 0 2px #ffd6e7; opacity:.9;" />
</td>
</tr></table>

&nbsp;

## ✿ Sebelum mulai
- Repo monorepo Gradle (Java 25 toolchain, Gradle 9.6.1). Tiap plugin subproject sendiri.
- Target utama **Paper 1.21+** (raytrace Paper API, Adventure). Spigot fallback fitur dasar.
- Bukan mod client. Bukan NMS-reflection 1.8–1.20 penuh.

&nbsp;

## ♡ Build & cek

<table width="100%"><tr>
<td width="44" valign="top" align="center">
  <img src="astolfo.png" alt="♡" width="36" style="border-radius:9px; box-shadow:0 0 0 2px #ffd6e7; opacity:.9;" />
</td>
<td valign="top">

```bash
./gradlew :AstolfoVoice:compileJava        # compile cepat
./gradlew :AstolfoVoice:shadowJar          # fat-jar -> AstolfoVoice/build/libs/
```
Build pertama butuh online (tarik Paper API + deps dari maven), setelah itu `--offline`.

Saat buka PR, pastikan:
- `compileJava` lulus tanpa error/warning baru.
- **Tidak ada TODO/FIXME/stub/empty-method** yang baru (repo jaga zero-marker).
- Tidak menambah dependensi native yang gak portable, kecuali ada fallback pure-Java.
- Protokol wire format tetap byte-exact kompatibel client SVC (lihat
  `AstolfoVoice/docs/PROTOCOL_REFERENCE.md`) kalau menyentuh paket.

</td>
</tr></table>

&nbsp;

## ✦ Kompatibilitas Simple Voice Chat
Hal paling krusial: client SVC biasa harus tetap connect. Jangan ubah:
- Namespace channel `voicechat:*` (bukan `astolfo:*`).
- UDP envelope `[0xFF][UUID][IV+AES-GCM]` + 10 tipe paket + field order.
- Konstanta audio (Opus 48k/20ms/960, MTU 1275).

&nbsp;

## ⋆ Style
- Java, package `id.astolfo.*`. Final class kecuali butuh extend.
- Async via virtual thread (`Thread.ofVirtual()`) untuk pekerjaan I/O/DSP berat.
- Raytrace **main-thread** (Bukkit aman); cache hasil per tick, DSP worker baca cache.
- Jangan spill NMS; pakai Paper API publik.

&nbsp;

## ✿ Lapor bug / request fitur
Buka issue pakai template (`.github/ISSUE_TEMPLATE/`). Kasih: versi plugin, versi
Paper, log relevan, langkah reproduksi, dan apa yang diharapkan vs yang terjadi.

<p align="right">
  <img src="astolfo2.png" alt="♡" width="120" style="border-radius:14px; box-shadow:0 0 0 3px #ffb6c1, 0 6px 18px rgba(255,92,168,.28);" />
</p>
<p align="center"><sub style="color:#d8b4e2">stay soft · contribute with ♡</sub></p>
