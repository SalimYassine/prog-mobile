package com.example.smarthouseapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.UUID;

import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    // Gestionnaire Bluetooth unique du téléphone
    private BluetoothAdapter bluetoothAdapter;

    // Notre identifiant unique partagé (UUID)
    private static final UUID MON_UUID = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");

    // Les éléments de l'interface graphique
    private Button btnLancerServeur;
    private Button btnLancerClient;
    private TextView tvStatusConnexion;

    // Le thread de communication global, accessible depuis les autres écrans
    public static CommunicationThread threadDeCommunication = null;
    public static boolean estServeur = false; // Pour savoir qui fait quoi

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- NOUVEAU BLOC DE PERMISSIONS INTELLIGENT ---
        // Si le téléphone est sous Android 12 ou plus récent (Android 16)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                        101);
            }
        }
        // Si le téléphone est sous Android 11, 10 ou plus ancien
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        101);
            }
        }
        // --- FIN DU NOUVEAU BLOC ---

        // Initialisation du gestionnaire Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Liaison des boutons avec le fichier XML
        btnLancerServeur = findViewById(R.id.btn_lancer_serveur);
        btnLancerClient = findViewById(R.id.btn_lancer_client);
        tvStatusConnexion = findViewById(R.id.tv_status_connexion);

        // Action lors du clic sur le bouton SERVEUR
        btnLancerServeur.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preparerInterfaceAttente(btnLancerServeur, btnLancerClient, "SERVEUR EN ATTENTE", "*Attente de connexion d'un client*");
                // On instancie et on lance notre Thread d'écoute Serveur
                new ServeurThread().start();
            }
        });

        // Action lors du clic sur le bouton CLIENT
        btnLancerClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preparerInterfaceAttente(btnLancerClient, btnLancerServeur, "CLIENT EN ATTENTE", "*Attente de connexion au serveur*");
                new ClientThread().start();
            }
        });
    }

    // Méthode utilitaire pour modifier l'affichage lors de l'attente
    private void preparerInterfaceAttente(Button btnClique, Button btnACacher, String texteBouton, String texteStatut) {
        btnACacher.setVisibility(View.GONE);
        btnClique.setText(texteBouton);
        btnClique.setEnabled(false);
        tvStatusConnexion.setText(texteStatut);
        tvStatusConnexion.setVisibility(View.VISIBLE);
    }

    // Thread d'écoute pour le Serveur (Le Hub domotique)
    // On ajoute cette ligne pour dire à Android qu'on gère la sécurité nous-mêmes
    @SuppressLint("MissingPermission")
    private class ServeurThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public ServeurThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("MonServeur", MON_UUID);
            } catch (SecurityException | IOException e) {
                e.printStackTrace();
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (mmServerSocket == null) {
                return;
            }

            BluetoothSocket socket = null;
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }

                if (socket != null) {
                    // --- LE CODE EST PLACÉ ICI : LA CONNEXION A RÉUSSI ---
                    estServeur = true;
                    threadDeCommunication = new CommunicationThread(socket);
                    threadDeCommunication.start();

                    // On ouvre la page de monitoring
                    startActivity(new Intent(MainActivity.this, MonitoringActivity.class));
                    // -----------------------------------------------------

                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }

    // Thread pour le Client (La télécommande utilisateur)
    @SuppressLint("MissingPermission")
    private class ClientThread extends Thread {
        private BluetoothSocket mmSocket = null;

        public ClientThread() {
            java.util.Set<android.bluetooth.BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

            if (pairedDevices.size() > 0) {
                android.bluetooth.BluetoothDevice device = pairedDevices.iterator().next();
                try {
                    mmSocket = device.createRfcommSocketToServiceRecord(MON_UUID);
                } catch (SecurityException | IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void run() {
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException e) {
                e.printStackTrace();
            }

            if (mmSocket != null) {
                try {
                    mmSocket.connect();

                    // --- LE CODE EST PLACÉ ICI : LA CONNEXION A RÉUSSI ---
                    estServeur = false;
                    threadDeCommunication = new CommunicationThread(mmSocket);
                    threadDeCommunication.start();

                    // On ouvre la page de monitoring
                    startActivity(new Intent(MainActivity.this, MonitoringActivity.class));
                    // -----------------------------------------------------

                } catch (SecurityException | IOException connectException) {
                    try {
                        mmSocket.close();
                    } catch (IOException closeException) {
                        closeException.printStackTrace();
                    }
                }
            }
        }
    }
    // Le Thread qui gère l'envoi et la réception des messages
    public class CommunicationThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final java.io.InputStream mmInStream;
        private final java.io.OutputStream mmOutStream;

        public CommunicationThread(BluetoothSocket socket) {
            mmSocket = socket;
            java.io.InputStream tmpIn = null;
            java.io.OutputStream tmpOut = null;

            // On récupère les flux de lecture et d'écriture du socket Bluetooth
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // Un tampon pour stocker les données reçues
            int bytes; // Le nombre d'octets reçus

            // Le thread tourne en boucle pour écouter les messages en permanence
            while (true) {
                try {
                    // On lit les données venant de l'autre téléphone
                    bytes = mmInStream.read(buffer);

                    // On transforme ces octets en texte lisible (String)
                    String messageRecu = new String(buffer, 0, bytes);

                    // Si on est le Serveur et qu'on reçoit un ordre du type "TOGGLE:294"
                    if (estServeur && messageRecu.startsWith("TOGGLE:")) {
                        // On extrait l'ID de l'appareil (ex: "294")
                        String idString = messageRecu.split(":")[1].trim();
                        final int idAppareil = Integer.parseInt(idString);

                        // On doit exécuter la requête HTTP sur le fil principal de l'interface
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Ordre reçu pour l'appareil " + idAppareil, Toast.LENGTH_SHORT).show();

                                // --- LA MAGIE OPÈRE ICI ---
                                // Si la page des appareils est bien ouverte, on lance la requête web !
                                if (MonitoringActivity.instance != null) {
                                    MonitoringActivity.instance.faireRequeteApi(idAppareil);
                                }
                            }
                        });
                    }

                } catch (IOException e) {
                    // Erreur ou déconnexion, on sort de la boucle
                    break;
                }
            }
        }

        // Méthode simple que le Client appellera pour envoyer un ordre au Serveur
        public void envoyerMessage(String message) {
            try {
                // On transforme le texte en octets et on l'envoie dans le tuyau
                mmOutStream.write(message.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}