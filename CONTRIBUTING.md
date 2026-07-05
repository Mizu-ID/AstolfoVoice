<table width="100%"><tr>
<td width="84" valign="middle" align="center">
  <img src="astolfo.png" alt="Astolfo" width="68" style="border-radius:12px; box-shadow:0 0 0 2px #ffb6c1, 0 4px 12px rgba(255,92,168,.28);" />
</td>
<td valign="middle">

# Contributing to Astolfo ♡

<p><sub style="color:#d8b4e2">makasih udah mau bantu bikin voice chat makin imut & kenceng</sub></p>

</td>
</tr></table>

&nbsp;

Sebelum mulai ngoding, ada beberapa hal yang good-to-know biar kontribusimu gampang
diterima dan gak bikin client SVC rusak connect.

&nbsp;

## Lisensi

<table width="100%"><tr>
<td valign="top">

Semua kontribusi jatuh di bawah **GPL-3.0** (lihat [LICENSE](LICENSE)). `AstolfoVoice`
itu reimplementasi protokol [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat)
(henkelmax) — dibuat ulang bersih, bukan copy-paste. Dengan kontribusi kamu, kamu set
kode kamu dirilis di bawah GPL-3.0 yang sama.

</td>
<td width="92" valign="middle" align="center">
  <img src="astolfo2.png" alt="" width="58" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
</tr></table>

&nbsp;

## Sebelum mulai

- Repo monorepo Gradle (toolchain Java 25, Gradle 9.6.1). Tiap plugin subproject sendiri.
- Target utama **Paper 1.21+** (raytrace Paper API + Adventure). Spigot = fallback fitur dasar.
- Bukan mod client. Bukan NMS-reflection 1.8–1.20 penuh kayak SVC aslinya.

&nbsp;

## Build & cek

<table width="100%"><tr>
<td width="92" valign="top" align="center">
  <img src="astolfo.png" alt="" width="58" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
<td valign="top">

```bash
./gradlew :AstolfoVoice:compileJava        # compile cepat
./gradlew :AstolfoVoice:shadowJar          # fat-jar -> AstolfoVoice/build/libs/
```

Build pertama butuh online (tarik Paper API + deps), setelah itu bisa `--offline`.

Saat buka PR, tolong pastiin:
- `compileJava` lulus tanpa error/warning baru.
- **Gak ada TODO/FIXME/stub/empty-method** baru — repo ini jaga zero-marker.
- Gak nambah dependensi native yang gak portable, kecuali ada fallback pure-Java.
- Wire format tetap byte-exact kompatibel client SVC (lihat
  `AstolfoVoice/docs/PROTOCOL_REFERENCE.md`) kalau kamu menyentuh paket.

</td>
</tr></table>

&nbsp;

## Kompatibilitas Simple Voice Chat ✿

Hal paling krusial: client SVC biasa harus tetap bisa connect tanpa diapa-apain. Jangan utak-atik:

- Namespace channel `voicechat:*` (bukan `astolfo:*`).
- UDP envelope `[0xFF][UUID][IV+AES-GCM]` + 10 tipe paket + urutan field.
- Konstanta audio: Opus 48k / 20ms / 960, MTU 1275.

&nbsp;

## Style

<table width="100%"><tr>
<td valign="top">

- Java, package `id.astolfo.*`. `final class` kecuali emang butuh di-extend.
- Async via **virtual thread** (`Thread.ofVirtual()`) buat kerjaan I/O/DSP berat.
- Raytrace jalan di **main-thread** (Bukkit aman); cache hasil per tick, DSP worker baca cache.
- Jangan spill NMS — pakai Paper API publik aja.
- Visual: palet pink candy lewat `AstolfoStyle`, bukan `ChatColor` mentah. Dekorasi
  imut yang hand-crafted, bukan emoji-spam generik.

</td>
<td width="92" valign="middle" align="center">
  <img src="astolfo2.png" alt="" width="58" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
</tr></table>

&nbsp;

## Lapor bug / usul fitur

Buka issue pakai template di `.github/ISSUE_TEMPLATE/`. Kasih: versi plugin, versi
Paper, log relevan, langkah reproduksi, plus apa yang kamu harap vs yang terjadi.
Makin jelas, makin cepat kami tangani ♡

<p align="right">
  <img src="astolfo.png" alt="" width="150" style="border-radius:16px; box-shadow:0 0 0 3px #ffb6c1, 0 8px 22px rgba(255,92,168,.30);" />
</p>
<p align="center"><sub style="color:#d8b4e2">stay soft, contribute with ♡</sub></p>
