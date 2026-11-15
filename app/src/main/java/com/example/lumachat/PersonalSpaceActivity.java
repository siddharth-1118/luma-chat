package com.example.lumachat;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class PersonalSpaceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_space);

        findViewById(R.id.btnBackPersonal).setOnClickListener(v -> finish());

        findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                Toast.makeText(this, "Edit profile coming soon!",
                        Toast.LENGTH_SHORT).show());

        findViewById(R.id.menuMyActivity).setOnClickListener(v ->
                openMessage("My Activity"));

        findViewById(R.id.menuSaved).setOnClickListener(v ->
                openMessage("Saved Messages"));

        findViewById(R.id.menuFiles).setOnClickListener(v ->
                openMessage("My Files"));

        findViewById(R.id.menuMedia).setOnClickListener(v ->
                openMessage("Shared Media"));

        findViewById(R.id.btnOpenSettings).setOnClickListener(v -> {
            startActivity(new android.content.Intent(
                    PersonalSpaceActivity.this, SettingsActivity.class));
        });
    }

    private void openMessage(String name) {
        Toast.makeText(this, name + " selected",
                Toast.LENGTH_SHORT).show();
    }
}