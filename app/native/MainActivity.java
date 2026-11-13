package com.saz.diary;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DriveAuthPlugin.class);
        registerPlugin(GuardPlugin.class);
        super.onCreate(savedInstanceState);

        /* The app draws behind the status and navigation bars (edge to edge), so it has to make room
           for them itself. This is done once, at the top of the screen's view tree, which receives the
           keyboard's size on every Android version: the page always sits between the status bar and
           whichever is taller, the navigation bar or the open keyboard. */
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WebView web = getBridge().getWebView();
        ViewCompat.setOnApplyWindowInsetsListener(web, null);          // drop any per-page edge handling
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) web.getLayoutParams();
        mlp.setMargins(0, 0, 0, 0);
        web.setLayoutParams(mlp);

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
