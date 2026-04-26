@echo off
echo === ProjectGrafika - Hand Tracking Drawing System ===
echo Installing Python dependencies...
pip install -r requirements.txt >nul 2>&1
echo.
echo Compiling...

REM Optimasi: hanya compile jika diperlukan
if not exist "out" (
    mkdir out
)

REM Optimasi: gunakan opsi kompilasi yang lebih cepat
javac -cp "lib\opencv-4120.jar" -d out -sourcepath src src\main\java\handtracking\*.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Kompilasi gagal!
    pause
    exit /b 1
)

echo Compilation successful! Running...

REM Optimasi: gunakan JVM options untuk performa lebih baik
java -Xms256m -Xmx512m -cp "out;lib\opencv-4120.jar" ^
     -Djava.library.path="D:\Softwares\opencv\build\java\x64" ^
     handtracking.Main

pause
