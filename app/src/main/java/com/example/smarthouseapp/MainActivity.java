package com.example.smarthouseapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // Déclaration de notre bouton
    private Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Récupération du bouton depuis le fichier XML grâce à son ID
        btnStart = findViewById(R.id.btn_start);

        // 2. Création de l'action lors du clic
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Création d'un Intent pour aller de MainActivity vers MonitoringActivity
                Intent intent = new Intent(MainActivity.this, MonitoringActivity.class);

                // Lancement du nouvel écran
                startActivity(intent);
            }
        });
    }
}