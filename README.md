# NOA Translator Test

Android German speech transcription, Romanian translation and optional offline Romanian speech playback prototype. Version 0.7 uses ML Kit on-device translation plus a small, explicitly labelled set of reviewed whole expressions. Recognition uses the system speech service and may require internet access; this is not an offline engine.

## Build

Use Java 17, Gradle 8.13 and Android SDK 35. Run `gradle testDebugUnitTest assembleDebug`. The existing GitHub Actions workflow builds and signs the APK on pushes to main. Download `Noa-Translator-Test-APK` from the successful workflow run.

The Android module is `app`; the old top-level `src` directory is not compiled.

## Version 0.7 — grouped translation and Romanian audio

- Groups adjacent final recognition fragments before translation: 1.4 seconds without reported speech activity, with an 8-second/300-character bound or terminal punctuation. This is a heuristic, not a guarantee of complete sentences or speaker separation. German words are preserved, including repetitions; partial results are never silently promoted to finals. Pending final text is saved and flushed on Stop/background.
- Adds exact whole-expression Romanian translations for common greetings and thanks, including `danke schön` → `Mulțumesc frumos.` These rows are labelled as locally reviewed expressions. Arbitrary phrases continue through ML Kit; no global word substitutions or guessed reconstruction of `in Lorin`.
- ML Kit translates German/Romanian via English; grouping may help context but cannot repair the underlying model. It is not equivalent to online Google Translate. Source: https://developers.google.com/ml-kit/language/translation . Translation quality must be assessed on the device; no claim that the TV examples are all fixed.
- Adds optional automatic reading of newly translated final phrases and an individual replay button. Existing history and provisional text are not automatically spoken. Audio starts disabled and is stopped on Stop/Clear/background. Switching off or cancelling audio prevents late pending translations from starting playback.
- Selects an installed, offline Romanian Android TTS voice; missing voice/engine gives an actionable status and a button to Android TTS settings. Does not silently select a network voice. Playback is serialized, bounded to five waiting phrases and has a 60-second missing-callback timeout.
- Uses the Android media output (select Soundcore in the system media-output selector); does not force a headset output or change the earbud DSP. Audio mode requires the phone microphone selection, avoiding the call route. Recognition pauses during speech playback and resumes afterward to prevent feedback. Words spoken during playback are not captured: this release is **not simultaneous interpretation**. Playback may use the phone speaker if that is the selected media output.
- Foreground-only operation remains: locking the screen or leaving the app stops listening and playback. Pocket/background operation is not implemented.

### Phone check for 0.7

Install over 0.6.1 without uninstalling. The feature branch also runs the existing signed build workflow. Select the phone microphone in NOA; connect Soundcore as Android's media output. If needed install an offline Romanian voice in Android TTS settings and reopen NOA.

1. Say `danke schön`, pause, and verify `Mulțumesc frumos.` plus the reviewed-expression label.
2. With automatic reading OFF, replay the same TV fragment and compare grouped German/RO text with 0.6.1. This isolates translation from the deliberate audio pauses.
3. Press `ASCULTĂ ÎN ROMÂNĂ` on one final row and verify Romanian speech is heard in Soundcore. Enable automatic reading, say one phrase, wait for the spoken translation, and then say another. Verify the mic resumes.
4. Stop playback mid-phrase, stop listening while translation is pending, clear history, and leave/reopen the app. No cancelled or historical text should speak unexpectedly. Disconnect the headset and verify the selected Android media output before continuing.
5. Missing Romanian voice must show a useful status, without blocking text translation or recognition.

Automated tests cover grouping boundaries and conservative expression matching plus existing recognition retry and translation-queue tests. Android TTS voice availability, output routing, model quality and acoustic feedback require the physical phone.

## Version 0.6.1

Compatibility investigation after the HONOR/Android 16 test of 0.6 returned eight consecutive NO_MATCH errors and no logged partial or final text. The screenshot contains a saved translation, but the supplied session log does not show any new successful result. This does not prove microphone failure or a formatting incompatibility.

- Removes the optional formatting extra introduced in 0.6, restoring exactly the recognition intent extras used by 0.5. Retains live previews, translation, Bluetooth routing, retry timings and history.
- Logs the recognition configuration on Start and the partial update count on error, including zero, so a later test can distinguish missing intermediate results from translation failures.
- This is a targeted compatibility rollback, not a confirmed fix. Test the same clip and microphone setup as 0.5; also speak one short German phrase yourself. Install over 0.6 without uninstalling. Copy diagnostics after stopping.

## Version 0.6

