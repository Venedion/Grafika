package handtracking;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

/**
 * HandAnalyzer - Menganalisis tangan dari kontur dan convex hull:
 *   - Menghitung jumlah jari yang terangkat
 *   - Menentukan posisi ujung jari telunjuk
 *   - Mendeteksi gesture: DRAW (1 jari) atau ERASE (2 jari)
 */
public class HandAnalyzer {

    public enum Gesture {
        NONE,   // Tidak ada tangan / tidak dikenali
        DRAW,   // Hanya jari telunjuk → gambar
        ERASE   // Jari telunjuk + tengah → hapus
    }

    // Hasil analisis tangan dalam satu frame
    public static class HandResult {
        public Gesture gesture       = Gesture.NONE;
        public Point   fingerTip     = null;  // Ujung jari telunjuk
        public Point   eraserCenter  = null;  // Tengah antara telunjuk & tengah
        public int     fingerCount   = 0;
        public Rect    boundingBox   = null;
        public List<Point> fingertips = new ArrayList<>();
    }

    // Threshold sudut untuk menentukan defect yang valid (lekukan antar jari)
    private static final double DEFECT_ANGLE_THRESHOLD = 80.0; // derajat

    /**
     * Menganalisis kontur tangan dan mengembalikan HandResult.
     *
     * @param contour    Kontur tangan (dari ImageProcessor.findLargestContour)
     * @param hull       Convex hull indices
     * @param defects    Convexity defects
     * @param frameSize  Ukuran frame (untuk filter posisi)
     */
    public HandResult analyze(
            MatOfPoint contour,
            MatOfInt hull,
            MatOfInt4 defects,
            Size frameSize) {

        HandResult result = new HandResult();
        if (contour == null || contour.rows() < 5) return result;

        result.boundingBox = Imgproc.boundingRect(contour);

        // ── Mencari titik-titik jari dari convexity defects ──────────────────
        List<Point> contourPts = contour.toList();
        List<int[]> defectList = parseDefects(defects);

        List<Point> fingerTips = new ArrayList<>();
        Point topPoint = findTopPoint(contourPts, result.boundingBox);

        // Proses setiap defect untuk menemukan ujung jari
        for (int[] d : defectList) {
            Point start  = contourPts.get(d[0]); // Ujung jari
            Point end    = contourPts.get(d[1]); // Ujung jari berikutnya
            Point far    = contourPts.get(d[2]); // Titik lekukan terendah
            double depth = d[3] / 256.0;          // Kedalaman lekukan (pixel)

            // Filter: kedalaman lekukan harus signifikan
            if (depth < 20) continue;

            // Hitung sudut di titik 'far' (sudut antar jari)
            double angle = calcAngle(start, far, end);

            // Sudut < threshold → ini adalah lekukan antar jari yang valid
            if (angle < DEFECT_ANGLE_THRESHOLD) {
                // 'start' dan 'end' adalah kandidat ujung jari
                // Filter: harus berada di bagian atas tangan
                if (isUpperHalf(start, result.boundingBox)) {
                    addUniqueFingertip(fingerTips, start, 25);
                }
                if (isUpperHalf(end, result.boundingBox)) {
                    addUniqueFingertip(fingerTips, end, 25);
                }
            }
        }

        // Tambahkan titik paling atas jika belum ada (jempol/telunjuk tanpa defect)
        if (topPoint != null && isUpperHalf(topPoint, result.boundingBox)) {
            addUniqueFingertip(fingerTips, topPoint, 30);
        }

        result.fingerCount = fingerTips.size();
        result.fingertips  = fingerTips;

        // ── Tentukan gesture berdasarkan jumlah jari ──────────────────────────
        assignGesture(result, frameSize);

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper: parse MatOfInt4 defects menjadi List
    // ─────────────────────────────────────────────────────────────────────────
    private List<int[]> parseDefects(MatOfInt4 defects) {
        List<int[]> list = new ArrayList<>();
        if (defects == null || defects.rows() == 0) return list;
        int[] data = new int[(int)(defects.total() * defects.channels())];
        defects.get(0, 0, data);
        for (int i = 0; i < data.length; i += 4) {
            list.add(new int[]{data[i], data[i+1], data[i+2], data[i+3]});
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper: cari titik tertinggi (y terkecil) dalam kontur
    // ─────────────────────────────────────────────────────────────────────────
    private Point findTopPoint(List<Point> pts, Rect bbox) {
        Point top = null;
        for (Point p : pts) {
            if (top == null || p.y < top.y) top = p;
        }
        return top;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper: cek apakah titik berada di 60% atas bounding box
    // ─────────────────────────────────────────────────────────────────────────
    private boolean isUpperHalf(Point p, Rect bbox) {
        return p.y < bbox.y + bbox.height * 0.65;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper: tambahkan fingertip jika belum ada yang terlalu dekat
    // ─────────────────────────────────────────────────────────────────────────
    private void addUniqueFingertip(List<Point> tips, Point p, double minDist) {
        for (Point existing : tips) {
            if (distance(existing, p) < minDist) return;
        }
        tips.add(p);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper: hitung sudut di titik B dari segitiga A-B-C (derajat)
    // ─────────────────────────────────────────────────────────────────────────
    private double calcAngle(Point a, Point b, Point c) {
        double ab = distance(a, b);
        double cb = distance(c, b);
        double ac = distance(a, c);
        if (ab * cb == 0) return 180.0;
        double cosAngle = (ab*ab + cb*cb - ac*ac) / (2 * ab * cb);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle)); // clamp
        return Math.toDegrees(Math.acos(cosAngle));
    }

    private double distance(Point a, Point b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx*dx + dy*dy);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tentukan gesture & posisi kursor
    // ─────────────────────────────────────────────────────────────────────────
    private void assignGesture(HandResult result, Size frameSize) {
        List<Point> tips = result.fingertips;

        // Urutkan fingertips dari kiri ke kanan (x terkecil = paling kiri)
        tips.sort((a, b) -> Double.compare(a.x, b.x));

        if (tips.size() == 1) {
            // ── DRAW mode: 1 jari (telunjuk) ─────────────────────────────────
            result.gesture   = Gesture.DRAW;
            result.fingerTip = tips.get(0);

        } else if (tips.size() == 2) {
            // ── ERASE mode: 2 jari (telunjuk + tengah) ───────────────────────
            result.gesture  = Gesture.ERASE;
            // Posisi penghapus = tengah antara dua jari
            Point p1 = tips.get(0);
            Point p2 = tips.get(1);
            result.eraserCenter = new Point((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
            result.fingerTip    = result.eraserCenter; // untuk tracking

        } else if (tips.size() >= 3) {
            // 3+ jari → tidak menggambar (mode idle/stop)
            result.gesture = Gesture.NONE;
        }
    }
}
