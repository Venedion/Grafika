#!/bin/bash
echo "=== ProjectGrafika - Hand Tracking Drawing System ==="
echo "Installing Python dependencies..."
pip install -r requirements.txt >/dev/null 2>&1
echo "Compiling..."

# Optimasi: hanya compile jika diperlukan
if [ ! -d "out" ]; then
    mkdir out
fi

# Optimasi: gunakan opsi kompilasi yang lebih cepat
javac -cp "lib/opencv-4120.jar" -d out -sourcepath src src/main/java/handtracking/*.java

if [ $? -ne 0 ]; then
    echo "[ERROR] Kompilasi gagal!"
    exit 1
fi

echo "Compilation successful! Running..."

# Optimasi: gunakan JVM options untuk performa lebih baik
java -Xms256m -Xmx512m -cp "out:lib/opencv-4120.jar" \
     -Djava.library.path="/usr/local/lib" \
     handtracking.Main
