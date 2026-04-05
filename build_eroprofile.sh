#!/data/data/com.termux/files/usr/bin/bash
# Script completo per compilare EroProfile APK su Galaxy S26 Ultra (Termux)
# Progetto: com.eroprofile.app
# Uso: bash build_eroprofile.sh [debug|release]

set -e

BUILD_TYPE=${1:-debug}

echo "=== Build EroProfile APK ($BUILD_TYPE) ==="

# Rileva JAVA_HOME automaticamente
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    : # già impostato correttamente
elif [ -x "$PREFIX/opt/openjdk/bin/java" ]; then
    export JAVA_HOME="$PREFIX/opt/openjdk"
elif [ -x "$PREFIX/lib/jvm/java-21-openjdk/bin/java" ]; then
    export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
elif command -v java >/dev/null 2>&1; then
    export JAVA_HOME="$(dirname $(dirname $(readlink -f $(command -v java))))"
else
    echo "ERRORE: Java non trovato. Esegui: pkg install openjdk-21"
    exit 1
fi
echo "JAVA_HOME: $JAVA_HOME"

export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Directory progetto (clona se non esiste)
PROJECT_DIR="$HOME/EroProfile"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "Clonazione progetto..."
    git clone --branch claude/android-eroprofile-app-oG2AH \
        https://github.com/PatrickG666/Apk.git "$PROJECT_DIR"
else
    echo "Aggiornamento progetto..."
    git -C "$PROJECT_DIR" pull origin claude/android-eroprofile-app-oG2AH
fi

cd "$PROJECT_DIR"

# Accetta licenze SDK
echo "Accettazione licenze Android SDK..."
mkdir -p "$ANDROID_HOME/licenses"
echo -e "\n24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$ANDROID_HOME/licenses/android-sdk-license"
echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" >> "$ANDROID_HOME/licenses/android-sdk-license"
echo -e "\nd975f751698a77b662f1254ddbeed3901e976f5a" > "$ANDROID_HOME/licenses/android-sdk-preview-license"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platforms;android-34" "build-tools;34.0.0" > /dev/null 2>&1 || true

# Genera gradlew se mancante
if [ ! -f "gradlew" ]; then
    echo "gradlew non trovato, generazione tramite 'gradle wrapper'..."
    gradle wrapper --gradle-version 8.2
fi
chmod +x gradlew

# Ottimizzazione Gradle per dispositivi mobili
cat > gradle.properties << 'EOF'
# Ottimizzazioni per Termux / Galaxy S26 Ultra
org.gradle.jvmargs=-Xmx3g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.daemon=false
android.useAndroidX=true
kotlin.incremental=true
EOF

# Compilazione
echo ""
echo "Avvio compilazione..."
if [ "$BUILD_TYPE" = "release" ]; then
    ./gradlew assembleRelease --no-daemon
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
else
    ./gradlew assembleDebug --no-daemon
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

echo ""
if [ -f "$APK_PATH" ]; then
    SIZE=$(du -sh "$APK_PATH" | cut -f1)
    echo "=== BUILD COMPLETATA! ==="
    echo "APK: $PROJECT_DIR/$APK_PATH"
    echo "Dimensione: $SIZE"
    echo ""
    echo "Per installare sul dispositivo:"
    echo "  adb install $PROJECT_DIR/$APK_PATH"
    echo ""
    echo "Oppure copia il file con un file manager e installalo manualmente."
else
    echo "ERRORE: APK non trovato. Controlla i log sopra."
    exit 1
fi
