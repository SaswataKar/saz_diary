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
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
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
    private boolean dictActive = false, dictRunning = false, segmented = false, onDevice = false, muted = false;
    private String dictLang = "en-IN";
    private int hardFails = 0, dictSession = 0;
    private long sessionStart = 0, lastLevel = 0;

    /* Short noises often come back as a tiny low-confidence "word". Drop those instead of writing them. */
    private boolean looksLikeNoise(String text, Bundle b) {
        float[] conf = b.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (conf == null || conf.length == 0 || conf[0] < 0) return false;          // recognizer gave no score: trust it
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        return conf[0] < 0.12f && words <= 2;
    }

    private void emitText(String event, Bundle b, boolean isFinal) {
        ArrayList<String> m = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (m == null || m.isEmpty() || m.get(0) == null) return;
        String text = m.get(0);
        if (isFinal && looksLikeNoise(text, b)) text = "";
        JSObject o = new JSObject();
        o.put("text", text);
        notifyListeners(event, o);
    }

    private void state(String st) {
        JSObject o = new JSObject();
        o.put("state", st);
        o.put("engine", onDevice ? "on-device" : "online");
        o.put("continuous", segmented);
        notifyListeners("dictState", o);
    }

    /* The system beep on every restart is distracting; mute it only while dictating. */
    private void muteBeeps(boolean on) {
        if (on == muted) return;
        try {
            android.media.AudioManager am = (android.media.AudioManager) getContext().getSystemService(android.content.Context.AUDIO_SERVICE);
            int dir = on ? android.media.AudioManager.ADJUST_MUTE : android.media.AudioManager.ADJUST_UNMUTE;
            for (int stream : new int[] { android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.STREAM_MUSIC }) {
                try { am.adjustStreamVolume(stream, dir, 0); } catch (Exception ignored) {}
            }
            muted = on;
        } catch (Exception ignored) {}
    }

    private void emitEnd(String reason) {
        dictRunning = false;
        muteBeeps(false);
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
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, onDevice);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L);
        if (segmented && Build.VERSION.SDK_INT >= 33) {
            i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS);
        }
        return i;
    }

    private void beginDict() {
        if (!dictActive) return;
        try {
            if (dict == null) {
                if (onDevice && Build.VERSION.SDK_INT >= 33) dict = SpeechRecognizer.createOnDeviceSpeechRecognizer(getContext());
                else dict = SpeechRecognizer.createSpeechRecognizer(getContext());
                dict.setRecognitionListener(dictListener);
            }
            sessionStart = SystemClock.elapsedRealtime();
            dictRunning = true;
            dict.startListening(dictIntent());
        } catch (Exception e) {
            if (onDevice) { onDevice = false; destroyDict(); restartSoon(100); return; }   // on-device engine refused: use the regular one
            JSObject o = new JSObject();
            o.put("message", String.valueOf(e.getMessage()));
            notifyListeners("dictError", o);
            dictActive = false;
            emitEnd("error");
        }
    }

    private void restartSoon(long ms) {
        if (dictActive) state("paused");
        main.postDelayed(() -> { if (dictActive && !dictRunning) beginDict(); }, ms);
    }

    private void destroyDict() {
        if (dict != null) {
            try { dict.destroy(); } catch (Exception ignored) {}
            dict = null;
        }
    }

    private final RecognitionListener dictListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { state("listening"); }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastLevel < 110) return;
            lastLevel = now;
            JSObject o = new JSObject();
            o.put("level", Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f)));
            notifyListeners("dictLevel", o);
        }
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override public void onPartialResults(Bundle b) { emitText("dictPartial", b, false); }

        @Override public void onSegmentResults(Bundle b) { hardFails = 0; emitText("dictFinal", b, true); }

        @Override public void onEndOfSegmentedSession() {
            dictRunning = false;
            if (dictActive) restartSoon(0); else emitEnd("stopped");
        }

        @Override public void onResults(Bundle b) {
            hardFails = 0;
            emitText("dictFinal", b, true);
            dictRunning = false;
            if (dictActive) restartSoon(0); else emitEnd("stopped");
        }

        @Override public void onError(int error) {
            dictRunning = false;
            if (!dictActive) { emitEnd("stopped"); return; }
            // Silence or a stray noise: simply keep listening. Never counts as a failure.
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) { restartSoon(0); return; }
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { fail("permission"); return; }
            boolean quick = SystemClock.elapsedRealtime() - sessionStart < 1500;
            // On-device engine can't do this language (not downloaded): switch to the regular engine.
            if (onDevice && (error == 12 || error == 13 || quick)) { onDevice = false; destroyDict(); restartSoon(100); return; }
            // Regular engine can't do long continuous sessions: switch to sentence-by-sentence.
            if (segmented && quick && error != SpeechRecognizer.ERROR_NETWORK && error != SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                segmented = false; destroyDict(); restartSoon(150); return;
            }
            hardFails++;
            if (hardFails > 6) { fail(error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ? "network" : "error"); return; }
            destroyDict();
            restartSoon(Math.min(1500, 250L * hardFails));   // busy / server hiccup: fresh recognizer, back off a little
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
            onDevice = false;
            if (Build.VERSION.SDK_INT >= 33) {
                try { onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext()); } catch (Exception ignored) {}
            }
            hardFails = 0;
            muteBeeps(true);
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

    /* ---------------- Secrets sealed in the phone's security chip ----------------
       An AES key lives in the Android Keystore and can only be used right after a fingerprint
       (strong biometric), checked by the hardware itself. New fingerprints added to the phone
       invalidate the key, so the spell has to be set up again. */
    private static final String KEY_ALIAS = "saz_diary_spell_key";

    private SecretKey newKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true);
        if (Build.VERSION.SDK_INT >= 30) b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        kg.init(b.build());
        return kg.generateKey();
    }

    private SecretKey existingKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return ks.containsAlias(KEY_ALIAS) ? (SecretKey) ks.getKey(KEY_ALIAS, null) : null;
    }

    private interface CipherDone { void run(Cipher c) throws Exception; }

    private void withFingerprint(PluginCall call, Cipher cipher, String title, CipherDone done) {
        BiometricPrompt prompt = new BiometricPrompt((FragmentActivity) getActivity(), ContextCompat.getMainExecutor(getContext()),
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    try {
                        done.run(result.getCryptoObject().getCipher());
                    } catch (Exception e) {
                        call.reject(String.valueOf(e.getMessage()), "crypto");
                    }
                }

                @Override
                public void onAuthenticationError(int code, CharSequence msg) {
                    String c = "error";
                    if (code == BiometricPrompt.ERROR_LOCKOUT || code == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) c = "lockout";
                    else if (code == BiometricPrompt.ERROR_USER_CANCELED || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_CANCELED) c = "cancelled";
                    call.reject(String.valueOf(msg), c);
                }
            });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Touch the fingerprint sensor")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Use passphrase")
            .build();
        prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
    }

    @PluginMethod
    public void sealSecret(PluginCall call) {
        final String text = call.getString("text", "");
        getActivity().runOnUiThread(() -> {
            try {
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, newKey());
                withFingerprint(call, c, "Seal your spell", ok -> {
                    byte[] ct = ok.doFinal(text.getBytes(StandardCharsets.UTF_8));
                    JSObject out = new JSObject();
                    out.put("iv", Base64.encodeToString(ok.getIV(), Base64.NO_WRAP));
                    out.put("ct", Base64.encodeToString(ct, Base64.NO_WRAP));
                    call.resolve(out);
                });
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()), "error");
            }
        });
    }

    @PluginMethod
    public void openSecret(PluginCall call) {
        final String iv = call.getString("iv", ""), ct = call.getString("ct", "");
        final String title = call.getString("title", "Open your diary");
        getActivity().runOnUiThread(() -> {
            try {
                SecretKey key = existingKey();
                if (key == null) { call.reject("No sealed spell on this phone", "no_key"); return; }
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                try {
                    c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
                } catch (KeyPermanentlyInvalidatedException e) {
                    call.reject("Fingerprints changed on this phone", "invalidated");
                    return;
                }
                withFingerprint(call, c, title, ok -> {
                    byte[] pt = ok.doFinal(Base64.decode(ct, Base64.NO_WRAP));
                    JSObject out = new JSObject();
                    out.put("text", new String(pt, StandardCharsets.UTF_8));
                    call.resolve(out);
                });
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()), "error");
            }
        });
    }

    @Override
    protected void handleOnDestroy() {
        stopRecognizer();
        dictActive = false;
        destroyDict();
        muteBeeps(false);
    }

    @Override
    protected void handleOnPause() {
        if (dictActive) { dictActive = false; if (dict != null) try { dict.stopListening(); } catch (Exception ignored) {} }
        muteBeeps(false);
    }
}
