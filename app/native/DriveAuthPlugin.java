package com.saz.diary;

import android.app.Activity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import java.util.Collections;

/**
 * Asks Android for permission to use Google Drive with the phone's Google account
 * and hands the page a short-lived access token. Only files this app creates are visible to it.
 */
@CapacitorPlugin(name = "DriveAuth")
public class DriveAuthPlugin extends Plugin {
    private static final String DRIVE_FILE = "https://www.googleapis.com/auth/drive.file";
    private ActivityResultLauncher<IntentSenderRequest> launcher;
    private PluginCall pending;

    @Override
    public void load() {
        launcher = bridge.registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            PluginCall call = pending;
            pending = null;
            if (call == null) return;
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                call.reject("Google sign-in was cancelled", "cancelled");
                return;
            }
            try {
                AuthorizationResult r = Identity.getAuthorizationClient(getActivity()).getAuthorizationResultFromIntent(result.getData());
                send(call, r);
            } catch (ApiException e) {
                call.reject("Google sign-in failed (code " + e.getStatusCode() + ")", "failed");
            }
        });
    }

    private void send(PluginCall call, AuthorizationResult r) {
        String token = r.getAccessToken();
        if (token == null) {
            call.reject("Google didn't return Drive access", "failed");
            return;
        }
        JSObject out = new JSObject();
        out.put("accessToken", token);
        try {
            if (r.toGoogleSignInAccount() != null) out.put("email", r.toGoogleSignInAccount().getEmail());
        } catch (Exception ignored) {}
        call.resolve(out);
    }

    @PluginMethod
    public void authorize(PluginCall call) {
        final boolean interactive = Boolean.TRUE.equals(call.getBoolean("interactive", false));
        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_FILE)))
            .build();
        Identity.getAuthorizationClient(getActivity())
            .authorize(request)
            .addOnSuccessListener(r -> {
                if (r.hasResolution()) {
                    if (!interactive || r.getPendingIntent() == null) {
                        call.reject("Google sign-in needed", "needs_signin");
                        return;
                    }
                    pending = call;
                    try {
                        launcher.launch(new IntentSenderRequest.Builder(r.getPendingIntent().getIntentSender()).build());
                    } catch (Exception e) {
                        pending = null;
                        call.reject("Couldn't open Google sign-in", "failed");
                    }
                } else {
                    send(call, r);
                }
            })
            .addOnFailureListener(e -> call.reject("Google sign-in failed: " + e.getMessage(), "failed"));
    }
}
