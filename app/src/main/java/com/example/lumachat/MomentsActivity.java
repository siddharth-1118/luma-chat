package com.example.lumachat;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MomentsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moments);

        findViewById(R.id.btnBackMoments).setOnClickListener(v -> finish());

        findViewById(R.id.btnAddMoment).setOnClickListener(v ->
                Toast.makeText(this, "Add a new moment!",
                        Toast.LENGTH_SHORT).show());

        findViewById(R.id.btnMyStory).setOnClickListener(v ->
                Toast.makeText(this, "Create your status!",
                        Toast.LENGTH_SHORT).show());
    }
}