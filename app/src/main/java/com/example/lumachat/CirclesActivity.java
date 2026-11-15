package com.example.lumachat;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class CirclesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_circles);

        findViewById(R.id.btnBackCircles).setOnClickListener(v -> finish());

        findViewById(R.id.btnCreateGroup).setOnClickListener(v ->
                Toast.makeText(this,
                        "Group creation will be added next!",
                        Toast.LENGTH_SHORT).show());

        findViewById(R.id.btnAddCircle).setOnClickListener(v ->
                Toast.makeText(this,
                        "Create a new Circle",
                        Toast.LENGTH_SHORT).show());
    }
}