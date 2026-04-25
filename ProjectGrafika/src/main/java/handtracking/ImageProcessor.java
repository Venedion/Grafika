package handtracking;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

/**
 * ImageProcessor - Memproses frame kamera menjadi:
 *   1. Citra Grayscale
 *   2. Citra Biner (thresholding)
 *   3. Deteksi tepi (Canny Edge Detection)
 *   4. Segmentasi kulit (skin segmentation via HSV/YCrCb)
 */
public class ImageProcessor {

    // ── Parameter skin segmentation (YCrCb color space) ──────────────────────
    // Lebih robust terhadap perubahan cahaya dibandingkan HSV
    private static final Scalar SKIN_LOWER_YCRCB = new Scalar(0, 133, 77);
    private static final Scalar SKIN_UPPER_YCRCB = new Scalar(255, 173, 127);

    // ── Parameter Canny edge detection ───────────────────────────────────────
    private static final int CANNY_THRESHOLD1 = 50;
    private static final int CANNY_THRESHOLD2 = 150;

    // ── Parameter binary threshold ────────────────────────────────────────────
    private static final int BINARY_THRESHOLD = 127;

    // ── Kernel untuk morphological operations ─────────────────────────────────
    private final Mat kernelSmall;
    private final Mat kernelMedium;

    public ImageProcessor() {
        kernelSmall  = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        kernelMedium = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, new Size(7, 7));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  1. GRAYSCALE CONVERSION
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Mengkonversi frame BGR ke grayscale.
     * @param bgr  Input frame (BGR, dari kamera)
     * @param gray Output grayscale Mat
     */
    public void toGrayscale(Mat bgr, Mat gray) {
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  2. BINARY IMAGE (Otsu Thresholding)
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Membuat citra biner dari grayscale menggunakan Otsu's thresholding.
     * Otsu secara otomatis mencari nilai threshold optimal.
     * @param gray   Input grayscale
     * @param binary Output biner (hitam-putih)
     */
    public void toBinary(Mat gray, Mat binary) {
        // Gaussian blur dulu untuk mengurangi noise
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);

        // Otsu thresholding — nilai threshold dihitung otomatis
        Imgproc.threshold(
            blurred, binary,
            BINARY_THRESHOLD,
            255,
            Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU
        );

        blurred.release();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  3. EDGE DETECTION (Canny)
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Mendeteksi tepi pada citra grayscale menggunakan Canny algorithm.
     * @param gray  Input grayscale
     * @param edges Output tepi
     */
    public void detectEdges(Mat gray, Mat edges) {
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 1.4);
        Imgproc.Canny(blurred, edges, CANNY_THRESHOLD1, CANNY_THRESHOLD2);
        blurred.release();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  4. SKIN SEGMENTATION
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Mensegmentasi area kulit menggunakan ruang warna YCrCb.
     * Lebih stabil terhadap variasi pencahayaan.
     * @param bgr       Input frame BGR
     * @param skinMask  Output mask kulit (putih = kulit, hitam = bukan)
     */
    public void extractSkinMask(Mat bgr, Mat skinMask) {
        Mat ycrcb = new Mat();

        // Konversi ke YCrCb
        Imgproc.cvtColor(bgr, ycrcb, Imgproc.COLOR_BGR2YCrCb);

        // Threshold berdasarkan range warna kulit
        Core.inRange(ycrcb, SKIN_LOWER_YCRCB, SKIN_UPPER_YCRCB, skinMask);

        // Morphological operations untuk menghilangkan noise
        // Erode: menghapus titik-titik kecil (noise)
        Imgproc.erode(skinMask, skinMask, kernelSmall, new Point(-1,-1), 2);
        // Dilate: mengisi lubang kecil pada area kulit
        Imgproc.dilate(skinMask, skinMask, kernelMedium, new Point(-1,-1), 3);
        // Close: menutup celah kecil
        Imgproc.morphologyEx(skinMask, skinMask, Imgproc.MORPH_CLOSE, kernelMedium);

        ycrcb.release();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  5. CONTOUR EXTRACTION — mencari kontur terbesar (tangan)
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Menemukan kontur terbesar pada mask, diasumsikan sebagai tangan.
     * @param mask  Input binary mask
     * @return      MatOfPoint kontur terbesar, atau null jika tidak ditemukan
     */
    public MatOfPoint findLargestContour(Mat mask) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(
            mask.clone(),
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        );
        hierarchy.release();

        if (contours.isEmpty()) return null;

        double maxArea = 0;
        MatOfPoint largestContour = null;

        for (MatOfPoint c : contours) {
            double area = Imgproc.contourArea(c);
            // Filter kontur terlalu kecil (bukan tangan)
            if (area > 5000 && area > maxArea) {
                maxArea = area;
                largestContour = c;
            }
        }

        return largestContour;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  6. CONVEX HULL & DEFECTS — analisis jari
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Menghitung convex hull dari kontur tangan.
     */
    public MatOfInt computeConvexHull(MatOfPoint contour) {
        MatOfInt hull = new MatOfInt();
        Imgproc.convexHull(contour, hull);
        return hull;
    }

    /**
     * Menghitung convexity defects untuk mendeteksi lekukan antar jari.
     * @param contour Kontur tangan
     * @param hull    Convex hull indices
     * @return        MatOfInt4 defects (start, end, far, depth)
     */
    public MatOfInt4 computeConvexityDefects(MatOfPoint contour, MatOfInt hull) {
        MatOfInt4 defects = new MatOfInt4();
        if (hull.rows() > 3) {
            Imgproc.convexityDefects(contour, hull, defects);
        }
        return defects;
    }

    /**
     * Membuat visualisasi 3-channel dari mask satu channel (untuk ditampilkan).
     */
    public Mat maskToColor(Mat mask, Scalar color) {
        Mat colored = Mat.zeros(mask.size(), CvType.CV_8UC3);
        colored.setTo(color, mask);
        return colored;
    }

    public void release() {
        kernelSmall.release();
        kernelMedium.release();
    }
}
