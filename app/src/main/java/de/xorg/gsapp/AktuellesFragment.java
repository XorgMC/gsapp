package de.xorg.gsapp;

import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class AktuellesFragment extends Fragment {

    String URI;
    private boolean isConnected = true;
    int lastID = 260;
    int PostID = 260;
    private ProgressDialog progressDialog;
    boolean isDark = false;
    String themeId;


    public AktuellesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_web, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null && getArguments().containsKey("theme")) {
            themeId = getArguments().getString("theme");
            isDark = (themeId.equals(Util.AppTheme.DARK));
        }

        //Variablen
        WebView Termine = (WebView) getView().findViewById(R.id.WebView);
        RelativeLayout FragFrm = (RelativeLayout) getView().findViewById(R.id.withers);

        switch(themeId) {
            case Util.AppTheme.DARK:
                FragFrm.setBackgroundResource(R.color.background_dark);
                Termine.setBackgroundResource(R.color.background_dark);
                break;
            case Util.AppTheme.LIGHT:
                FragFrm.setBackgroundResource(R.color.background_white);
                Termine.setBackgroundResource(R.color.background_white);
                break;
            case Util.AppTheme.YELLOW:
                FragFrm.setBackgroundResource(R.color.background_yellow);
                Termine.setBackgroundResource(R.color.background_yellow);
                break;
        }

        Termine.setWebViewClient(new MyWebViewClient() );

        Termine.getSettings().setJavaScriptEnabled(false);
        Termine.getSettings().setBuiltInZoomControls(false);

        isConnected = Util.hasInternet(this.getContext());

        if(isConnected)
            fetchLastPost();

        Termine.loadUrl("http://www.gymnasium-sonneberg.de/Informationen/Term/ausgebenK.php5");
    }

    public void fetchLastPost() {
        OkHttpClient.Builder b = new OkHttpClient.Builder();
        b.readTimeout(20, TimeUnit.SECONDS);
        b.connectTimeout(20, TimeUnit.SECONDS);
        b.followRedirects(false);

        OkHttpClient client = b.build();

        System.setProperty("http.keepAlive", "false");
        //Create a new progress dialog
        progressDialog = new ProgressDialog(AktuellesFragment.this.getContext());
        //Set the progress dialog to display a horizontal progress bar
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        //Set the dialog title to 'Loading...'
        progressDialog.setTitle("GSApp");
        //Set the dialog message to 'Loading application View, please wait...'
        progressDialog.setMessage("Lade Daten...");
        //This dialog can't be canceled by pressing the back key
        progressDialog.setCancelable(false);
        //This dialog isn't indeterminate
        progressDialog.setIndeterminate(true);
        //Display the progress dialog
        progressDialog.show();

        Request request = new Request.Builder()
                .url("https://www.gymnasium-sonneberg.de/Informationen/aktuelles.php5")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.e(e);
                if(AktuellesFragment.this.getActivity() == null)
                    return;

                AktuellesFragment.this.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(e.getMessage().contains("timeout")) {
                            Toast.makeText(AktuellesFragment.this.getContext(), "Warnung: Datenabruf fehlgeschlagen (Timeout) - Applet funktioniert nicht korrekt!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(AktuellesFragment.this.getContext(), "Warnung: Datenabruf fehlgeschlagen - Applet funktioniert nicht korrekt!", Toast.LENGTH_SHORT).show();
                        }


                        if (AktuellesFragment.this.progressDialog != null) {
                            AktuellesFragment.this.progressDialog.dismiss();
                        }
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if(!response.isSuccessful()) {
                    Timber.e("onResponse FAILED (" + response.code() + ")");
                    Toast.makeText(AktuellesFragment.this.getContext(), "Warnung: Datenabruf fehlgeschlagen - Applet funktioniert nicht korrekt!", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String result = response.body().string();

                if(AktuellesFragment.this.getActivity() == null)
                    return;

                AktuellesFragment.this.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        try {
                            Document d = Jsoup.parse(result);
                            Element ifr = d.select("iframe[id=inhframeAkt]").first();
                            String ID = ifr.attr("src").split("id=")[1];
                            lastID = Integer.parseInt(ID);
                            PostID = Integer.parseInt(ID);
                            openUrl("http://www.gymnasium-sonneberg.de/Informationen/Aktuell/ausgeben.php5?id=" + ID);
                        } catch(Exception e) { //TODO: Besseres Fehler-Catchen
                            Toast.makeText(AktuellesFragment.this.getContext(), "Warnung: Datenabruf fehlgeschlagen (JAVAEXC) - Applet funktioniert nicht korrekt!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.br_back:
                if(PostID == 2) {
                    Toast.makeText(AktuellesFragment.this.getContext(), "Fehler: Dies ist der erste Post!", Toast.LENGTH_SHORT).show();
                } else {
                    PostID = PostID - 1;
                    openUrl("http://www.gymnasium-sonneberg.de/Informationen/Aktuell/ausgeben.php5?id=" + PostID);
                }
                return true;
            case R.id.br_fwd:
                if(PostID == lastID) {
                    Toast.makeText(AktuellesFragment.this.getContext(), "Fehler: Dies ist der letzte Post!", Toast.LENGTH_SHORT).show();
                } else {
                    PostID = PostID + 1;
                    openUrl("http://www.gymnasium-sonneberg.de/Informationen/Aktuell/ausgeben.php5?id=" + PostID);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        Util.prepareMenu(menu, Util.NavFragments.AKTUELLES);
        menu.findItem(R.id.br_back).setIcon(Util.getThemedDrawable(this.getContext(), R.drawable.arrow_back, isDark));
        menu.findItem(R.id.br_fwd).setIcon(Util.getThemedDrawable(this.getContext(), R.drawable.arrow_next, isDark));
        super.onPrepareOptionsMenu(menu);
    }

    public void openUrl(String url) {
        WebView Speisen = (WebView) getView().findViewById(R.id.WebView);
        Speisen.loadUrl(url);
    }

    private class MyWebViewClient extends WebViewClient {

        final String OFFLINE = "<html><head></head><body bgcolor='#fed21b'><h2>Sie sind offline</h2><br><b>Es ist keine Internetverbindung verf&uuml;gbar! :(</b></body></html>";
        final String TIMEOUT = "<html><head></head><body bgcolor='#fed21b'><h2>TIMEOUT</h2><br><b>Der Server braucht zu lange,<br>um eine Antwort zu senden :(<br>Versuchen sie, einen<br>anderen Server in den<br>Einstellungen zu w&auml;hlen!<br></b></body></html>";
        final String GENERIC = "<html><head></head><body bgcolor='#fed21b'><h2>Fehler</h2><br><b>Bei der Verbindung zum Server<br>ist ein allgemeiner Fehler aufgetr. :(<br><br></b></body></html>";

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (isConnected) {
                view.loadUrl(url);
                return true;
            } else {
                view.loadData(OFFLINE, "text/html", "utf-8");
                return true;
            }
        }

        @Override
        public void onReceivedError (WebView view, int errorCode,
                                     String description, String failingUrl) {
            if (errorCode == ERROR_TIMEOUT) {
                view.stopLoading();
                view.loadData(TIMEOUT, "text/html", "utf-8");
            } else {
                view.stopLoading();
                view.loadData(GENERIC, "text/html", "utf-8");
            }
        }
    }
}
