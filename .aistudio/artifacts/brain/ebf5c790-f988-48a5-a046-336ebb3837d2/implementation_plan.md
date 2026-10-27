# Push-to-Talk Waveform Animation & Offline Clinical Speech Fallback

Implement smooth animated modal transitions, a real-time RMS amplitude-driven multi-bar audio waveform spectrum, and an offline-native keyword-based clinical command recognition engine for the Speech-to-Text module when disconnected from the Gemini API.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following choices were confirmed during interactive clarification and govern the implementation:

- **Confirmed Decision 1 (Waveform Style)**: Multi-bar audio spectrum with real-time RMS height scaling, smoothly animating individual frequency bars with stagger and damping based on live speech input.
- **Confirmed Decision 2 (Amplitude Dynamics)**: Direct RMS decibel levels from Android's `SpeechRecognizer.RecognitionListener.onRmsChanged()` feeding the UI state flow for responsive visualization.
- **Confirmed Decision 3 (Offline Clinical Fallback)**: Built-in regional keyword matcher for Telugu, Hindi, and English clinical symptoms and vitals commands, providing zero-latency offline triage and SOAP generation with an explicit offline fallback indicator.

---

## 1. Overview & Core Concept

- **What It Does**: Upgrades the Push-to-Talk clinical voice intake modal with a fluid fade-in/fade-out animated transition and an interactive 24-bar audio frequency visualizer that reacts dynamically to the clinician's spoken amplitude in real time. Adds an intelligent offline keyword extraction engine that recognizes regional clinical code-switching and voice commands (e.g., vitals dictation, emergency keywords) when offline or when the Gemini API is unreachable.
- **Target Audience / Persona**: Auxiliary Nurse Midwives (ANMs) and Medical Officers (MOs) practicing in rural Primary Health Centers (PHCs) and Ayushman Arogya Mandirs where connectivity is intermittent.
- **Key Value**: Provides clear, confidence-inspiring visual feedback while speaking, and ensures uninterrupted clinical intake even in total zero-connectivity conditions without blocking or dropping consultations.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Modal Entry**: Clinician taps the microphone icon on any SOAP intake card. The push-to-talk modal opens with a fluid fade-in (`fadeIn(tween(250))` combined with a subtle scale spring (`scaleIn(spring(dampingRatio = 0.8f))`).
2. **Audio Capture with Live Spectrum**: As the clinician dictates symptoms in Telugu, Hindi, or English, 24 rounded spectrum bars dynamically scale in height according to live RMS audio dB, creating an intuitive, glowing equalizer visualization in `PrimaryContainerTeal` and `PrimaryTeal`.
3. **Modal Exit**: Dismissing or completing dictation triggers a smooth fade-out and downward slide exit transition.
4. **Offline Detection & Fallback Routing**: When internet is offline or Gemini API times out, the app automatically switches to the `OfflineClinicalCommandEngine`, extracting chief complaints, vitals (BP, SpO2, Pulse), and triage tier with an "Offline Rule Engine" badge in the SOAP form.

### Visual Identity & Theme
- **Color Palette**: Clinical Emerald / Teal (`0xFF006A60`), Active Waveform (`0xFF148577`), Glowing Crests (`0xFF7BF5DF`), Background Dark (`0xFF00201D`), Surface Dark (`0xFF111C1A`).
- **Typography**: Clean monospace and tabular numbers for vitals and dB indicators (`12.sp`, `14.sp`), bold clinical headings (`18.sp`).
- **Interactive Feedback**: Ripple touch feedback on all buttons (minimum 48dp touch targets), pulsing recording indicator with active millisecond recording counter.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: RMS-Driven Waveform vs. AudioRecord Raw PCM**
  - *Chosen Approach*: Use `SpeechRecognizer.RecognitionListener.onRmsChanged` with an interpolated 24-bar spectral envelope.
  - *Why*: Avoids opening concurrent `AudioRecord` hardware streams which would conflict with Android's system `SpeechRecognizer` mic lock.
  - *Alternatives Considered*: Direct AudioRecord FFT (causes `ERROR_AUDIO` contention in SpeechRecognizer on Android devices).

- **Decision 2: Seamless Offline Clinical Rule Engine Fallback**
  - *Chosen Approach*: Implement a rule-based parser in `OfflineClinicalCommandEngine` that extracts Telugu keywords (*gunde noppi*, *aayasam*, *kadupu noppi*), Hindi keywords (*chhati dard*, *bukhar*, *saans phoolna*), and numeric vitals patterns (*BP 140/90*, *SpO2 92%*).
  - *Why*: Guarantees instant triage classification (RED/YELLOW/GREEN) and SOAP population within 20 milliseconds without network dependence.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│               Push-to-Talk Voice Modal                 │
│  - AnimatedVisibility (fadeIn + scaleIn / fadeOut)     │
│  - 24-Bar Dynamic Spectrum Waveform (RMS dB State)     │
│  - Live Partial & Final Transcript Display             │
└───────────────┬────────────────────────┬───────────────┘
                │                        │
       [RMS dB & Text]             [Parse Action]
                │                        │
                ▼                        ▼
┌─────────────────────────┐    ┌─────────────────────────┐
│ SpeechRecognitionManager│    │    Network / Status?    │
│ - Android SpeechListener│    └────────────┬────────────┘
│ - onRmsChanged -> State │                 │
└─────────────────────────┘       ┌─────────┴─────────┐
                                  ▼                   ▼
                            [Online]             [Offline]
                                  │                   │
                                  ▼                   ▼
                      ┌──────────────────────┐ ┌──────────────────────┐
                      │ GeminiClinicalParser │ │ OfflineCommandEngine │
                      │ - Gemini 2.5 Flash   │ │ - Regional Regex/Dict│
                      │ - Full SOAP & FHIR   │ │ - Vitals & Triage Tier│
                      └──────────┬───────────┘ └──────────┬───────────┘
                                 │                        │
                                 └──────────┬─────────────┘
                                            ▼
                               ┌─────────────────────────┐
                               │   SoapClinicalFormLayout│
                               │ - Offline Source Badge  │
                               │ - Editable SOAP Fields  │
                               └─────────────────────────┘
```

### Components to Update
1. **`SpeechRecognitionManager.kt`**: Smooth RMS decibel normalization with decay filter to prevent abrupt bar snapping.
2. **`VoiceRecordingModal.kt`**:
   - `AnimatedVisibility` wrapper with fade and scale transitions.
   - Enhanced 24-bar waveform spectrum using staggered animated heights and color gradients.
   - Offline fallback execution button and automatic timeout handling.
3. **`OfflineClinicalCommandEngine.kt`**:
   - Dedicated local parser for Telugu/Hindi/English symptom keywords.
   - Vitals voice command extraction (e.g., "BP 130 85", "Oxygen 94", "Pulse 90").
   - Instant triage tier calculation with FHIR condition placeholder generation.
4. **`SoapClinicalFormLayout.kt`**:
   - Visual badge displaying whether SOAP note was generated via Gemini Cloud or Offline Rule Fallback.

---

## 5. Verification Plan
- Verify compilation with `compile_applet`.
- Verify smooth modal fade transitions and live waveform dynamics without mic errors.
- Test offline clinical command extraction with simulated voice inputs.
