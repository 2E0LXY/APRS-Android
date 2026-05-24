package uk.aprsnet.client;

import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

/**
 * APRS Net - main activity.
 *
 * Adds a custom User-Agent suffix ("APRSNetApp/1.0") so the aprsnet.uk
 * website can detect it is running inside the official Android client and
 * enable the client overlay (toolbar, GPS marker, single sign-on, etc.).
 *
 * The Capacitor bridge already exposes window.aprsClient via bridge.js on
 * the local launch page; once the WebView navigates to the live site the
 * site itself loads /client-overlay.js when it sees the APRSNetApp UA.
 */
public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        if (webView != null) {
            String ua = webView.getSettings().getUserAgentString();
            if (ua != null && !ua.contains("APRSNetApp")) {
                webView.getSettings().setUserAgentString(ua + " APRSNetApp/1.0");
            }
            // APRS map + live updates need these
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setGeolocationEnabled(true);
        }
    }
}