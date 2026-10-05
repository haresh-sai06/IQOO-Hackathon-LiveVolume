# 🌐 LiveVolume — Real-Time Spatial 3D Volumetric Calling

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%2014%2B-green.svg?logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material3-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Agora RTC](https://img.shields.io/badge/Transport-Agora%20Video%20RTC%204.4.1-099DFD.svg?logo=agora)](https://www.agora.io/)
[![TensorFlow Lite](https://img.shields.io/badge/AI%2FML-TensorFlow%20Lite%20%2B%20MiDaS-FF6F00.svg?logo=tensorflow)](https://www.tensorflow.org/lite)
[![OpenGL ES](https://img.shields.io/badge/Graphics-OpenGL%20ES%202.0-5586A4.svg?logo=opengl)](https://www.khronos.org/opengles/)
[![Firebase](https://img.shields.io/badge/Signaling-Cloud%20Firestore%20%2B%20FCM-FFCA28.svg?logo=firebase)](https://firebase.google.com/)

**LiveVolume** transforms conventional 2D video calling into an interactive, spatial 3D volumetric experience on consumer mobile hardware. By coupling on-device neural depth estimation and silhouette segmentation with high-performance OpenGL ES 3D point cloud rendering and Agora RTC data streams, LiveVolume captures, reconstructs, streams, and renders live 3D holograms of callers in real time.

Developed for the **iQOO Hackathon**.

---

## 🚀 Key Features

- **Real-Time On-Device Depth Estimation (MiDaS AI)**: Runs monocular depth inference on camera frames using quantized `midas_small.tflite` with hardware acceleration delegates (NNAPI, GPU, and multi-threaded CPU fallback).
- **Foreground Silhouette Segmentation**: Integrates MediaPipe Selfie Segmentation (`selfie_segmentation.tflite`) to cleanly mask out background clutter, isolating only the caller's volumetric human geometry.
- **Interactive 3D Point Cloud Orbit Renderer**: Unprojects 2D masked depth and camera RGB into a 3D coordinate space (~6,000–12,000 colored vertices), rendered in custom OpenGL ES 2.0 with smooth touch drag-to-orbit and pinch-to-zoom controls.
- **Sub-Second Point Cloud Network Codec**: Quantizes $(X, Y, Z)$ positions into 16-bit signed shorts and colors into 16-bit RGB565 (8 bytes/point). Automatically fragments frames into $\le 1024$-byte chunked packets streamed across Agora RTC low-latency data streams and reassembled on the receiving device.
- **Two-Way Video & Audio (Agora RTC SDK)**: Native peer video streaming, PiP self-view, hardware camera switching (front/back), audio mute, speakerphone routing, and live decibel audio level metering.
- **Call Ringing & Handshake (Cloud Firestore + FCM)**: Real-time call invite documents and Firebase Cloud Messaging heads-up notifications with interactive *Answer* and *Decline* actions.
- **Clean 2D / 3D Experience (DESIGN.md Compliant)**: Sleek two-state pill toggle (`"2D"` vs `"3D"`) with zero technical jargon, floating HUD controls, and haptic feedback.
- **Hardened Performance Architecture**: Asynchronous model loading, hardware `PixelCopy` capture decoupled from the UI thread, dynamic latency clamping to prevent frame drops, and a hidden *Performance Fallback Mode* in Settings.

---

## 🏛️ End-to-End Pipeline Architecture

```mermaid
sequenceDiagram
    autonumber
    actor Caller as Caller (Device A)
    participant AgoraA as Agora RTC Engine
    participant Signaling as Firestore + FCM
    participant AgoraB as Agora RTC Engine
    actor Callee as Callee (Device B)

    Caller->>Signaling: Initiate Call (channel invite)
    Signaling->>Callee: FCM Heads-Up Incoming Call
    Callee->>Signaling: Tap Answer
    Caller->>AgoraA: Join Channel
    Callee->>AgoraB: Join Channel
    AgoraA<<-->>AgoraB: Two-Way Video & Audio (Agora RTC)

    Note over Caller,Callee: Spatial 3D Volumetric Reconstruction Pipeline
    Caller->>Caller: PixelCopy Surface Capture (Off Main Thread)
    Caller->>Caller: MiDaS Depth + MediaPipe Silhouette (On-Device AI)
    Caller->>Caller: Unproject Masked Depth to 3D Point Cloud
    Caller->>Caller: Quantize to 8-byte Vertices & Chunk Packets
    Caller->>AgoraA: sendStreamMessage(streamId, chunk)
    AgoraA->>AgoraB: Low-Latency Agora Data Stream
    AgoraB->>Callee: onStreamMessage(uid, streamId, chunk)
    Callee->>Callee: Reassemble Chunks into PointCloud
    Callee->>Callee: Render via OpenGL ES 2.0 (VolumetricVisualizer)
    Callee->>Callee: Touch & Drag to Orbit Caller in 3D!
```

---

## 🧩 Technical Deep-Dive

### 1. Neural Depth & Segmentation Pipeline ([`DepthEstimator.kt`](file:///c:/Users/share/Documents/IQOO-Hackathon/IQOO-Hackathon-LiveVolume/app/src/main/java/com/example/ml/DepthEstimator.kt))
- **Model Buffer Allocation**: Direct memory-mapped buffers (`MappedByteBuffer`) load `midas_small.tflite` and `selfie_segmentation.tflite` from `assets/` without copying.
- **Delegate Fallback Matrix**: Tries Android NNAPI delegate $\rightarrow$ GPU delegate (`tensorflow-lite-gpu-api`) $\rightarrow$ 4-thread CPU fallback. All delegates catch `Throwable` to safeguard against native linkage errors.
- **Depth Masking**:
  $$\text{MaskedDepth}(x, y) = \begin{cases} \text{NormDepth}(x, y) & \text{if } \text{PersonMask}(x, y) \ge 0.5 \\ 0.0 & \text{otherwise} \end{cases}$$
- **Pinhole Camera Unprojection**:
  $$X = \frac{x - c_x}{f_x} \cdot Z, \quad Y = -\frac{y - c_y}{f_y} \cdot Z$$
  where disparity $Z = 0.75 + (1.0 - d) \times 1.15$ meters.

### 2. High-Performance Point Cloud Codec ([`PointCloudStreamer.kt`](file:///c:/Users/share/Documents/IQOO-Hackathon/IQOO-Hackathon-LiveVolume/app/src/main/java/com/example/util/PointCloudStreamer.kt))
- **8-Byte Quantization**:
  - $X, Y, Z$: Scaled float $\rightarrow$ Int16 (`short`), providing sub-millimeter spatial fidelity.
  - Color: 24-bit RGB888 $\rightarrow$ 16-bit RGB565 (`short`), cutting color bandwidth in half.
- **Packet Protocol**:
  - 8-Byte Header: `Magic (0x564C)`, `FrameId (UInt16)`, `ChunkIdx (UInt8)`, `TotalChunks (UInt8)`, `PointCount (UInt16)`.
  - Max 120 points per packet (960 bytes payload + 8 bytes header = 968 bytes $\le 1024$ bytes Agora MTU).
- **Reassembly Buffer**: Stateful ring-buffer reassembles unordered chunks by `FrameId` and emits complete frames to the 3D renderer.

### 3. OpenGL ES 2.0 Orbit Renderer ([`PointCloudRenderer.kt`](file:///c:/Users/share/Documents/IQOO-Hackathon/IQOO-Hackathon-LiveVolume/app/src/main/java/com/example/ui/components/PointCloudRenderer.kt))
- Native `GLSurfaceView.Renderer` with hardware depth testing (`GL_DEPTH_TEST`).
- Spherical camera coordinates: Pitch ($[-85^\circ, +85^\circ]$) and Yaw ($0^\circ - 360^\circ$) rotated around object center via touch gestures.
- Custom vertex shader scales point size by camera distance (`gl_PointSize = uPointSize / gl_Position.w`), and fragment shader renders smooth anti-aliased circular points with true RGB colors.

---

## 🛠️ Project Structure

```
app/src/main/
├── assets/
│   ├── midas_small.tflite          # MiDaS v2.1 Small on-device depth estimation
│   └── selfie_segmentation.tflite  # MediaPipe Selfie Segmentation human silhouette
├── java/com/example/
│   ├── data/
│   │   ├── local/                  # Room Database: AppDatabase, CallRecordEntity, CallHistoryDao
│   │   └── repository/             # CallSignalingRepository (Firestore & FCM)
│   ├── ml/
│   │   ├── DepthEstimator.kt       # On-device TFLite depth & segmentation inference pipeline
│   │   └── PointCloud.kt           # Geometry models
│   ├── model/                      # Session models, user entities, mesh modes
│   ├── service/                    # AudioProcessingService, LiveVolumeMessagingService (FCM)
│   ├── ui/
│   │   ├── components/             # VolumetricVisualizer, PointCloudRenderer, DepthDebugPreview
│   │   ├── screens/                # CallScreen, MainShellScreen, ContactsScreen, HistoryScreen, SettingsScreen
│   │   └── theme/                  # Color, Type, Theme tokens (DESIGN.md specification)
│   └── util/
│       ├── AgoraManager.kt         # Agora RTC Video Engine & Data Stream transport singleton
│       ├── PointCloudStreamer.kt   # Point cloud quantization & chunk reassembly codec
│       ├── HapticsManager.kt       # Tactile feedback orchestrator
│       └── InAppNotificationManager.kt
└── AndroidManifest.xml             # Camera, audio, internet & foreground permissions
```

---

## 📲 Getting Started

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or newer
- **JDK 17** to build and run the app. Note that the *Firebase emulator suite* used by the integration tests additionally requires **JDK 21+**, and firebase-tools resolves `java` through `PATH` while ignoring `JAVA_HOME` — see [Testing](#-testing).
- **Android SDK 35** (Minimum SDK: 26 / Android 8.0 Oreo)
- Physical Android device with camera & microphone (e.g., iQOO / Vivo device)

### Setup & Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/haresh-sai06/IQOO-Hackathon-LiveVolume.git
   cd IQOO-Hackathon-LiveVolume
   ```

2. **Configure the backend** — LiveVolume runs on Firebase (Auth, Firestore signaling, Cloud Messaging) plus Agora RTC. Copy the template and fill in your Agora credentials:

   ```bash
   cp .env.example .env
   ```

   ```properties
   AGORA_APP_ID=your_agora_app_id_here
   # Public URL of the token service in ./server (required for production)
   AGORA_TOKEN_SERVER_URL=https://your-host.example.com/rtc-token
   ```

   The Secrets Gradle plugin turns these keys into `BuildConfig` fields, so no credentials are ever hard-coded. The same keys may be supplied as Gradle properties or environment variables.

   Then wire up Firebase:
   - Create a Firebase project and enable **Email/Password** authentication, **Cloud Firestore**, and **Cloud Messaging**.
   - Download `google-services.json` and place it at `app/google-services.json` (git-ignored — inject it from CI secrets for release builds).
   - Deploy the security rules: `firebase deploy --only firestore:rules`
   - Deploy the Agora token service described in [`server/README.md`](server/README.md).

   Without `google-services.json` the project still builds and runs, but accounts, contact discovery, call ringing and push are inert: Settings reports the backend as *not connected* and sign-in returns an explicit error instead of a fabricated local session.

3. **Build the project**:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on connected device**:
   ```bash
   adb install -r ./app/build/outputs/apk/debug/app-debug.apk
   ```

5. **Launch LiveVolume**:
   ```bash
   adb shell am start -n com.aistudio.livevolume.vknrz/com.example.MainActivity
   ```

---

## 🧪 Testing

Two suites cover the call handshake at different levels.

### Unit suite (no backend required)

```bash
./gradlew :app:testDebugUnitTest
```

`CallSignalingHandshakeTest` drives `CallSignalingRepository`'s state machine — invite, ring, duplicate suppression, dismissal, answer, decline, TTL expiry and teardown — over an in-memory transport, so the handshake is deterministic and needs no emulator. The same run also covers the Room call-history layer and the network-quality tiering.

### Call-signaling integration suite (real Firestore + real security rules)

```bash
scripts/verify-call-signaling.sh
```

`FirestoreSignalingEmulatorTest` runs on a connected device against the Firebase emulator suite, and verifies what an in-memory fake cannot: the document schema written to `calls`, the `receiverId` + `state` query, the `update()`-then-`set()` fallback in device-token registration, the shape of listener callbacks, and that the shipped [`firebase/firestore.rules`](firebase/firestore.rules) allow — and deny — exactly what the transport depends on.

The script exists because three prerequisites each fail in a way that looks like an unrelated timeout:

1. **Java 21.** firebase-tools v15 rejects older runtimes, and it looks up `java` on `PATH` rather than `JAVA_HOME`. The JDK on `PATH` is frequently 17, so the script falls back to the JetBrains Runtime bundled with Android Studio.
2. **`adb reverse`.** The device can only reach the emulator through a tunnel that is dropped every time the cable is unplugged. Without it, listeners stay silent and tests time out instead of erroring.
3. **Cleartext policy.** Only loopback cleartext is allowed, so an emulator host alias has to be passed with `--host 10.0.2.2`.

Flags:

| Flag | Effect |
| --- | --- |
| `--unit` | Run only the JVM suite; no device or emulator needed |
| `--keep-emulators` | Leave the emulator suite running afterwards |
| `--host HOST` | Skip `adb reverse` and address the emulator directly (use `10.0.2.2` on an AVD) |

If the emulator suite is already listening on ports 8080/9099, the script reuses it and leaves it running, so a long-lived instance in another terminal is never killed. Tunnels it creates are always removed on exit.

---

## 🎨 Design Guidelines

The app follows the design tokens outlined in `DESIGN.md`:
- **Primary Accent**: `LivePrimaryContainer` (`#2563EB`)
- **Success State**: `LiveSuccess` (`#10B981`)
- **Surface Elevation**: Dark HUD overlay (`#0F172A`) with rounded status bars and bottom action dock
- **Minimalist Controls**: 2-state pill toggle (`"2D"` / `"3D"`) with zero technical jargon visible to callers

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
