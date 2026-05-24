package com.example.smarthouseapp;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import android.os.Handler;
import android.os.Looper;

public class MonitoringActivity extends AppCompatActivity {

    public static MonitoringActivity instance = null;
    private LinearLayout llDeviceList;
    private RequestQueue queue;

    // /!\ REMPLACEZ "TON_ID_MAISON" PAR VOTRE VRAI IDENTIFIANT DE MAISON (ex: 12)
    private final String HOUSE_ID = "16";
    private final String URL_GET_DEVICES = "http://happyresto.enseeiht.fr/smartHouse/api/v1/devices/" + HOUSE_ID;
    private final String URL_POST_COMMAND = "http://happyresto.enseeiht.fr/smartHouse/api/v1/devices/";
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnableCode;
    private final int UPDATE_INTERVAL = 5000; // Temps de rafraîchissement en millisecondes (5 secondes)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_monitoring);

        llDeviceList = findViewById(R.id.ll_device_list);

        // Initialisation de la file d'attente Volley
        queue = Volley.newRequestQueue(this);

        // Définition de la tâche répétitive
        runnableCode = new Runnable() {
            @Override
            public void run() {
                // 1. On interroge le serveur
                fetchDevices();
                // 2. On dit au Handler de relancer ce même bout de code dans 5 secondes
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // L'application revient au premier plan : on lance le monitoring régulier
        handler.post(runnableCode);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // L'application n'est plus visible : on arrête proprement les requêtes réseau répétitives
        handler.removeCallbacks(runnableCode);
    }

    /**
     * Récupère la liste des appareils depuis l'API REST (Requête GET)
     */
    private void fetchDevices() {
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                URL_GET_DEVICES,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        // On nettoie l'interface avant de la reconstruire
                        llDeviceList.removeAllViews();

                        try {
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject device = response.getJSONObject(i);

                                // Extraction des données avec les clés en majuscules de l'API
                                int id = device.optInt("ID", -1);
                                String name = device.optString("NAME", "Appareil Inconnu");
                                String brand = device.optString("BRAND", "");
                                String model = device.optString("MODEL", "");
                                String dataString = device.optString("DATA", "");
                                int autonomy = device.optInt("AUTONOMY", -1);
                                boolean isOn = (device.optInt("STATE", 0) == 1);

                                // Formatage des détails de l'appareil
                                String displayName = "[" + brand + "-" + model + "] " + name;
                                String deviceInfo = "";
                                if (autonomy != -1) {
                                    deviceInfo += "Autonomy: " + autonomy + "% ";
                                }
                                if (!dataString.isEmpty()) {
                                    deviceInfo += "Data: " + dataString;
                                }
                                if (deviceInfo.isEmpty()) {
                                    deviceInfo = "Aucune donnée disponible";
                                }

                                // Création visuelle de la carte et ajout au conteneur principal
                                View deviceView = createDeviceView(id, displayName, deviceInfo, isOn);
                                llDeviceList.addView(deviceView);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(MonitoringActivity.this, "Erreur de lecture des données", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("MonitoringActivity", "Erreur GET: " + error.getMessage());
                        Toast.makeText(MonitoringActivity.this, "Erreur réseau lors de la récupération", Toast.LENGTH_LONG).show();
                    }
                }
        );

        queue.add(jsonArrayRequest);
    }

    /**
     * Génère dynamiquement le Layout (carte) pour un appareil connecté
     */
    private View createDeviceView(final int deviceId, String name, String info, final boolean isOn) {

        // 1. Conteneur de la carte (RelativeLayout)
        RelativeLayout layout = new RelativeLayout(this);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(Color.parseColor("#E0E0E0"));

        LinearLayout.LayoutParams marginParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        marginParams.setMargins(0, 0, 0, 16); // Espace entre chaque carte
        layout.setLayoutParams(marginParams);

        // 2. Texte : Nom de l'appareil
        TextView tvName = new TextView(this);
        tvName.setId(View.generateViewId());
        tvName.setText(name);
        tvName.setTextSize(16);
        tvName.setTextColor(Color.BLACK);

        // 3. Texte : Informations d'état / autonomie
        TextView tvInfo = new TextView(this);
        tvInfo.setText(info);
        tvInfo.setTextColor(Color.DKGRAY);
        tvInfo.setTextSize(14);

        RelativeLayout.LayoutParams infoParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        infoParams.addRule(RelativeLayout.BELOW, tvName.getId()); // Placé sous le nom
        tvInfo.setLayoutParams(infoParams);

        // 4. Bouton d'action : ON / OFF
        final Button btnToggle = new Button(this);
        btnToggle.setText(isOn ? "ON" : "OFF");

        RelativeLayout.LayoutParams btnParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE); // Tout à droite
        btnParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);   // Centré verticalement
        btnToggle.setLayoutParams(btnParams);

        // 5. Listener sur le bouton (Section 4 : Envoi de commandes POST)
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Désactivation temporaire pour éviter les clics frénétiques
                btnToggle.setEnabled(false);
                toggleDeviceState(deviceId);
            }
        });

        // Assemblage des éléments dans la carte
        layout.addView(tvName);
        layout.addView(tvInfo);
        layout.addView(btnToggle);

        return layout;
    }

    /**
     * Envoie une commande POST pour changer l'état d'un appareil (Section 4 du TP)
     */
    private void toggleDeviceState(final int deviceId) {
        if (MainActivity.estServeur) {
            // --- LE SERVEUR ---
            // Il garde l'ancien code : c'est lui qui fait la vraie requête HTTP à l'API
            faireRequeteApi(deviceId);
        } else {
            // --- LE CLIENT (Télécommande) ---
            // Il n'utilise pas Internet. Il envoie l'ordre au serveur par Bluetooth.
            if (MainActivity.threadDeCommunication != null) {
                String ordre = "TOGGLE:" + deviceId;
                MainActivity.threadDeCommunication.envoyerMessage(ordre);
                Toast.makeText(this, "Ordre envoyé au Serveur !", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Erreur : Bluetooth non connecté", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // J'ai juste isolé ton ancien code Volley dans cette méthode pour que ce soit propre
    public void faireRequeteApi(final int deviceId) {
        StringRequest stringRequest = new StringRequest(
                Request.Method.POST,
                URL_POST_COMMAND,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        fetchDevices(); // On réactualise l'affichage
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(MonitoringActivity.this, "Impossible de modifier l'état", Toast.LENGTH_SHORT).show();
                        fetchDevices();
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("deviceId", String.valueOf(deviceId));
                params.put("houseId", HOUSE_ID); // N'oublie pas que HOUSE_ID doit être ton vrai ID
                params.put("action", "turnOnOff");
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("Content-Type", "application/x-www-form-urlencoded");
                return params;
            }
        };

        queue.add(stringRequest);
    }
}