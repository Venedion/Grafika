package handtracking;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * DrawingCanvas - Mengelola kanvas gambar:
 *   - Menggambar garis ketika gesture DRAW
 *   - Menghapus area ketika gesture ERASE
 *   - Menyimpan dan membersihkan kanvas
 */
public class DrawingCanvas {

    // ── Warna & ukuran ────────────────────────────────────────────────────────
    private static final Scalar DRAW_COLOR   = new Scalar(255, 80,  80);  // Biru
    private static final Scalar ERASE_COLOR  = new Scalar(0, 255, 200);   // Cyan (preview)
    private static final Scalar CURSOR_DRAW  = new Scalar(255, 80,  80);  // Biru
    private static final Scalar CURSOR_ERASE = new Scalar(50, 255, 255);  // Kuning
    private static final int DRAW_THICKNESS  = 5;
    private static final int ERASE_RADIUS    = 40;

    private final Mat canvas;   // Kanvas gambar (hitam, 3 channel)
    private final int width;
    private final int height;

    private Point lastPoint = null;  // Titik terakhir untuk melanjutkan garis

    public DrawingCanvas(int width, int height) {
        this.width  = width;
        this.height = height;
        this.canvas = Mat.zeros(height, width, CvType.CV_8UC3);
        System.out.println("Kanvas dibuat: " + width + "x" + height);
    }

    /**
     * Proses titik baru berdasarkan gesture yang terdeteksi.
     * @param tip     Posisi ujung jari (dalam koordinat frame asli)
     * @param gesture Gesture saat ini
     */
    public void processPoint(Point tip, HandAnalyzer.Gesture gesture) {
        if (tip == null) {
            lastPoint = null;
            return;
        }

        // Mirror koordinat X (kamera biasanya mirror)
        Point mirroredTip = new Point(width - 1 - tip.x, tip.y);

        switch (gesture) {
            case DRAW:
                drawLine(mirroredTip);
                break;
            case ERASE:
                eraseAt(mirroredTip);
                lastPoint = null; // reset saat mode hapus
                break;
            default:
                lastPoint = null;
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Menggambar garis dari lastPoint ke titik baru
    // ─────────────────────────────────────────────────────────────────────────
    private void drawLine(Point current) {
        if (lastPoint != null) {
            // Gambar garis dari titik sebelumnya ke titik sekarang
            Imgproc.line(
                canvas,
                lastPoint,
                current,
                DRAW_COLOR,
                DRAW_THICKNESS,
                Imgproc.LINE_AA  // Anti-aliased untuk tampilan halus
            );
        } else {
            // Titik pertama → gambar titik saja
            Imgproc.circle(canvas, current, DRAW_THICKNESS / 2, DRAW_COLOR, -1);
        }
        lastPoint = current;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Menghapus area di sekitar titik
    // ─────────────────────────────────────────────────────────────────────────
    private void eraseAt(Point center) {
        Imgproc.circle(canvas, center, ERASE_RADIUS, new Scalar(0, 0, 0), -1);
    }

    /**
     * Membersihkan seluruh kanvas.
     */
    public void clearCanvas() {
        canvas.setTo(new Scalar(0, 0, 0));
        lastPoint = null;
        System.out.println("Kanvas dibersihkan.");
    }

    /**
     * Reset titik terakhir (saat tangan hilang dari frame).
     */
    public void resetLastPoint() {
        lastPoint = null;
    }

    /**
     * Menggabungkan kanvas dengan frame kamera (overlay) - Optimized version.
     * Gambar ditampilkan di atas frame kamera secara transparan.
     * @param cameraFrame Frame dari kamera (BGR)
     * @param result      Mat hasil (untuk reuse)
     * @param alpha       Transparansi gambar (0.0 - 1.0)
     */
    public void overlayOnFrame(Mat cameraFrame, Mat result, double alpha) {
        cameraFrame.copyTo(result);

        // Optimasi: hanya proses jika ada gambar di kanvas
        if (Core.countNonZero(canvas.reshape(1)) == 0) {
            return; // Kanvas kosong, tidak perlu overlay
        }

        // Buat mask dari area yang sudah digambar - reuse mask
        if (mask == null) {
            mask = new Mat();
        }
        Core.inRange(canvas, new Scalar(1, 1, 1), new Scalar(255, 255, 255), mask);

        // Optimasi: hanya blend jika ada area yang perlu digambar
        if (Core.countNonZero(mask) > 0) {
            // Reuse temporary mats
            if (canvasArea == null) canvasArea = new Mat();
            if (frameArea == null) frameArea = new Mat();

            canvas.copyTo(canvasArea, mask);
            result.copyTo(frameArea, mask);

            // result = alpha * canvas + (1-alpha) * frame
            Core.addWeighted(canvasArea, alpha, frameArea, 1.0 - alpha, 0, canvasArea);
            canvasArea.copyTo(result, mask);
        }
    }

    // Add instance variables for optimization
    private Mat mask = null;
    private Mat canvasArea = null;
    private Mat frameArea = null;

    /**
     * Menampilkan kursor di posisi jari pada frame.
     */
    public void drawCursor(Mat frame, Point tip, HandAnalyzer.Gesture gesture) {
        if (tip == null) return;

        // Mirror koordinat untuk tampilan
        Point mirroredTip = new Point(width - 1 - tip.x, tip.y);

        switch (gesture) {
            case DRAW:
                // Lingkaran kecil berwarna biru = mode gambar
                Imgproc.circle(frame, mirroredTip, 10, CURSOR_DRAW, 2, Imgproc.LINE_AA);
                Imgproc.circle(frame, mirroredTip, 3,  CURSOR_DRAW, -1);
                break;
            case ERASE:
                // Lingkaran besar berwarna kuning = mode hapus
                Imgproc.circle(frame, mirroredTip, ERASE_RADIUS, CURSOR_ERASE, 2, Imgproc.LINE_AA);
                Imgproc.line(frame,
                    new Point(mirroredTip.x - 8, mirroredTip.y),
                    new Point(mirroredTip.x + 8, mirroredTip.y),
                    CURSOR_ERASE, 1);
                Imgproc.line(frame,
                    new Point(mirroredTip.x, mirroredTip.y - 8),
                    new Point(mirroredTip.x, mirroredTip.y + 8),
                    CURSOR_ERASE, 1);
                break;
            default:
                break;
        }
    }

    /**
     * Menyimpan gambar kanvas ke file PNG.
     * @return Path file yang disimpan
     */
    public String saveCanvas() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename  = "drawing_" + timestamp + ".png";
        boolean ok = Imgcodecs.imwrite(filename, canvas);
        if (ok) {
            System.out.println("Gambar disimpan: " + filename);
        } else {
            System.err.println("Gagal menyimpan gambar!");
        }
        return ok ? filename : null;
    }

    public Mat getCanvas() { return canvas; }

    public void release() {
        canvas.release();
    }
}
