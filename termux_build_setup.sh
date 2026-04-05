#!/data/data/com.termux/files/usr/bin/bash
# Script di setup per compilare APK su Galaxy S26 Ultra (Termux)
# Uso: bash termux_build_setup.sh [percorso_progetto]

set -e

echo "=== Setup compilazione APK su Termux ==="

# Aggiorna pacchetti
echo "[1/5] Aggiornamento pacchetti..."
pkg update -y && pkg upgrade -y

# Installa dipendenze
echo "[2/5] Installazione dipendenze..."
pkg install -y \
    openjdk-21 \
    gradle \
    git \
    wget \
    unzip \
    aapt \
    apksigner

# Configura JAVA_HOME
echo "[3/5] Configurazione JAVA_HOME..."
echo 'export JAVA_HOME=$PREFIX/opt/openjdk' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# Scarica Android SDK command-line tools
echo "[4/5] Download Android SDK command-line tools..."
SDK_DIR="$HOME/android-sdk"
mkdir -p "$SDK_DIR/cmdline-tools"

CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
wget -q --show-progress -O /tmp/cmdline-tools.zip "$CMDLINE_URL"
unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-tmp
mv /tmp/cmdline-tools-tmp/cmdline-tools "$SDK_DIR/cmdline-tools/latest"
rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-tmp

# Configura SDK environment
cat >> ~/.bashrc << 'EOF'

# Android SDK
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF

source ~/.bashrc

# Accetta licenze e installa componenti SDK
echo "[5/5] Installazione Android SDK components..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo ""
echo "=== Setup completato! ==="
echo ""
echo "Per compilare il tuo progetto:"
echo "  cd /percorso/del/tuo/progetto"
echo "  ./gradlew assembleDebug"
echo ""
echo "L'APK sarà in: app/build/outputs/apk/debug/"
