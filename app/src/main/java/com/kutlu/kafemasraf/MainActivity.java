package com.kutlu.kafemasraf;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;

/**
 * Kafe Masraf: masraf takip sayfasını Chrome'un uygulama içi sekmesinde açar.
 * Chrome'daki Claude girişi kullanıldığı için sayfa sahibi kayıt girebilir,
 * diğerleri yalnızca görüntüler. Veriler her zaman güncel sayfadan gelir.
 */
public class MainActivity extends Activity {

    private static final String URL = "https://claude.ai/artifact/NmTZra4bS3W38BT8fujGuH";
    private boolean leftForTab = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openPage();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openPage();
    }

    @Override
    protected void onStop() {
        super.onStop();
        leftForTab = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Kullanıcı sekmeyi kapatıp geri geldiyse uygulamayı kapat.
        if (leftForTab) finish();
    }

    private void openPage() {
        leftForTab = false;
        Uri uri = Uri.parse(URL);
        CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(0xFF7A4A24)
                .setNavigationBarColor(0xFF7A4A24)
                .build();
        CustomTabsIntent tab = new CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colors)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build();
        try {
            tab.launchUrl(this, uri);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "Tarayıcı bulunamadı. Lütfen Chrome'u yükleyin.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
