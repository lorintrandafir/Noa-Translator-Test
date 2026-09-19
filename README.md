# NOA Translator Test

Android German speech transcription prototype. The current build does not yet translate to Romanian or speak translations. Recognition uses the system speech service and may require internet access; this is not an offline engine.

## Build

Use Java 17, Gradle 8.13 and Android SDK 35. Run `gradle assembleDebug`. The existing GitHub Actions workflow builds and signs the APK on pushes to main. Download `Noa-Translator-Test-APK` from the successful workflow run.

The Android module is `app`; the old top-level `src` directory is not compiled.

## Version 0.2

- Keeps completed phrases in a separate transcript rather than replacing them with each partial result. Saves the transcript locally between app launches; use the explicit clear button while stopped to erase it.
- Shows the current partial phrase separately. If interrupted without a final result, saves it with a `[provizoriu]` label instead of silently discarding it.
- Restarts recognition after final results and normal silence. Invalidates old callbacks, cancels pending restarts and destroys the previous recognizer before starting a new one.
- Recovers if startup, speech progress or final results stop arriving. Real service failures retry with increasing delays and stop after five consecutive failures. Displays errors instead of claiming to be listening indefinitely.
- Stops when the activity goes into the background and keeps the screen awake during foreground listening. Background listening is not implemented.
- Requests Bluetooth permission when selecting a headset, refreshes available devices on each selection and releases routing on leaving the app. The speech provider may ignore the requested communication route; test the actual microphone on the device.

## Phone acceptance test

1. Install 0.2 over the previous app; grant microphone permission. Say three separate German sentences with pauses. All three should remain visible.
2. Leave silence for 30 seconds, then speak again. Recognition should resume without pressing Start.
3. Play a German video on the TV for 5 minutes with the app visible. Check that new phrases continue appearing and earlier phrases remain. System recognition consists of separate utterances and may miss words between restarts; this is not gapless streaming.
4. Press Stop during a phrase or retry countdown. It must stay stopped; a partial phrase may remain labelled provisional. Start again and verify earlier text remains.
5. Leave the app or rotate the phone. Return and verify saved text is present and the microphone is stopped; press Start to continue.
6. Test with no internet or a speech-service failure. The status should show retries or an actionable error, never an endless false listening state.
7. Test Bluetooth permission denial, no headset, then connected headset. Compare speaking close to the phone versus close to the headset. Do not infer actual input from routing selection alone.
8. Stop, clear text, close and reopen the app; the transcript should remain empty.

Compilation does not verify recognition quality, Bluetooth routing or timing on a physical phone.