- Shows a labelled, temporary DE/RO preview while speaking, when the speech provider supplies partial text. Requests optional Android 13+ punctuation/capitalization with the latency optimization strategy; unsupported providers may ignore it.
- Preview translation starts at most once per 1.2 seconds, allows only one in-flight preview, and coalesces changes to the latest text. Each translation is displayed with the exact German snapshot it translated; newer German text is labelled separately. Continuous partial updates do not endlessly debounce the preview.
- Final results clear the preview and use the existing persistent translation queue. Preview callbacks from previous utterances, Stop, Clear or a destroyed Activity cannot reappear. Preview failures cannot cause retry loops or stop recognition. Previews are never committed as final translations.
- Logs the first partial-result delay, partial update count and preview translation duration without transcript contents. Keeps the 12-second safety deadline and 150 ms restart behavior. This release does not claim sentence-perfect segmentation, improved model accuracy or gapless capture; it asks for punctuation and reduces display latency when partials are available.
- Protects model-ready state from a late initial model-check callback after successful download.

### Phone check for 0.6

Install over 0.5. With translation ready, speak a longer German sentence and watch the labelled provisional DE/RO block below the microphone level. Check that the final translation is saved once, with no preview returning afterward. Repeat a phrase, stop during speech, and restart: old previews must not return. Test the same TV clip and copy diagnostics so partial support and timings can be checked. Previously downloaded models and history are reused. No guaranteed intermediate text if the speech provider withholds partial results. TTS remains unimplemented.

## Version 0.5

- German final phrases are translated to Romanian on the phone using ML Kit 17.0.3. Each phrase keeps its own original and translation, including repeated phrases.
- Prepare translation once on Wi-Fi to download German and Romanian models. Translation works locally after download; system speech recognition can still require internet. Models can be downloaded again if removed by the system.
- A serial asynchronous translation queue is independent of recognizer restart timers. Failure keeps the original and offers retry. Clear during translation cannot resurrect deleted text. Provisional speech is labelled and not translated.
- Existing German history migrates into paired entries; originals, translations and pending work survive reopening. Recognition still stops in the background. Translation callbacks are ignored after Activity destruction and the translator is closed.
- Translation status and success/failure events are included in diagnostics without phrase content.
- Translation is automatic and may be inaccurate, especially for short fragments and names. No text-to-speech or background listening yet.

### Phone check for 0.5

Install over 0.4 without uninstalling. Connect Wi-Fi and press the translation preparation button. Wait for the ready message; existing final phrases should receive Romanian translations. Select headset and speak three distinct short German sentences. Confirm each DE line has its matching RO line. Pause 30 seconds and resume. Stop, reopen and check both languages persist. Clear while translations are pending and ensure old rows do not return. Build/unit tests do not validate model downloads, translation quality or real-device audio behavior.

## Version 0.4

Focus: reduce app-imposed gaps after the 0.3 phone test confirmed successful recognition with the headset selected and the phone in another room.

- Normal results and normal-length empty/silence sessions restart after 150 ms (previously 800 ms for results and 1.5/3/4.5 seconds for empty sessions).
- Normal silence no longer stops recognition after four empty sessions. Stop and background lifecycle handling still cancel pending restarts and release the recognizer.
- An empty response in under 750 ms is treated as a possible provider rejection loop. Six consecutive such responses stop the loop, with increasing delays before that. This is a timing heuristic, not proof of an audio fault.
- Actual service errors retain their independent five-attempt budget and rate limiting waits 30 seconds. Only successful text or an explicit new Start clears that budget.
- Diagnostic events have millisecond timestamps and retain the last 100 entries. The final-result/error-to-next-ready interval is recorded; this is callback timing, not a direct measurement of continuous microphone capture.
- The audio level label no longer says “does not identify the microphone”, which was easy to misread as an error. Communication routing still does not prove the speech provider's actual input.

This remains utterance-based system recognition, not gapless streaming. Time spent waiting for final results and the provider's own startup latency can still lose speech. No changes to the recognition engine, Bluetooth routing, transcript storage or audio probe are included.

### Phone check for 0.4

With the headset selected, speak 10 short German phrases at a normal pace. Pause silently for 30 seconds, then speak again without pressing Start. Check which phrases are retained and copy the diagnostic after Stop. Also check Stop during a restart delay; it must remain stopped. Tests cover prolonged silence, rapid empty loops, mixed errors, retry reset and rate limits; hardware behavior still needs phone testing.

## Version 0.3 (historical)

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

1. Install 0.4 over the previous app; grant microphone permission. Say three separate German sentences with pauses. All three should remain visible.
2. Leave silence for 30 seconds, then speak again. Normal silence should not stop recognition.
3. Play a German video on the TV for 5 minutes with the app visible. Check that new phrases continue appearing and earlier phrases remain. System recognition consists of separate utterances and may miss words between restarts; this is not gapless streaming.
4. Press Stop during a phrase or retry countdown. It must stay stopped; a partial phrase may remain labelled provisional. Start again and verify earlier text remains.
5. Leave the app or rotate the phone. Return and verify saved text is present and the microphone is stopped; press Start to continue.
6. Test with no internet or a speech-service failure. The status should show retries or an actionable error, never an endless false listening state.
7. Test Bluetooth permission denial, no headset, then connected headset. Compare speaking close to the phone versus close to the headset. Do not infer actual input from routing selection alone.
8. Stop, clear text, close and reopen the app; the transcript should remain empty.

Compilation does not verify recognition quality, Bluetooth routing or timing on a physical phone.

