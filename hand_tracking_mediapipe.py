import cv2
import json
import sys
import time
import os
import mediapipe as mp
import numpy as np

# Inisialisasi MediaPipe Hands (API Baru)
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

model_path = os.path.join(os.path.dirname(__file__), 'hand_landmarker.task')
base_options = python.BaseOptions(model_asset_path=model_path)
options = vision.HandLandmarkerOptions(
    base_options=base_options,
    running_mode=vision.RunningMode.VIDEO,
    num_hands=1,
    min_hand_detection_confidence=0.5,
    min_hand_presence_confidence=0.5,
    min_tracking_confidence=0.5
)

try:
    HAND_DETECTOR = vision.HandLandmarker.create_from_options(options)
except Exception as e:
    print(json.dumps({"error": f"Failed to create HandLandmarker: {str(e)}"}))
    sys.exit(1)

FINGERTIP_IDS = [4, 8, 12, 16, 20]
FINGER_PIP_IDS = [3, 6, 10, 14, 18]
FINGER_MCP_IDS = [2, 5, 9, 13, 17]

import math

def count_raised_fingers(hand_landmarks, w, h):
    raised = []
    tips_px = {}

    wrist = hand_landmarks[0]
    
    def distance(lm1, lm2):
        return math.hypot(lm1.x - lm2.x, lm1.y - lm2.y)

    # 1. Jempol (Thumb)
    # Saat jempol direntangkan, ujungnya (tip) menjauh dari pangkal kelingking (pinky mcp).
    # Saat dilipat, ujungnya menekuk masuk mendekati area kelingking.
    thumb_tip = hand_landmarks[4]
    thumb_ip = hand_landmarks[3]
    pinky_mcp = hand_landmarks[17]

    if distance(thumb_tip, pinky_mcp) > distance(thumb_ip, pinky_mcp):
        raised.append(0)
        tips_px[0] = (int(thumb_tip.x * w), int(thumb_tip.y * h))

    # 2. Jari lainnya (Telunjuk - Kelingking)
    # Membandingkan jarak dari ujung jari ke pergelangan tangan vs jarak persendian (pip) ke pergelangan tangan.
    for i in range(1, 5):
        tip = hand_landmarks[FINGERTIP_IDS[i]]
        pip = hand_landmarks[FINGER_PIP_IDS[i]]
        
        # Jika jarak ujung jari ke wrist lebih besar dari jarak sendi tengah ke wrist
        # berarti jari sedang terangkat / lurus.
        if distance(tip, wrist) > distance(pip, wrist):
            raised.append(i)
            tips_px[i] = (int(tip.x * w), int(tip.y * h))

    return raised, tips_px

def get_bounding_box(hand_landmarks, w, h, padding=20):
    landmarks_array = np.array([(lm.x, lm.y) for lm in hand_landmarks])
    xs = (landmarks_array[:, 0] * w).astype(int)
    ys = (landmarks_array[:, 1] * h).astype(int)

    x_min = max(0, np.min(xs) - padding)
    y_min = max(0, np.min(ys) - padding)
    x_max = min(w, np.max(xs) + padding)
    y_max = min(h, np.max(ys) + padding)

    return [int(x_min), int(y_min), int(x_max - x_min), int(y_max - y_min)]

START_TIME = time.time()

def detect_hand(frame):
    h, w = frame.shape[:2]
    result = {
        "gesture": "NONE",
        "fingerTip": None,
        "eraserCenter": None,
        "fingerCount": 0,
        "boundingBox": None
    }

    try:
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

        timestamp_ms = int((time.time() - START_TIME) * 1000)
        try:
            detection_result = HAND_DETECTOR.detect_for_video(mp_image, timestamp_ms)
        except Exception:
            timestamp_ms += 1
            detection_result = HAND_DETECTOR.detect_for_video(mp_image, timestamp_ms)

        if not detection_result.hand_landmarks:
            return result

        hand_landmarks = detection_result.hand_landmarks[0]
        raised, tips_px = count_raised_fingers(hand_landmarks, w, h)
        
        result["fingerCount"] = len(raised)
        result["boundingBox"] = get_bounding_box(hand_landmarks, w, h)

        non_thumb_raised = [f for f in raised if f != 0]

        if non_thumb_raised == [1]:
            tip = tips_px[1]
            result["gesture"] = "DRAW"
            result["fingerTip"] = [tip[0], tip[1]]
        elif set(non_thumb_raised) == {1, 2}:
            tip1 = tips_px[1]
            tip2 = tips_px[2]
            cx = (tip1[0] + tip2[0]) // 2
            cy = (tip1[1] + tip2[1]) // 2
            result["gesture"] = "ERASE"
            result["eraserCenter"] = [cx, cy]
            result["fingerTip"] = [cx, cy]

        return result

    except Exception as e:
        import traceback
        result["error"] = traceback.format_exc()
        return result

if __name__ == "__main__":
    for line in sys.stdin:
        image_path = line.strip()
        if not image_path or image_path == "quit":
            break
            
        if not os.path.exists(image_path):
            print(json.dumps({"error": "File not found: " + image_path, "gesture": "NONE"}))
            sys.stdout.flush()
            continue

        frame = cv2.imread(image_path)
        if frame is None:
            print(json.dumps({"error": "Could not read image: " + image_path, "gesture": "NONE"}))
            sys.stdout.flush()
            continue

        result = detect_hand(frame)
        print(json.dumps(result))
        sys.stdout.flush()