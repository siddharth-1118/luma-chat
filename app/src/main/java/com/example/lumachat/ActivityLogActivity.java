package com.example.lumachat;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ActivityLogActivity extends AppCompatActivity {

    private LinearLayout callList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity);

        callList = findViewById(R.id.callList);

        findViewById(R.id.btnBackActivity).setOnClickListener(v -> finish());

        String[] names = {"Ananya Sharma", "Rahul", "Sravani", "Priya"};
        String[] avatars = {"A", "R", "S", "P"};
        String[] details = {
                "Incoming • 10:30 AM",
                "Outgoing • 9:15 AM",
                "Missed • Yesterday",
                "Incoming • Yesterday"
        };
        String[] types = {"↙", "↗", "✕", "↙"};

        for (int i = 0; i < callList.getChildCount(); i++) {
            View item = callList.getChildAt(i);
            ((TextView) item.findViewById(R.id.callName)).setText(names[i]);
            ((TextView) item.findViewById(R.id.callAvatar)).setText(avatars[i]);
            ((TextView) item.findViewById(R.id.callDetail)).setText(details[i]);
            ((TextView) item.findViewById(R.id.callType)).setText(types[i]);
        }

        findViewById(R.id.filterAll).setOnClickListener(v -> showCalls("All"));
        findViewById(R.id.filterIncoming).setOnClickListener(v -> showCalls("Incoming"));
        findViewById(R.id.filterOutgoing).setOnClickListener(v -> showCalls("Outgoing"));
        findViewById(R.id.filterMissed).setOnClickListener(v -> showCalls("Missed"));
    }

    private void showCalls(String filter) {
        for (int i = 0; i < callList.getChildCount(); i++) {
            View item = callList.getChildAt(i);
            TextView detail = item.findViewById(R.id.callDetail);
            String value = detail.getText().toString();

            item.setVisibility(filter.equals("All") ||
                    value.startsWith(filter) ? View.VISIBLE : View.GONE);
        }
    }
}