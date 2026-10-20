package com.saz.diary;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;

/**
 * Fingerprint check (Android BiometricPrompt, with the phone's PIN as a fallback on Android 11+)
 * and offline-only speech recognition for saying the passphrase. Speech here never uses the network:
 * if the phone has no on-device recognizer, the call is refused instead of falling back to the cloud.
 */
@CapacitorPlugin(name = "Guard")
public class GuardPlugin extends Plugin {
    private SpeechRecognizer recognizer;

    private int allowed() {
        return Build.VERSION.SDK_INT >= 30 ? (BIOMETRIC_STRONG | DEVICE_CREDENTIAL) : BIOMETRIC_STRONG;
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject out = new JSObject();
        int r = BiometricManager.from(getContext()).canAuthenticate(BIOMETRIC_STRONG);
        String bio = "unavailable";
        if (r == BiometricManager.BIOMETRIC_SUCCESS) bio = "ready";
        else if (r == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) bio = "none_enrolled";
        out.put("biometric", bio);
        boolean onDevice = false;
        if (Build.VERSION.SDK_INT >= 33) {
            try { onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext()); } catch (Exception ignored) {}
        }
        out.put("onDeviceSpeech", onDevice);
        call.resolve(out);
    }

    @PluginMethod
    public void verify(PluginCall call) {
        final String title = call.getString("title", "Open your diary");
        final String subtitle = call.getString("subtitle", "Touch the fingerprint sensor");
        getActivity().runOnUiThread(() -> {
            BiometricPrompt prompt = new BiometricPrompt((FragmentActivity) getActivity(), ContextCompat.getMainExecutor(getContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        JSObject ok = new JSObject();
                        ok.put("ok", true);
                        call.resolve(ok);
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        String c = "error";
                        if (code == BiometricPrompt.ERROR_LOCKOUT || code == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) c = "lockout";
                        else if (code == BiometricPrompt.ERROR_USER_CANCELED || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_CANCELED) c = "cancelled";
                        call.reject(String.valueOf(msg), c);
                    }
                });
            BiometricPrompt.PromptInfo.Builder info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(allowed());
            if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Cancel");
            try {
                prompt.authenticate(info.build());
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()), "error");
            }
        });
    }

    @PluginMethod
    public void hear(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33) {
            call.reject("This phone can't recognise speech offline", "offline_unavailable");
            return;
        }
        final String lang = call.getString("language");
        getActivity().runOnUiThread(() -> {
            try {
                if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext())) {
                    call.reject("This phone can't recognise speech offline", "offline_unavailable");
                    return;
                }
                stopRecognizer();
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(getContext());
                recognizer.setRecognitionListener(new RecognitionListener() {
                    private boolean done = false;

                    @Override
                    public void onResults(Bundle results) {
                        if (done) return;
                        done = true;
                        ArrayList<String> m = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        JSObject out = new JSObject();
                        out.put("matches", new JSArray(m == null ? new ArrayList<String>() : m));
                        call.resolve(out);
                        getActivity().runOnUiThread(GuardPlugin.this::stopRecognizer);
                    }

                    @Override
                    public void onError(int error) {
                        if (done) return;
                        done = true;
                        String c = "error";
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) c = "no_match";
                        else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) c = "permission";
                        call.reject("Speech recognition error " + error, c);
                        getActivity().runOnUiThread(GuardPlugin.this::stopRecognizer);
                    }

                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        JSObject s = new JSObject();
                        s.put("state", "ready");
                        notifyListeners("hearing", s);
                    }

                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {}
                    @Override public void onPartialResults(Bundle partialResults) {}
                    @Override public void onEvent(int eventType, Bundle params) {}
                });
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                if (lang != null) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                recognizer.startListening(intent);
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()), "error");
            }
        });
    }

    @PluginMethod
    public void stopHearing(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (recognizer != null) recognizer.stopListening();
            call.resolve();
        });
    }

    private void stopRecognizer() {
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    /* ---------------- Continuous dictation ----------------
       Android 13+: one "segmented" session that keeps listening through pauses and hands back each
       finished sentence (onSegmentResults). Older phones: the recognizer is restarted natively the
       moment a sentence ends, with no round trip to the page. stopDictation() lets the recognizer
       finish the sentence in progress, delivers it, then sends "dictEnd". */
    private SpeechRecognizer dict;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean dictActive = false, dictRunning = false, segmented = false;
    private String dictLang = "en-IN";
    private int quickFails = 0, dictSession = 0;
    private long sessionStart = 0;

    private void emitText(String event, Bundle b) {
        ArrayList<String> m = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (m == null || m.isEmpty() || m.get(0) == null) return;
        JSObject o = new JSObject();
        o.put("text", m.get(0));
        notifyListeners(event, o);
    }

    private void emitEnd(String reason) {
        dictRunning = false;
        JSObject o = new JSObject();
        o.put("reason", reason);
        notifyListeners("dictEnd", o);
        destroyDict();
    }

    private Intent dictIntent() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, dictLang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        if (segmented && Build.VERSION.SDK_INT >= 33) {
            i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS);
        }
        return i;
    }

    private void beginDict() {
        if (!dictActive) return;
        try {
            if (dict == null) {
                dict = SpeechRecognizer.createSpeechRecognizer(getContext());
                dict.setRecognitionListener(dictListener);
            }
            sessionStart = SystemClock.elapsedRealtime();
            dictRunning = true;
            dict.startListening(dictIntent());
        } catch (Exception e) {
            JSObject o = new JSObject();
            o.put("message", String.valueOf(e.getMessage()));
            notifyListeners("dictError", o);
            dictActive = false;
            emitEnd("error");
        }
    }

    private void restartSoon(long ms) {
        main.postDelayed(() -> { if (dictActive && !dictRunning) beginDict(); }, ms);
    }

    private void destroyDict() {
        if (dict != null) {
            try { dict.destroy(); } catch (Exception ignored) {}
            dict = null;
        }
    }

    private final RecognitionListener dictListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {
            JSObject o = new JSObject();
            o.put("state", "listening");
            notifyListeners("dictState", o);
        }
        @Override public void onBeginningOfSpeech() { quickFails = 0; }
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override public void onPartialResults(Bundle b) { emitText("dictPartial", b); }

        @Override public void onSegmentResults(Bundle b) { quickFails = 0; emitText("dictFinal", b); }

        @Override public void onEndOfSegmentedSession() {
            dictRunning = false;
            if (dictActive) restartSoon(0); else emitEnd("stopped");
        }

        @Override public void onResults(Bundle b) {
            emitText("dictFinal", b);
            dictRunning = false;
            if (dictActive) restartSoon(0); else emitEnd("stopped");
        }

        @Override public void onError(int error) {
            dictRunning = false;
            if (!dictActive) { emitEnd("stopped"); return; }
            boolean quick = SystemClock.elapsedRealtime() - sessionStart < 1500;
            if (quick) quickFails++;
            if (segmented && quick) { segmented = false; destroyDict(); restartSoon(150); return; }   // phone can't do long sessions: fall back
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { fail("permission"); return; }
            if (quickFails > 5) { fail(error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ? "network" : "error"); return; }
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) { restartSoon(0); return; }
            destroyDict();
            restartSoon(300);   // busy / client / server hiccup: start a fresh recognizer
        }
    };

    private void fail(String code) {
        dictActive = false;
        JSObject o = new JSObject();
        o.put("code", code);
        notifyListeners("dictError", o);
        emitEnd("error");
    }

    @PluginMethod
    public void startDictation(PluginCall call) {
        final String lang = call.getString("language", "en-IN");
        getActivity().runOnUiThread(() -> {
            if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
                call.reject("No speech recognizer on this phone", "unavailable");
                return;
            }
            dictLang = lang;
            dictSession++;
            destroyDict();
            dictActive = true;
            segmented = Build.VERSION.SDK_INT >= 33;
            quickFails = 0;
            beginDict();
            call.resolve();
        });
    }

    @PluginMethod
    public void stopDictation(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            dictActive = false;
            if (dict != null && dictRunning) {
                try { dict.stopListening(); } catch (Exception e) { emitEnd("stopped"); }
                final int session = dictSession;
                main.postDelayed(() -> { if (session == dictSession && !dictActive && dict != null) emitEnd("timeout"); }, 4000);   // never hang if no result comes
            } else {
                emitEnd("stopped");
            }
            call.resolve();
        });
    }

    @Override
    protected void handleOnDestroy() {
        stopRecognizer();
        dictActive = false;
        destroyDict();
    }
}
