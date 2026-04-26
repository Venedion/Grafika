package handtracking;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class HandTrackingApp {

    private static final int FRAME_W = 640;
    private static final int FRAME_H = 480;

    private Point smoothedTip = null;
    private static final double SMOOTH_ALPHA = 0.4;

    private ImageProcessor imageProcessor;
    private HandAnalyzer handAnalyzer;
    private DrawingCanvas drawingCanvas;
    private DisplayHUD displayHUD;

    // Kontrol keyboard
    private volatile boolean running = true;
    private volatile boolean clearCanvas = false;
    private volatile boolean saveCanvas = false;

    // Panel-panel Swing
    private JLabel mainLabel;
    private JLabel canvasLabel;

    public void run() {
        // ── Buka kamera ───────────────────────────────────────────────────────
        VideoCapture cap = new VideoCapture(0);
        if (!cap.isOpened()) {
            System.err.println("[ERROR] Tidak bisa membuka kamera!");
            return;
        }
        cap.set(3, FRAME_W);
        cap.set(4, FRAME_H);

        // Tunggu kamera siap
        System.out.println("Menunggu kamera siap...");
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }
        for (int i = 0; i < 15; i++) {
            cap.read(new Mat());
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
        }
        System.out.println("Kamera siap!");

        // ── Inisialisasi komponen ─────────────────────────────────────────────
        drawingCanvas = new DrawingCanvas(FRAME_W, FRAME_H);
        displayHUD = new DisplayHUD(FRAME_W, FRAME_H);

        // ── Buat jendela Swing ────────────────────────────────────────────────
        setupWindows();

        System.out.println("Sistem berjalan...");
        System.out.println("1 jari = GAMBAR | 2 jari = HAPUS | C = Bersihkan | S = Simpan | Q/ESC = Keluar");

        Mat frame = new Mat();

        HandAnalyzer.HandResult result = new HandAnalyzer.HandResult();

        // ── MAIN LOOP ─────────────────────────────────────────────────────────
        Mat displayFrame = new Mat(); // Reuse Mat object
        Mat combined = new Mat();     // Reuse Mat object

        while (running) {
            long startTime = System.currentTimeMillis();

            if (!cap.read(frame) || frame.empty()) {
                try {
                    Thread.sleep(20); // Reduced sleep time
                } catch (InterruptedException e) {
                }
                continue;
            }
            if (frame.width() <= 0 || frame.height() <= 0)
                continue;

            // Handle input keyboard - optimized
            if (clearCanvas) {
                drawingCanvas.clearCanvas();
                clearCanvas = false;
            }
            if (saveCanvas) {
                drawingCanvas.saveCanvas();
                saveCanvas = false;
            }

            // Flip horizontal - reuse displayFrame
            Core.flip(frame, displayFrame, 1);

            // Deteksi tangan menggunakan MediaPipe
            result = detectHandWithMediaPipe(frame);
            boolean handDetected = (result.gesture != HandAnalyzer.Gesture.NONE);

            if (handDetected && result.fingerTip != null) {
                smoothedTip = smoothPoint(smoothedTip, result.fingerTip);
                result.fingerTip = smoothedTip;
            } else {
                drawingCanvas.resetLastPoint();
                smoothedTip = null;
            }

            drawingCanvas.processPoint(result.fingerTip, result.gesture);

            // Reuse combined Mat
            drawingCanvas.overlayOnFrame(displayFrame, combined, 0.85);
            drawingCanvas.drawCursor(combined, result.fingerTip, result.gesture);
            displayHUD.draw(combined, result, handDetected);

            // Tampilkan ke Swing
            showMat(mainLabel, combined);
            showMat(canvasLabel, drawingCanvas.getCanvas());

            // Calculate frame time and adjust sleep for consistent frame rate
            long frameTime = System.currentTimeMillis() - startTime;
            int sleepTime = Math.max(1, 33 - (int)frameTime); // Target ~30 FPS

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
            }
        }

        // Bersihkan - optimized cleanup
        if (processInput != null) {
            processInput.println("quit");
            processInput.flush();
            processInput.close();
        }
        if (process != null) {
            process.destroy();
        }
        cap.release();
        drawingCanvas.release();
        frame.release();
        displayFrame.release();
        combined.release();
        System.out.println("Aplikasi selesai.");
        System.exit(0);
    }

    // ── Detect hand using MediaPipe Python script ────────────────────────────
    private HandAnalyzer.HandResult detectHandWithMediaPipe(Mat frame) {
        HandAnalyzer.HandResult result = new HandAnalyzer.HandResult();
        try {
            // Optimasi: gunakan temporary file yang sama untuk mengurangi I/O
            if (tempFile == null) {
                tempFile = Files.createTempFile("frame_", ".jpg");
            }
            Imgcodecs.imwrite(tempFile.toString(), frame);

            // Run Python script - persistent process
            if (process == null) {
                processBuilder = new ProcessBuilder("python", "hand_tracking_mediapipe.py");
                processBuilder.directory(new File(System.getProperty("user.dir")));
                process = processBuilder.start();
                processInput = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"), true);
                processOutput = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            }
            
            // Kirim path gambar ke python via stdin
            processInput.println(tempFile.toString());
            processInput.flush();

            // Read output - baca dari stdout python
            String line;
            while ((line = processOutput.readLine()) != null) {
                if (line.trim().startsWith("{")) {
                    result = parseJsonToHandResult(line.trim());
                    break;
                }
            }

        } catch (Exception e) {
            // Optimasi: jangan print error setiap frame
            result.gesture = HandAnalyzer.Gesture.NONE;
        }
        return result;
    }

    // Add instance variables for optimization
    private Path tempFile = null;
    private ProcessBuilder processBuilder = null;
    private Process process = null;
    private PrintWriter processInput = null;
    private BufferedReader processOutput = null;

    private HandAnalyzer.HandResult parseJsonToHandResult(String json) {
        HandAnalyzer.HandResult result = new HandAnalyzer.HandResult();
        try {
            if (json.contains("\"gesture\": \"DRAW\"")) result.gesture = HandAnalyzer.Gesture.DRAW;
            else if (json.contains("\"gesture\": \"ERASE\"")) result.gesture = HandAnalyzer.Gesture.ERASE;
            else result.gesture = HandAnalyzer.Gesture.NONE;

            result.fingerCount = extractInt(json, "\"fingerCount\":");
            result.fingerTip = extractPoint(json, "\"fingerTip\":");
            result.eraserCenter = extractPoint(json, "\"eraserCenter\":");
            result.boundingBox = extractRect(json, "\"boundingBox\":");
        } catch (Exception e) {
            result.gesture = HandAnalyzer.Gesture.NONE;
        }
        return result;
    }

    private int extractInt(String json, String keyPattern) {
        int idx = json.indexOf(keyPattern);
        if (idx == -1) return 0;
        int start = idx + keyPattern.length();
        int end = json.indexOf(',', start);
        if (end == -1) end = json.indexOf('}', start);
        if (end == -1) return 0;
        try { return Integer.parseInt(json.substring(start, end).trim()); } catch (Exception e) { return 0; }
    }

    private Point extractPoint(String json, String keyPattern) {
        int idx = json.indexOf(keyPattern);
        if (idx == -1) return null;
        int start = json.indexOf('[', idx);
        int end = json.indexOf(']', idx);
        if (start == -1 || end == -1) return null;
        String[] parts = json.substring(start + 1, end).split(",");
        if (parts.length >= 2) {
            try { return new Point(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())); } catch (Exception e) {}
        }
        return null;
    }

    private Rect extractRect(String json, String keyPattern) {
        int idx = json.indexOf(keyPattern);
        if (idx == -1) return null;
        int start = json.indexOf('[', idx);
        int end = json.indexOf(']', idx);
        if (start == -1 || end == -1) return null;
        String[] parts = json.substring(start + 1, end).split(",");
        if (parts.length >= 4) {
            try { return new Rect(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim())); } catch (Exception e) {}
        }
        return null;
    }

    // ── Setup jendela Swing ───────────────────────────────────────────────────
    private void setupWindows() {
        // Jendela utama
        JFrame mainFrame = createFrame("Hand Tracking - Gambar dengan Tangan", 0, 0, FRAME_W, FRAME_H);
        mainLabel = new JLabel();
        mainLabel.setPreferredSize(new Dimension(FRAME_W, FRAME_H));
        mainFrame.add(mainLabel);
        mainFrame.pack();
        mainFrame.setVisible(true);
        addKeyListener(mainFrame);

        // Jendela kanvas
        JFrame canvasFrame = createFrame("Kanvas", FRAME_W + 10, 0, FRAME_W / 2, FRAME_H / 2);
        canvasLabel = new JLabel();
        canvasLabel.setPreferredSize(new Dimension(FRAME_W / 2, FRAME_H / 2));
        canvasFrame.add(canvasLabel);
        canvasFrame.pack();
        canvasFrame.setVisible(true);
        addKeyListener(canvasFrame);
    }

    private JFrame createFrame(String title, int x, int y, int w, int h) {
        JFrame f = new JFrame(title);
        f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        f.setLocation(x, y);
        f.setBackground(Color.BLACK);
        f.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                running = false;
            }
        });
        return f;
    }

    private void addKeyListener(JFrame frame) {
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_Q || k == KeyEvent.VK_ESCAPE) {
                    running = false;
                } else if (k == KeyEvent.VK_C) {
                    clearCanvas = true;
                } else if (k == KeyEvent.VK_S) {
                    saveCanvas = true;
                }
            }
        });
        frame.setFocusable(true);
    }

    // ── Konversi Mat OpenCV → BufferedImage → tampil di JLabel ───────────────
    private void showMat(JLabel label, Mat mat) {
        if (mat == null || mat.empty())
            return;
        try {
            Mat resized = new Mat();
            Dimension d = label.getPreferredSize();
            Imgproc.resize(mat, resized, new Size(d.width, d.height));

            Mat display = new Mat();
            if (resized.channels() == 1) {
                Imgproc.cvtColor(resized, display, Imgproc.COLOR_GRAY2BGR);
            } else {
                resized.copyTo(display);
            }

            int w = display.width();
            int h = display.height();
            if (w <= 0 || h <= 0)
                return;

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
            display.get(0, 0, data);

            ImageIcon icon = new ImageIcon(img);
            SwingUtilities.invokeLater(() -> label.setIcon(icon));

            resized.release();
            display.release();
        } catch (Exception e) {
            // skip frame jika ada error
        }
    }

    // ── Gambar kontur & hull ──────────────────────────────────────────────────
    private void drawHandContour(Mat frame, MatOfPoint contour, MatOfInt hull) {
        java.util.List<MatOfPoint> contourList = new java.util.ArrayList<>();
        contourList.add(mirrorContour(contour, FRAME_W));
        Imgproc.drawContours(frame, contourList, 0, new Scalar(0, 220, 0), 1, Imgproc.LINE_AA);

        java.util.List<Point> contourPts = contour.toList();
        java.util.List<Point> hullPts = new java.util.ArrayList<>();
        int[] hullIdx = new int[(int) hull.total()];
        hull.get(0, 0, hullIdx);
        for (int idx : hullIdx)
            hullPts.add(contourPts.get(idx));

        if (hullPts.size() > 2) {
            MatOfPoint hullContour = mirrorContour(
                    new MatOfPoint(hullPts.toArray(new Point[0])), FRAME_W);
            java.util.List<MatOfPoint> hullList = new java.util.ArrayList<>();
            hullList.add(hullContour);
            Imgproc.drawContours(frame, hullList, 0, new Scalar(50, 200, 255), 1, Imgproc.LINE_AA);
        }
    }

    private MatOfPoint mirrorContour(MatOfPoint contour, int width) {
        java.util.List<Point> pts = contour.toList();
        java.util.List<Point> mirrored = new java.util.ArrayList<>();
        for (Point p : pts)
            mirrored.add(new Point(width - 1 - p.x, p.y));
        return new MatOfPoint(mirrored.toArray(new Point[0]));
    }

    private Point smoothPoint(Point prev, Point current) {
        if (prev == null)
            return current;
        return new Point(
                SMOOTH_ALPHA * current.x + (1 - SMOOTH_ALPHA) * prev.x,
                SMOOTH_ALPHA * current.y + (1 - SMOOTH_ALPHA) * prev.y);
    }
}
