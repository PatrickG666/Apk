# Compilare EroProfile APK su Galaxy S26 Ultra (Termux)

**Progetto:** `com.eroprofile.app`
**Branch:** `claude/android-eroprofile-app-oG2AH`

## Prerequisiti

1. Installa **Termux** da [F-Droid](https://f-droid.org/packages/com.termux/) (NON dal Play Store)
2. Almeno **5GB di spazio libero**

## Step 1 — Setup iniziale (una volta sola)

```bash
bash termux_build_setup.sh
```

Installa: OpenJDK 21, Gradle, Android SDK 35, build-tools.

## Step 2 — Compila EroProfile

```bash
# Debug (per installazione diretta)
bash build_eroprofile.sh debug

# Release (per distribuzione)
bash build_eroprofile.sh release
```

Lo script:
- Clona automaticamente il progetto da GitHub
- Ottimizza Gradle per Termux
- Genera l'APK in `~/EroProfile/app/build/outputs/apk/`

## Installare l'APK

```bash
adb install ~/EroProfile/app/build/outputs/apk/debug/app-debug.apk
```

Oppure usa un file manager per copiare e installare manualmente.

## Dettagli progetto

| Parametro | Valore |
|-----------|--------|
| applicationId | com.eroprofile.app |
| minSdk | 24 (Android 7.0+) |
| targetSdk | 34 |
| compileSdk | 34 |

## Note Galaxy S26 Ultra

- ARM64 — compatibile con tutti gli strumenti
- RAM 12-16GB — sufficiente per build Gradle
- Gradle configurato con `-Xmx3g` per lasciare RAM al sistema
