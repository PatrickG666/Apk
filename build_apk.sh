#!/data/data/com.termux/files/usr/bin/bash
# Script per compilare un APK in Termux
# Uso: bash build_apk.sh [debug|release]

set -e

BUILD_TYPE=${1:-debug}
PROJECT_DIR=${2:-$(pwd)}

echo "=== Compilazione APK ($BUILD_TYPE) ==="
echo "Progetto: $PROJECT_DIR"

cd "$PROJECT_DIR"

# Verifica che sia un progetto Android
if [ ! -f "gradlew" ] && [ ! -f "build.gradle" ] && [ ! -f "build.gradle.kts" ]; then
    echo "ERRORE: Nessun progetto Android trovato in $PROJECT_DIR"
    exit 1
fi

# Rendi gradlew eseguibile se presente
[ -f "gradlew" ] && chmod +x gradlew

# Imposta variabili ambiente
export JAVA_HOME="$PREFIX/opt/openjdk"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Scegli il comando gradle
if [ -f "gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    GRADLE_CMD="gradle"
fi

# Compilazione
if [ "$BUILD_TYPE" = "release" ]; then
    echo "Compilazione RELEASE..."
    $GRADLE_CMD assembleRelease
    echo ""
    echo "APK generato in: app/build/outputs/apk/release/"
else
    echo "Compilazione DEBUG..."
    $GRADLE_CMD assembleDebug
    echo ""
    echo "APK generato in: app/build/outputs/apk/debug/"
fi

# Mostra il file APK generato
find . -name "*.apk" -newer build.gradle* 2>/dev/null | head -5 | while read apk; do
    SIZE=$(du -sh "$apk" | cut -f1)
    echo "  -> $apk ($SIZE)"
done
