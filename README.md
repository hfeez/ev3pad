# EV3 Pad (Android)

Kontrol motor LEGO EV3 dengan controller PS4 lewat Android. Kotlin + Jetpack Compose.

## Cara build APK
1. Pasang Android Studio (Hedgehog atau lebih baru), lalu **File > Open** folder `EV3Pad`.
2. Tunggu Gradle sync selesai (butuh internet; JDK 17 sudah ikut Android Studio).
3. **Build > Build APK(s)**. Hasil: `app/build/outputs/apk/debug/app-debug.apk`.
4. Salin APK ke Infinix Note 30 Pro dan Huawei MatePad T10s, lalu izinkan "install dari sumber tidak dikenal".

## Cara pakai
1. Pasangkan EV3 di Pengaturan Bluetooth Android (nyalakan Bluetooth EV3, PIN 1234).
2. Pasangkan PS4 (tahan tombol PS + Share sampai lampu berkedip cepat).
3. Buka aplikasi, tekan **Hubungkan EV3**, pilih EV3, lalu ketuk tombol di gambar controller untuk mengatur motor.
