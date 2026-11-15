package com.example.lumachat;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.model.CallItem;

import java.util.List;

public class ActivityLogActivity extends AppCompatActivity {

    private LinearLayout callList;
    private TextView tvEmptyCalls;
    private TextView filterAll, filterIncoming, filterOutgoing, filterMissed;
    private String currentFilter = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity);

        callList = findViewById(R.id.callList);
        tvEmptyCalls = findViewById(R.id.tvEmptyCalls);

        findViewById(R.id.btnBackActivity).setOnClickListener(v -> finish());

        filterAll = findViewById(R.id.filterAll);
        filterIncoming = findViewById(R.id.filterIncoming);
        filterOutgoing = findViewById(R.id.filterOutgoing);
        filterMissed = findViewById(R.id.filterMissed);

        filterAll.setOnClickListener(v -> selectFilter("All"));
        filterIncoming.setOnClickListener(v -> selectFilter("Incoming"));
        filterOutgoing.setOnClickListener(v -> selectFilter("Outgoing"));
        filterMissed.setOnClickListener(v -> selectFilter("Missed"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCalls();
    }

    private void selectFilter(String filter) {
        currentFilter = filter;
        updateFilterStyles();
        loadCalls();
    }

    private void updateFilterStyles() {
        filterAll.setTextColor(Color.parseColor("All".equals(currentFilter) ? "#19152F" : "#77718A"));
        filterAll.setTypeface(null, "All".equals(currentFilter) ? Typeface.BOLD : Typeface.NORMAL);

        filterIncoming.setTextColor(Color.parseColor("Incoming".equals(currentFilter) ? "#19152F" : "#77718A"));
        filterIncoming.setTypeface(null, "Incoming".equals(currentFilter) ? Typeface.BOLD : Typeface.NORMAL);

        filterOutgoing.setTextColor(Color.parseColor("Outgoing".equals(currentFilter) ? "#19152F" : "#77718A"));
        filterOutgoing.setTypeface(null, "Outgoing".equals(currentFilter) ? Typeface.BOLD : Typeface.NORMAL);

        filterMissed.setTextColor(Color.parseColor("Missed".equals(currentFilter) ? "#19152F" : "#77718A"));
        filterMissed.setTypeface(null, "Missed".equals(currentFilter) ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void loadCalls() {
        callList.removeAllViews();
        List<CallItem> calls = DatabaseHelper.getInstance(this).getCalls(currentFilter);

        if (calls.isEmpty()) {
            tvEmptyCalls.setVisibility(View.VISIBLE);
        } else {
            tvEmptyCalls.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (CallItem call : calls) {
                View item = inflater.inflate(R.layout.call_item, callList, false);

                TextView tvAvatar = item.findViewById(R.id.callAvatar);
                TextView tvName = item.findViewById(R.id.callName);
                TextView tvDetail = item.findViewById(R.id.callDetail);
                TextView tvType = item.findViewById(R.id.callType);

                tvAvatar.setText(call.getContactAvatar());
                tvName.setText(call.getContactName());
                tvDetail.setText(call.getCallTime() + (!call.getDuration().isEmpty() ? " • " + call.getDuration() : ""));

                if ("Incoming".equalsIgnoreCase(call.getCallType())) {
                    tvType.setText("↙");
                    tvType.setTextColor(Color.parseColor("#58B88A"));
                } else if ("Outgoing".equalsIgnoreCase(call.getCallType())) {
                    tvType.setText("↗");
                    tvType.setTextColor(Color.parseColor("#7C5CBF"));
                } else {
                    tvType.setText("✕");
                    tvType.setTextColor(Color.parseColor("#E55353"));
                }

                // Call back on tap!
                item.setOnClickListener(v -> {
                    Intent callIntent = new Intent(ActivityLogActivity.this, CallActivity.class);
                    callIntent.putExtra("name", call.getContactName());
                    callIntent.putExtra("avatar", call.getContactAvatar());
                    startActivity(callIntent);
                });

                item.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle(call.getContactName())
                            .setMessage("Call log: " + call.getCallTime())
                            .setPositiveButton("Call Back", (d, w) -> {
                                Intent callIntent = new Intent(ActivityLogActivity.this, CallActivity.class);
                                callIntent.putExtra("name", call.getContactName());
                                callIntent.putExtra("avatar", call.getContactAvatar());
                                startActivity(callIntent);
                            })
                            .setNegativeButton("Close", null)
                            .show();
                    return true;
                });

                callList.addView(item);
            }
        }
    }
}
