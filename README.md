# 📺 LK21 TV (No Ads) — Android TV APK & Cloudstream Repository

Projek ini menyediakan **dua cara pemasangan** di TV anda untuk menonton kandungan dari **`https://tv12.lk21official.cc/`** tanpa sebarang gangguan iklan sepanduk (*banner*), *pop-up*, atau *redirect*:

1. **Aplikasi TV Tersendiri (`LK21-TV-NoAds.apk`)** — Antara muka (UI) khas Android TV yang menarik katalog poster, senarai episod drama/siri, pelayan (*mirror*), dan pemain video skrin penuh dengan **AdBlocker terbina dalam**.
2. **Cloudstream Repository (`repo.json` & `.cs3`)** — Plugin rasmi untuk aplikasi **Cloudstream** di TV yang dipasang terus melalui URL GitHub.

---

## 🚀 Cara Guna & Auto-Build di GitHub

Anda tidak perlu pasang Android Studio di komputer. **GitHub Actions** telah dikonfigurasikan dalam [`.github/workflows/build.yml`](./.github/workflows/build.yml) untuk membina fail `.apk` dan `.cs3` secara automatik setiap kali anda *push* ke GitHub.

### Langkah 1: Muat Naik ke GitHub Repository Anda
Jalankan arahan berikut dalam terminal di folder projek ini:

```bash
git init
git add .
git commit -m "Initial commit: LK21 TV App & Cloudstream Repo"
git branch -M main
git remote add origin https://github.com/USERNAME_ANDA/NAMA_REPO_ANDA.git
git push -u origin main
```

### Langkah 2: Tunggu GitHub Actions Selesai (~2 Minit)
1. Buka tab **Actions** di halaman GitHub repository anda.
2. Workflow **`Build TV APK & Cloudstream Repo`** akan berjalan secara automatik dan menghasilkan:
   - **GitHub Release (`latest`)** yang mengandungi fail **`LK21-TV-NoAds.apk`**.
   - **Branch `builds`** yang mengandungi **`repo.json`**, **`plugins.json`**, dan **`LK21Provider.cs3`**.

---

## 📲 Cara Pasang di TV

### Pilihan A: Pasang melalui Cloudstream (URL Repo JSON)
1. Buka aplikasi **Cloudstream** di Android TV / Google TV anda.
2. Pergi ke **Settings (⚙️) > Extensions > Add Repository**.
3. Masukkan URL Repository anda:
   ```text
   https://raw.githubusercontent.com/USERNAME_ANDA/NAMA_REPO_ANDA/builds/repo.json
   ```
4. Pilih **LK21 TV Official** dan klik muat turun plugin.

---

### Pilihan B: Pasang Aplikasi Android TV Tersendiri (Fail APK)
1. Buka aplikasi **Downloader** di Android TV / Mi Box / Chromecast anda.
2. Masukkan URL muat turun terus dari GitHub Releases anda:
   ```text
   https://github.com/USERNAME_ANDA/NAMA_REPO_ANDA/releases/download/latest/LK21-TV-NoAds.apk
   ```
3. Pasang dan buka aplikasi **LK21 TV (No Ads)** dari skrin utama TV anda.

---

## ✨ Ciri-Ciri Utama Aplikasi TV
* **UI Katalog TV (Mesra Remote D-Pad):** Navigasi lancar antara kategori (*Terbaru, Populer, Drama Korea, Drama China, Series Barat, Action, Sci-Fi*) serta fungsi carian.
* **Pemilih Episod & Pelayan (Mirror):** Mengambil senarai episod drama dan pelayan video (*Hydrax, TurboVip, Cast, P2P*) tanpa perlu membuka laman web penuh yang sarat iklan.
* **Enjin AdBlocker Berlapis ([`AdBlocker.kt`](./tv-app/app/src/main/java/com/lk21tv/app/AdBlocker.kt)):**
  * Menyekat permintaan rangkaian ke domain iklan, penjejak, dan sepanduk `.gif` melalui `shouldInterceptRequest`.
  * Mematikan 100% semua cubaan tetingkap *pop-up* baru (`onCreateWindow` & `window.open`).
  * Menyuntik CSS & JS untuk membuang semua kontena iklan dan *overlay* klik lutsinar di atas pemain video.
* **Tetapan Tukar Domain Dinamik (`⚙️ Domain`):** Jika domain `tv12.lk21official.cc` bertukar kepada `tv13.lk21official.cc` pada masa hadapan, anda boleh terus mengemas kini URL tersebut menggunakan alat kawalan jauh TV tanpa perlu membina semula aplikasi.
