<table width="100%"><tr>
<td width="84" valign="middle" align="center">
  <img src="astolfo2.png" alt="Astolfo" width="68" style="border-radius:12px; box-shadow:0 0 0 2px #ffb6c1, 0 4px 12px rgba(255,92,168,.28);" />
</td>
<td valign="middle">

# Security Policy ✿

<p><sub style="color:#d8b4e2">AstolfoVoice — lapor & hardening, tapi tetap lembut</sub></p>

</td>
</tr></table>

&nbsp;

## Versi yang didukung

Hanya rilis terbaru yang mendapat perbaikan keamanan.

| Versi | Didukung |
|-------|----------|
| 0.2.x | ya |
| < 0.2 | belum |

&nbsp;

<table width="100%"><tr>
<td valign="top">

## Melaporkan kerentanan

Kalau nemu celah, tolong jangan buka issue publik ya — itu bisa dieksploit orang lain
sebelum ketambal. Kirim privat lewat **Security** → **Report a vulnerability** di
repo, atau email maintainer langsung.

Sertakan biar kami cepat tangani: deskripsi celah, langkah reproduksi, dampaknya,
dan versi plugin yang terdampak. Kami usahain balas dalam 7 hari ♡

</td>
<td width="92" valign="middle" align="center" rowspan="2">
  <img src="astolfo.png" alt="" width="58" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.92;" />
</td>
</tr></table>

&nbsp;

## Catatan keamanan

Beberapa hal yang udah kami kunci dari sononya:

- Payload UDP dienkripsi **AES-128-GCM** per player (secret 16-byte baru tiap session).
- `readByteArray` dibatasi `MAX_VOICE_CHAT_PACKET_SIZE` — mitigasi paket oversized.
- Paket kedaluwarsa di-drop otomatis (TTL): mic 500ms, sound 10s.
- Rate limit TCP per player (`network.tcp_rate_limit`).
- Secret gak pernah ke-log. Dekripsi gagal = silent drop, gak bocor info ke pengirim.

&nbsp;

## Roadmap hardening

Hal yang masih mau kami tambah lama-lama:

- Bundle **RNNoise** native + fallback Speex pure-Java (noise suppression penuh).
- Regression fixture byte-exact vs Simple Voice Chat, biar wire format gak sengaja rusak.
- Adapter legacy compatibility-version (multi-versi client) dengan validasi makin ketat.

<p align="right">
  <img src="astolfo2.png" alt="" width="150" style="border-radius:16px; box-shadow:0 0 0 3px #ffb6c1, 0 8px 22px rgba(255,92,168,.30);" />
</p>
<p align="center"><sub style="color:#d8b4e2">aman, tapi tetap stay soft ♡</sub></p>
