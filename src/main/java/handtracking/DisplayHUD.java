package handtracking;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * DisplayHUD - Menampilkan informasi status di atas frame:
 *   - Mode saat ini (GAMBAR / HAPUS / IDLE)
 *   - Jumlah jari terdeteksi
 *   - Lingkaran pada ujung jari terdeteksi
 *   - Panel tampilan proses citra (grayscale, biner, tepi)
 */
public class DisplayHUD {

    // ── Warna HUD ─────────────────────────────────────────────────────────────
    private static final Scalar COLOR_DRAW   = new Scalar(255, 80,  80);   // Biru
    private static final Scalar COLOR_ERASE  = new Scalar(50,  255, 255);  // Kuning
    private static final Scalar COLOR_IDLE   = new Scalar(180, 180, 180);  // Abu
    private static final Scalar COLOR_WHITE  = new Scalar(255, 255, 255);
    private static final Scalar COLOR_BLACK  = new Scalar(0,   0,   0);
    private static final Scalar COLOR_GREEN  = new Scalar(50,  220, 100);
    private static final Scalar COLOR_PANEL  = new Scalar(30,  30,  30);   // Panel gelap

    private final int frameWidth;
    private final int frameHeight;

    public DisplayHUD(int frameWidth, int frameHeight) {
        this.frameWidth  = frameWidth;
        this.frameHeight = frameHeight;
    }

