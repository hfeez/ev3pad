# EV3 Direct Control (Android)

Kontrol motor LEGO EV3 lewat Bluetooth, dengan controller PS4 fisik atau controller virtual buatan sendiri.
Kotlin + Jetpack Compose. Ikon launcher dari `icon_ev3_pad.svg`.

## Build APK
- Lewat GitHub Actions: upload proyek ke GitHub, jalankan workflow **Build APK**, unduh artifact `EV3Pad-debug-apk`.
- Atau Android Studio: **Build > Build APK(s)**.

Catatan: APK debug dari GitHub Actions ditandatangani kunci debug yang berbeda tiap build. Jika Android menolak
memperbarui aplikasi lama, uninstall dulu versi lama lalu pasang APK baru.

## Pemakaian singkat
1. Pasangkan EV3 (PIN 1234) dan PS4 di Pengaturan Bluetooth Android.
2. Buka aplikasi, tekan **Hubungkan EV3**.
3. Tab **Controller**: gambar menyesuaikan jenis controller (DualShock, Xbox, Joy-Con). Ketuk tombol untuk mengatur motor dan syarat sensor; mode **Uji input** untuk mencoba lewat sentuhan. Tombol menyala dan stik bergerak mengikuti controller fisik.
4. Tab **Virtual**: mode **Edit** untuk menambah, memindah, dan mengubah ukuran tombol/slider/analog; mode **Kontrol** untuk memakainya.
   Di mode Kontrol ada tombol **Layar penuh** (otomatis landscape, mode imersif).
5. **Profil** menyimpan, memuat, mengganti nama, dan menghapus setelan (terpisah untuk Controller dan Virtual).
