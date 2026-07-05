<table width="100%"><tr>
<td width="72" valign="middle" align="center">
  <img src="astolfo2.png" alt="Astolfo" width="60" style="border-radius:11px; box-shadow:0 0 0 2px #ffb6c1, 0 4px 12px rgba(255,92,168,.28);" />
</td>
<td valign="middle">

# Security Policy <span style="color:#ff5ca8">✿</span>

</td>
</tr></table>

&nbsp;

## ⋆ Versi yang didukung
Hanya rilis terbaru yang mendapat perbaikan keamanan.

| Versi | Didukung |
|-------|----------|
| 0.2.x | ✿ ya |
| < 0.2 | ✗ tidak |

&nbsp;

## ♡ Melaporkan kerentanan

<table width="100%"><tr>
<td valign="top">

Jangan buka issue publik untuk kerentanan keamanan. Kirim laporan privat lewat
GitHub Security Advisory: tab **Security** -> **Report a vulnerability**, atau
email maintainer.

Sertakan: deskripsi, langkah reproduksi, dampak, dan versi yang terdampak.
Kami berusaha merespons dalam 7 hari.

</td>
<td width="44" valign="middle" align="center">
  <img src="astolfo.png" alt="✿" width="36" style="border-radius:9px; box-shadow:0 0 0 2px #ffd6e7; opacity:.9;" />
</td>
</tr></table>

&nbsp;

## ✿ Catatan keamanan
- Payload UDP dienkripsi AES-128-GCM per player (secret 16-byte per session).
- `readByteArray` dibatasi (`MAX_VOICE_CHAT_PACKET_SIZE`) untuk mitigasi paket besar.
- Paket kedaluwarsa di-drop (TTL) — `MicPacket` 500ms, sound 10s.
- Rate limit TCP per player (`network.tcp_rate_limit`).
- Secret tidak pernah di-log. Dekripsi gagal = silent drop (tidak membocorkan info).

&nbsp;

## ✦ Hardening roadmap
- Bundle RNNoise native dengan fallback Speex pure-Java (noise suppression penuh).
- Regression fixture byte-exact vs Simple Voice Chat untuk proteksi wire format.
- Adapter legacy compatibility-version (multi-version client) dengan validasi ketat.

<p align="right">
  <img src="astolfo.png" alt="♡" width="120" style="border-radius:14px; box-shadow:0 0 0 3px #ffb6c1, 0 6px 18px rgba(255,92,168,.28);" />
</p>
<p align="center"><sub style="color:#d8b4e2">secure · but stay soft ♡</sub></p>