    /**
     * Menggambar semua elemen HUD pada frame output - Optimized version.
     */
    public void draw(Mat frame, HandAnalyzer.HandResult result, boolean handDetected) {
        // Optimasi: selalu gambar status bar dan instructions (statis)
        drawStatusBar(frame, result, handDetected);
        drawFingerCount(frame, result.fingerCount);
        drawInstructions(frame);

        // Optimasi: hanya gambar gesture label jika ada gesture
        if (result.gesture != HandAnalyzer.Gesture.NONE) {
            drawGestureLabel(frame, result.gesture);
        }

        // Optimasi: hanya gambar contour jika ada bounding box
        if (result.boundingBox != null) {
            drawContourOnFrame(frame, result);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Status bar di bagian atas
    // ─────────────────────────────────────────────────────────────────────────
    private void drawStatusBar(Mat frame, HandAnalyzer.HandResult result, boolean detected) {
        // Background bar
        Imgproc.rectangle(
            frame,
            new Point(0, 0),
            new Point(frameWidth, 50),
            COLOR_PANEL, -1
        );

        // Status tangan dengan indikator visual
        String statusText;
        Scalar statusColor;
        if (detected) {
            statusText = "✓ TANGAN TERDETEKSI";
            statusColor = COLOR_GREEN;

            // Indikator visual: lingkaran hijau
            Imgproc.circle(frame, new Point(25, 25), 8, COLOR_GREEN, -1);
            Imgproc.circle(frame, new Point(25, 25), 8, COLOR_WHITE, 2);
        } else {
            statusText = "✗ TIDAK ADA TANGAN";
            statusColor = COLOR_IDLE;

            // Indikator visual: lingkaran abu
            Imgproc.circle(frame, new Point(25, 25), 8, COLOR_IDLE, -1);
            Imgproc.circle(frame, new Point(25, 25), 8, COLOR_WHITE, 2);
        }
        putText(frame, statusText, 45, 32, 0.65, statusColor, 1);

        // Jumlah jari dengan indikator
        String fingerText = "JARI: " + result.fingerCount;
        Scalar fingerColor = result.fingerCount > 0 ? COLOR_GREEN : COLOR_IDLE;
        putText(frame, fingerText, frameWidth / 2 - 40, 32, 0.65, fingerColor, 1);

        // Mode kanan atas dengan indikator visual
        String modeText;
        Scalar modeColor;
        switch (result.gesture) {
            case DRAW:
                modeText  = "[ MENGGAMBAR ]";
                modeColor = COLOR_DRAW;
                // Icon pensil
                Imgproc.line(frame, new Point(frameWidth - 120, 15),
                           new Point(frameWidth - 100, 35), modeColor, 2);
                break;
            case ERASE:
                modeText  = "[ MENGHAPUS ]";
                modeColor = COLOR_ERASE;
                // Icon penghapus (kotak)
                Imgproc.rectangle(frame, new Point(frameWidth - 125, 15),
                                new Point(frameWidth - 105, 35), modeColor, 2);
                break;
            default:
                modeText  = "[ IDLE ]";
                modeColor = COLOR_IDLE;
                break;
        }
        putText(frame, modeText, frameWidth - 150, 32, 0.7, modeColor, 2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Indikator jumlah jari (lingkaran-lingkaran kecil)
    // ─────────────────────────────────────────────────────────────────────────
    private void drawFingerCount(Mat frame, int count) {
        int startX = 10;
        int y      = 75;
        for (int i = 0; i < 5; i++) {
            Scalar c = (i < count) ? COLOR_GREEN : COLOR_IDLE;
            Imgproc.circle(frame, new Point(startX + i * 28, y), 10, c, -1);
            Imgproc.circle(frame, new Point(startX + i * 28, y), 10, COLOR_WHITE, 1);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Label gesture besar di tengah bawah
    // ─────────────────────────────────────────────────────────────────────────
    private void drawGestureLabel(Mat frame, HandAnalyzer.Gesture gesture) {
        if (gesture == HandAnalyzer.Gesture.NONE) return;

        String label;
        Scalar color;
        if (gesture == HandAnalyzer.Gesture.DRAW) {
            label = "MENGGAMBAR";
            color = COLOR_DRAW;
        } else {
            label = "MENGHAPUS";
            color = COLOR_ERASE;
        }

        // Shadow
        putText(frame, label, frameWidth/2 - 78, frameHeight - 22,
                0.85, COLOR_BLACK, 3);
        putText(frame, label, frameWidth/2 - 80, frameHeight - 24,
                0.85, color, 2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Menggambar kontur & fingertips pada frame utama
    // ─────────────────────────────────────────────────────────────────────────
    private void drawContourOnFrame(Mat frame, HandAnalyzer.HandResult result) {
        if (result.boundingBox != null) {
            // Mirror bounding box coordinates because frame sudah diflip horizontal
            Rect mirroredBox = new Rect(
                frameWidth - result.boundingBox.x - result.boundingBox.width,
                result.boundingBox.y,
                result.boundingBox.width,
                result.boundingBox.height
            );

            // Kotak pembatas tangan - HIJAU TEBAL ketika terdeteksi
            Imgproc.rectangle(frame, mirroredBox.tl(), mirroredBox.br(), new Scalar(0, 255, 0), 3);

            // Lingkaran besar hijau di tengah bounding box sebagai indikator deteksi
            Point center = new Point(
                mirroredBox.x + mirroredBox.width / 2,
                mirroredBox.y + mirroredBox.height / 2
            );
            Imgproc.circle(frame, center, 25, new Scalar(0, 255, 0), 2);
            putText(frame, "TANGAN", (int)center.x - 25, (int)center.y - 30,
                    0.6, new Scalar(0, 255, 0), 2);
        }

        // Tandai ujung jari telunjuk untuk DRAW
        if (result.fingerTip != null) {
            // Mirror untuk tampilan
            Point p = new Point(frameWidth - 1 - result.fingerTip.x, result.fingerTip.y);
            Imgproc.circle(frame, p, 15, COLOR_GREEN, -1);
            Imgproc.circle(frame, p, 15, COLOR_WHITE, 3);
            putText(frame, "JARI", (int)p.x - 15, (int)p.y + 6, 0.7, COLOR_BLACK, 2);

            // Tambahkan panah menunjuk ke arah jari
            Point arrowStart = new Point(p.x, p.y - 25);
            Point arrowEnd = new Point(p.x, p.y - 5);
            Imgproc.arrowedLine(frame, arrowStart, arrowEnd, COLOR_GREEN, 2, Imgproc.LINE_AA, 0, 0.3);
        }

        // Tandai area penghapus untuk ERASE
        if (result.eraserCenter != null) {
            // Mirror untuk tampilan
            Point p = new Point(frameWidth - 1 - result.eraserCenter.x, result.eraserCenter.y);
            Imgproc.circle(frame, p, 18, COLOR_ERASE, -1);
            Imgproc.circle(frame, p, 18, COLOR_WHITE, 3);
            putText(frame, "HAPUS", (int)p.x - 20, (int)p.y + 6, 0.6, COLOR_BLACK, 2);

            // Tambahkan X mark untuk erase
            Imgproc.line(frame, new Point(p.x - 8, p.y - 8), new Point(p.x + 8, p.y + 8),
                        COLOR_ERASE, 2);
            Imgproc.line(frame, new Point(p.x + 8, p.y - 8), new Point(p.x - 8, p.y + 8),
                        COLOR_ERASE, 2);
        }

        // Indikator jumlah jari terdeteksi
        if (result.fingerCount > 0) {
            String countText = result.fingerCount + " JARI";
            Scalar countColor = (result.fingerCount == 1 || result.fingerCount == 2) ?
                               COLOR_GREEN : COLOR_WHITE;
            putText(frame, countText, frameWidth / 2 - 50, frameHeight / 2,
                    1.0, countColor, 3);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Instruksi singkat di pojok kiri bawah
    // ─────────────────────────────────────────────────────────────────────────
    private void drawInstructions(Mat frame) {
        String[] lines = {
            "C=Bersihkan  S=Simpan  Q=Keluar"
        };
        int y = frameHeight - 8;
        // Background transparan
        Imgproc.rectangle(frame,
            new Point(0, frameHeight - 22),
            new Point(frameWidth, frameHeight),
            new Scalar(20, 20, 20), -1);
        putText(frame, lines[0], 8, y, 0.45, COLOR_IDLE, 1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Membuat panel mini berisi citra proses (grayscale, biner, tepi)
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Membuat panel 3-in-1 untuk ditampilkan di jendela "Proses Citra".
     * @param gray     Citra grayscale
     * @param binary   Citra biner
     * @param edges    Deteksi tepi
     * @param skinMask Mask kulit
     * @param panelW   Lebar panel output
     * @param panelH   Tinggi panel output
     * @return         Mat berisi 4 gambar berdampingan
     */
    public Mat buildProcessPanel(Mat gray, Mat binary, Mat edges, Mat skinMask,
                                  int panelW, int panelH) {
        // Setiap sub-panel = 1/4 lebar panel
        int subW = panelW / 4;
        int subH = panelH;

        Mat panel = Mat.zeros(subH, panelW, org.opencv.core.CvType.CV_8UC3);

        addSubPanel(panel, gray,     0,      subW, subH, "Grayscale");
        addSubPanel(panel, binary,   subW,   subW, subH, "Biner");
        addSubPanel(panel, edges,    subW*2, subW, subH, "Tepi (Canny)");
        addSubPanel(panel, skinMask, subW*3, subW, subH, "Kulit");

        return panel;
    }

    private void addSubPanel(Mat panel, Mat src, int xOffset,
                              int subW, int subH, String label) {
        if (src == null || src.empty()) return;

        // Resize source ke ukuran sub-panel
        Mat resized = new Mat();
        org.opencv.imgproc.Imgproc.resize(src, resized, new Size(subW, subH - 18));

        // Konversi ke 3-channel jika perlu
        Mat colored = new Mat();
        if (resized.channels() == 1) {
            org.opencv.imgproc.Imgproc.cvtColor(resized, colored, Imgproc.COLOR_GRAY2BGR);
        } else {
            resized.copyTo(colored);
        }

        // Copy ke panel
        Mat roi = panel.submat(18, subH, xOffset, xOffset + subW);
        colored.copyTo(roi);

        // Label di atas
        Imgproc.rectangle(panel,
            new Point(xOffset, 0), new Point(xOffset + subW, 18),
            COLOR_PANEL, -1);
        putText(panel, label, xOffset + 4, 13, 0.4, COLOR_WHITE, 1);

        // Border
        Imgproc.rectangle(panel,
            new Point(xOffset, 0), new Point(xOffset + subW - 1, subH - 1),
            new Scalar(80, 80, 80), 1);

        resized.release();
        colored.release();
        roi.release();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utilitas teks (shorthand)
    // ─────────────────────────────────────────────────────────────────────────
    private void putText(Mat frame, String text, int x, int y,
                         double scale, Scalar color, int thickness) {
        Imgproc.putText(
            frame, text,
            new Point(x, y),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            scale, color, thickness,
            Imgproc.LINE_AA
        );
    }
}
