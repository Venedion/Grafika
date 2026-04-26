import cv2
import json
import sys
import os
import mediapipe as mp
import numpy as np

# ── Inisialisasi MediaPipe Hands (API Baru) ─────────────────────────────────────
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

# Base options untuk Hand Landmarker
model_path = os.path.join(os.path.dirname(__file__), 'hand_landmarker.task')
base_options = python.BaseOptions(model_asset_path=model_path)
options = vision.HandLandmarkerOptions(
    base_options=base_options,
    num_hands=1,
    min_hand_detection_confidence=0.6,
    min_hand_presence_confidence=0.6,
    min_tracking_confidence=0.5
)

# Download dan load model (MediaPipe akan handle ini)
try:
    HAND_DETECTOR = vision.HandLandmarker.create_from_options(options)
except Exception as e:
    print(json.dumps({"error": f"Failed to create HandLandmarker: {str(e)}"}))
    sys.exit(1)

# Indeks landmark MediaPipe untuk ujung setiap jari
# Urutan: Jempol, Telunjuk, Tengah, Manis, Kelingking
FINGERTIP_IDS = [4, 8, 12, 16, 20]

# Indeks sendi PIP (tengah jari) untuk mendeteksi apakah jari terangkat
FINGER_PIP_IDS = [3, 6, 10, 14, 18]

# Indeks sendi MCP (pangkal jari) sebagai referensi telapak tangan
FINGER_MCP_IDS = [2, 5, 9, 13, 17]


def count_raised_fingers(hand_landmarks, w, h):
    """
    Hitung jari yang terangkat berdasarkan posisi landmark.
    - Jari 1-4: terangkat jika ujung jari (tip.y) lebih kecil dari sendi PIP (pip.y)
    - Jempol: bandingkan secara horizontal (x-axis)

    Returns:
        raised  : list index jari yang terangkat (0=jempol, 1=telunjuk, ...)
        tips_px : dict {finger_idx: (x_px, y_px)} untuk ujung jari yang terangkat
    """
    raised = []
    tips_px = {}

    # ── Jempol (index 0) ──────────────────────────────────────────────────────
    thumb_tip = hand_landmarks[FINGERTIP_IDS[0]]
    thumb_ip  = hand_landmarks[FINGER_PIP_IDS[0]]
    index_mcp = hand_landmarks[FINGER_MCP_IDS[1]]
    if abs(thumb_tip.x - index_mcp.x) > abs(thumb_ip.x - index_mcp.x):
        raised.append(0)
        tips_px[0] = (int(thumb_tip.x * w), int(thumb_tip.y * h))

    # ── Jari telunjuk s/d kelingking (index 1-4) ──────────────────────────────
    for i in range(1, 5):
        tip = hand_landmarks[FINGERTIP_IDS[i]]
        pip = hand_landmarks[FINGER_PIP_IDS[i]]
        if tip.y < pip.y:
            raised.append(i)
            tips_px[i] = (int(tip.x * w), int(tip.y * h))

    return raised, tips_px


def get_bounding_box(hand_landmarks, w, h, padding=20):
    """Hitung bounding box dari semua landmark tangan."""
    xs = [int(lm.x * w) for lm in hand_landmarks]
    ys = [int(lm.y * h) for lm in hand_landmarks]
    x_min = max(0, min(xs) - padding)
    y_min = max(0, min(ys) - padding)
    x_max = min(w, max(xs) + padding)
    y_max = min(h, max(ys) + padding)
    return [x_min, y_min, x_max - x_min, y_max - y_min]

    # ── Jari telunjuk s/d kelingking (index 1-4) ──────────────────────────────
    for i in range(1, 5):
        tip = lm[FINGERTIP_IDS[i]]
        pip = lm[FINGER_PIP_IDS[i]]
        if tip.y < pip.y:
            raised.append(i)
            tips_px[i] = (int(tip.x * w), int(tip.y * h))

    return raised, tips_px


def get_bounding_box(hand_landmarks, w, h, padding=20):

    """
    Deteksi tangan menggunakan MediaPipe Hands (API Baru).

    Gesture:
      - 1 jari terangkat (hanya telunjuk/index=1) → DRAW
      - 2 jari terangkat (telunjuk + tengah)      → ERASE
      - Selain itu                                 → NONE

    Output dict kompatibel dengan HandTrackingApp.parseJsonToHandResult()
    """
    h, w = frame.shape[:2]
    result_default = {
        "gesture": "NONE",
        "fingerTip": None,
        "eraserCenter": None,
        "fingerCount": 0,
        "boundingBox": None
    }

    try:
        # Konversi ke mp.Image
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame)

        # Jalankan deteksi MediaPipe
        detection_result = HAND_DETECTOR.detect(mp_image)

        if not detection_result.hand_landmarks:
            return result_default

        # Ambil tangan pertama yang terdeteksi
        hand_landmarks = detection_result.hand_landmarks[0]

        # Hitung jari yang terangkat
        raised, tips_px = count_raised_fingers(hand_landmarks, w, h)
        finger_count = len(raised)
        bbox = get_bounding_box(hand_landmarks, w, h)

        output = {
            "gesture": "NONE",
            "fingerTip": None,
            "eraserCenter": None,
            "fingerCount": finger_count,
            "boundingBox": bbox
        }

        # ── DRAW: hanya telunjuk terangkat ────────────────────────────────────────
        if raised == [1]:
            tip = tips_px[1]
            output["gesture"] = "DRAW"
            output["fingerTip"] = [tip[0], tip[1]]

        # ── ERASE: telunjuk + jari tengah terangkat ───────────────────────────────
        elif set(raised) == {1, 2}:
            tip1 = tips_px[1]
            tip2 = tips_px[2]
            cx = (tip1[0] + tip2[0]) // 2
            cy = (tip1[1] + tip2[1]) // 2
            output["gesture"] = "ERASE"
            output["eraserCenter"] = [cx, cy]
            output["fingerTip"] = [cx, cy]

        # ── NONE: gesture tidak dikenali ─────────────────────────────────────────
        else:
            output["gesture"] = "NONE"

        return output

    except Exception as e:
        print(json.dumps({"error": f"Detection failed: {str(e)}"}))
        return result_default


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(json.dumps({"error": "Usage: python hand_tracking_mediapipe.py <image_path>"}))
        sys.exit(1)

    image_path = sys.argv[1]
    if not os.path.exists(image_path):
        print(json.dumps({"error": "File not found: " + image_path}))
        sys.exit(1)

    frame = cv2.imread(image_path)
    if frame is None:
        print(json.dumps({"error": "Could not read image: " + image_path}))
        sys.exit(1)

    result = detect_hand(frame)

    # Output JSON ke stdout — dibaca oleh HandTrackingApp.java
    print(json.dumps(result))
