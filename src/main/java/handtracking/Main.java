package handtracking;

import org.opencv.core.Core;

/**
 * ============================================================
 *  Hand Tracking Drawing System - Entry Point
 *  Requires: OpenCV 4.x (opencv-4120.jar or similar)
 *            Python with MediaPipe installed
 *
 *  Compile:
 *    javac -cp "lib\opencv-4xx.jar" src\main\java\handtracking\*.java -d out/
 *
 *  Run:
 *    java -cp "out;lib\opencv-4xx.jar" ^
 *         -Djava.library.path="/path/to/opencv/lib" ^
 *         handtracking.Main
 *
 *  Python dependencies:
 *    pip install -r requirements.txt
 * ============================================================
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("=== Hand Tracking Drawing System ===");
        System.out.println("Loading OpenCV native library...");

        try {
            // Load OpenCV native library
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            System.out.println("OpenCV loaded: " + Core.VERSION);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[ERROR] OpenCV native library not found!");
            System.err.println("Pastikan OpenCV sudah diinstall dan path library sudah benar.");
            System.err.println("Jalankan dengan: java -Djava.library.path=/path/to/opencv/lib ...");
            System.exit(1);
        }

        System.out.println("Memulai sistem tracking tangan...");
        System.out.println("------------------------------------");
        System.out.println("KONTROL:");
        System.out.println("  [1 jari telunjuk] -> Mode GAMBAR (warna biru)");
        System.out.println("  [2 jari: telunjuk+tengah] -> Mode HAPUS");
        System.out.println("  [Tekan 'C'] -> Bersihkan kanvas");
        System.out.println("  [Tekan 'S'] -> Simpan gambar");
        System.out.println("  [Tekan 'Q' / ESC] -> Keluar");
        System.out.println("------------------------------------");

        HandTrackingApp app = new HandTrackingApp();
        app.run();
    }
}
