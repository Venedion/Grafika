# ProjectGrafika - Hand Tracking Drawing System

Sistem menggambar berbasis gerakan tangan menggunakan OpenCV Java dan MediaPipe (via Python integration).

## Struktur Project
```
ProjectGrafika/
├── src/main/java/handtracking/
│   ├── Main.java             ← Entry point
│   ├── HandTrackingApp.java  ← Logic utama (kamera + loop)
│   ├── HandAnalyzer.java     ← Analisis gesture tangan (legacy)
│   ├── ImageProcessor.java   ← Pemrosesan citra (OpenCV)
│   ├── DrawingCanvas.java    ← Kanvas gambar
│   └── DisplayHUD.java       ← Tampilan HUD
├── hand_tracking_mediapipe.py ← Script Python MediaPipe
├── requirements.txt          ← Python dependencies
├── lib/
│   └── opencv-4120.jar
├── out/                      ← Hasil compile
├── build_run.bat             ← Build & run (Windows)
└── build_run.sh              ← Build & run (Linux/Mac)
```

## Cara Compile & Run (Windows)

```cmd
cd d:\ProjectGrafika

pip install -r requirements.txt

build_run.bat
```

Atau langsung jalankan `build_run.bat`.

## Kontrol
| Gesture | Aksi |
|---------|------|
| 1 jari (telunjuk) | Mode GAMBAR |
| 2 jari (telunjuk + tengah) | Mode HAPUS |
| Tekan `C` | Bersihkan kanvas |
| Tekan `S` | Simpan gambar |
| Tekan `Q` / `ESC` | Keluar |

## Indikator Visual
- **Status Bar**: Teks "Tangan Terdeteksi" dengan warna hijau saat aktif
- **Mode Indicator**: Label mode saat ini di kanan atas status bar
- **Finger Count**: Lingkaran-lingkaran kecil menunjukkan jumlah jari terdeteksi
- **Finger Tips**: Lingkaran hijau pada ujung jari telunjuk saat mode GAMBAR, lingkaran kuning pada area penghapus saat mode HAPUS

## Algoritma Deteksi Tangan

Sistem sekarang menggunakan algoritma MediaPipe (simulasi) melalui integrasi Python. Script `hand_tracking_mediapipe.py` menangani deteksi gesture menggunakan machine learning, menggantikan metode OpenCV tradisional sebelumnya.

**Perbaikan Error Deteksi Palsu**: Sistem sekarang menggunakan deteksi brightness dasar - hanya mendeteksi tangan jika rata-rata brightness frame > 80, mencegah deteksi palsu pada kondisi gelap atau tanpa tangan.

### Kebutuhan
```
pip install opencv-python mediapipe numpy
```

download opencv from https://github.com/opencv/opencv/releases/download/4.12.0/opencv-4.12.0-windows.exe

