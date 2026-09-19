# NOA Translator Test

Android German speech transcription prototype. The current build does not yet translate to Romanian or speak translations. Recognition uses the system speech service and may require internet access; this is not an offline engine.

## Build

Use Java 17, Gradle 8.13 and Android SDK 35. Run `gradle testDebugUnitTest assembleDebug`. The existing GitHub Actions workflow builds and signs the APK on pushes to main. Download `Noa-Translator-Test-APK` from the successful workflow run.

The Android module is `app`; the old top-level `src` directory is not compiled.

## Version 0.3

Phone reports from 0.2 showed repeated empty recognition sessions and unconfirmed Bluetooth input. These reports do not establish the exact device/provider failure.

- Reuses SpeechRecognizer after normal results or silence instead of destroying the service every utterance. Fault recovery and Stop still release it.
- Requests final results with stopListening after a 12-second ready window, then waits up to 10 seconds before fault recovery. Partial text and audio-level callbacks do not extend this deadline. Startup has a 15-second timeout.
- Pauses after four consecutive sessions without final text; errors have a separate five-attempt budget. A successful final transcript resets both budgets. Rate limiting waits 30 seconds.
- Waits up to 6 seconds for the requested Bluetooth communication route, followed by an 800 ms settling interval. Releases the requested route and restores the previous audio mode on Stop or leaving the app. This cannot force a separate speech provider to use the same input.
- Adds a separate four-second AudioRecord input test. It requests the selected input, reports the actual routed device and PCM RMS level, and retains no audio. Recognition is disabled during the test. Cancellation releases recording on the worker thread before another test or recognition can start.
- Shows optional speech-provider RMS feedback and a bounded event log with exact error codes. Copy diagnostic includes device/Android version and operational events, not the recognized transcript. Missing RMS callbacks do not prove silence.
- Makes the whole screen scrollable for large system font sizes.

### Focused phone test for 0.3

1. With Bluetooth off, use TEST MICROFON and speak for four seconds. Save the input name and level shown. Then Start and say a short German sentence with a pause.
2. Stop, connect/select the headset, repeat TEST MICROFON while speaking yourself, then test recognition. TV playback is a separate test; first establish near-mouth speech works.
3. If recognition fails, Stop, select COPIAZĂ DIAGNOSTICUL and share the text. Compare the audio probe result with recognition errors. The probe and the speech provider are separate consumers, so their input routes can differ.
4. Check Stop during a reconnect delay, leaving the app during a probe, and text persistence. Verify no recording/retry continues after leaving.

Automated tests cover empty-result and failure retry budgets, mixed error sequences, recovery after successful text and rate-limit delay. Physical microphone routing and recognizer callback timing still require phone testing.

## Version 0.2 (historical)

- Keeps completed phrases in a separate transcript rather than replacing them with each partial result. Saves the transcript locally between app launches; use the explicit clear button while stopped to erase it.
- Shows the current partial phrase separately. If interrupted without a final result, saves it with a `[provizoriu]` label instead of silently discarding it.
- Restarts recognition after final results and normal silence. Invalidates old callbacks, cancels pending restarts and destroys the previous recognizer before starting a new one.
- Recovers if startup, speech progress or final results stop arriving. Real service failures retry with increasing delays and stop after five consecutive failures. Displays errors instead of claiming to be listening indefinitely.
- Stops when the activity goes into the background and keeps the screen awake during foreground listening. Background listening is not implemented.
- Requests Bluetooth permission when selecting a headset, refreshes available devices on each selection and releases routing on leaving the app. The speech provider may ignore the requested communication route; test the actual microphone on the device.

## Phone acceptance test

1. Install 0.3 over the previous app; grant microphone permission. Say three separate German sentences with pauses. All three should remain visible.
2. Leave a short silence, then speak again. Recognition should resume until four consecutive sessions without text cause an explicit diagnostic pause.
3. Play a German video on the TV for 5 minutes with the app visible. Check that new phrases continue appearing and earlier phrases remain. System recognition consists of separate utterances and may miss words between restarts; this is not gapless streaming.
4. Press Stop during a phrase or retry countdown. It must stay stopped; a partial phrase may remain labelled provisional. Start again and verify earlier text remains.
5. Leave the app or rotate the phone. Return and verify saved text is present and the microphone is stopped; press Start to continue.
6. Test with no internet or a speech-service failure. The status should show retries or an actionable error, never an endless false listening state.
7. Test Bluetooth permission denial, no headset, then connected headset. Compare speaking close to the phone versus close to the headset. Do not infer actual input from routing selection alone.
8. Stop, clear text, close and reopen the app; the transcript should remain empty.

Compilation does not verify recognition quality, Bluetooth routing or timing on a physical phone.

