# Compilare APK su Galaxy S26 Ultra (Termux)

## Prerequisiti

1. Installa **Termux** da [F-Droid](https://f-droid.org/packages/com.termux/) (NON dal Play Store, versione obsoleta)
2. Almeno **5GB di spazio libero**

## Setup iniziale (una volta sola)

```bash
bash termux_build_setup.sh
```

Questo script installa:
- OpenJDK 21
- Gradle
- Android SDK (platform-tools, build-tools 35, android-35)

## Compilare un progetto

```bash
# Debug (per test)
bash build_apk.sh debug /percorso/progetto

# Release (per distribuzione)
bash build_apk.sh release /percorso/progetto
```

## Installare l'APK direttamente

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Oppure copia il file APK con un file manager e installalo manualmente.

## Note Galaxy S26 Ultra

- Architettura: ARM64 (aarch64) — compatibile con tutti gli strumenti
- RAM: 12-16GB — sufficiente per build Gradle anche grandi
- Per progetti grandi aggiungi in `gradle.properties`:
  ```
  org.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError
  ```
