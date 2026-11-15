package com.example.lumachat.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.model.Conversation;
import com.example.lumachat.model.Message;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

public class P2PChatManager {

    public interface OnMessageReceivedListener {
        void onMessageReceived(Message message);
    }

    public interface OnPeerStatusListener {
        void onStatusChanged(boolean isConnected, String message);
    }

    public enum Mode {
        NONE, DIRECT_TCP, SERVER_TCP, HTTP_RELAY
    }

    public static final int DEFAULT_PORT = 8888;
    private static final String PREF_KEY_LAST_ADDR = "luma_last_peer_address";
    private static P2PChatManager instance;

    // TCP Sockets
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter outWriter;
    private BufferedReader inReader;

    // HTTP Relay state
    private String httpBaseUrl = "";
    private long lastSeenTimestamp = 0;
    private volatile boolean isHttpPollingActive = false;
    private Thread sseThread;
    private Thread pollThread;

    private Mode currentMode = Mode.NONE;
    private boolean isConnected = false;
    private String connectedPeerIp = "";

    // Message deduplication
    private final Set<String> processedKeys = Collections.synchronizedSet(new LinkedHashSet<>());

    private OnMessageReceivedListener messageListener;
    private OnPeerStatusListener statusListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;

    public static synchronized P2PChatManager getInstance() {
        if (instance == null) {
            instance = new P2PChatManager();
        }
        return instance;
    }

    public void init(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setMessageListener(OnMessageReceivedListener listener) {
        this.messageListener = listener;
    }

    public void setStatusListener(OnPeerStatusListener listener) {
        this.statusListener = listener;
    }

    public boolean isConnected() {
        if (currentMode == Mode.HTTP_RELAY) {
            return isConnected && !httpBaseUrl.isEmpty();
        }
        return isConnected && clientSocket != null && !clientSocket.isClosed();
    }

    public Mode getCurrentMode() {
        return currentMode;
    }

    public String getConnectedPeerIp() {
        return connectedPeerIp;
    }

    public String getLastSavedAddress(Context context) {
        SharedPreferences sp = context.getSharedPreferences("luma_chat_net", Context.MODE_PRIVATE);
        return sp.getString(PREF_KEY_LAST_ADDR, "https://nancey-pandemoniacal-candra.ngrok-free.dev");
    }

    public void saveLastAddress(Context context, String addr) {
        SharedPreferences sp = context.getSharedPreferences("luma_chat_net", Context.MODE_PRIVATE);
        sp.edit().putString(PREF_KEY_LAST_ADDR, addr).apply();
    }

    public String getLocalIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ipInt = wm.getConnectionInfo().getIpAddress();
                if (ipInt != 0) {
                    return Formatter.formatIpAddress(ipInt);
                }
            }
        } catch (Exception ignored) {}

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        return "127.0.0.1";
    }

    // ==========================================
    // 1. PHONE AS SERVER (TCP HOST)
    // ==========================================
    public void startServer(int port) {
        disconnect();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                currentMode = Mode.SERVER_TCP;
                notifyStatus(false, "Hosting on port " + port + "... Waiting for friend.");

                clientSocket = serverSocket.accept();
                connectedPeerIp = clientSocket.getInetAddress().getHostAddress();
                setupStreams(clientSocket);
                isConnected = true;
                notifyStatus(true, "Peer connected: " + connectedPeerIp);

                listenForIncomingTcp();
            } catch (Exception e) {
                currentMode = Mode.NONE;
                isConnected = false;
                notifyStatus(false, "Host stopped: " + e.getMessage());
            }
        }).start();
    }

    // ==========================================
    // 2. CONNECT TO PEER (AUTO DETECT HTTP OR TCP)
    // ==========================================
    public void connectToPeer(String rawInput, int defaultPort) {
        if (rawInput == null || rawInput.trim().isEmpty()) return;
        final String input = rawInput.trim();

        if (appContext != null) {
            saveLastAddress(appContext, input);
        }

        disconnect();

        // Check if user provided an HTTP / Cloud URL (like Ngrok)
        boolean isHttp = input.startsWith("http://") || input.startsWith("https://")
                || input.contains("ngrok") || input.contains(".dev") || input.contains(".app")
                || input.contains(".page") || (input.contains(".") && !input.matches("^[0-9.:]+$") && !input.contains(":"));

        if (isHttp) {
            connectHttpRelay(input);
        } else {
            connectDirectTcp(input, defaultPort);
        }
    }

    // ==========================================
    // 3. HTTP CLOUD RELAY (NGROK / REST / SSE)
    // ==========================================
    private void connectHttpRelay(String input) {
        new Thread(() -> {
            String url = input;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            final String baseUrl = url;

            notifyStatus(false, "Connecting to Cloud Relay: " + baseUrl + "...");

            try {
                // Test connectivity with /status
                URL statusUrl = new URL(baseUrl + "/status");
                HttpURLConnection conn = (HttpURLConnection) statusUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "LumaChat-Android");
                conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int respCode = conn.getResponseCode();
                if (respCode >= 200 && respCode < 400) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();

                    JSONObject statusObj = new JSONObject(sb.toString());
                    String ngrokUrl = statusObj.optString("ngrokUrl", baseUrl);

                    httpBaseUrl = baseUrl;
                    currentMode = Mode.HTTP_RELAY;
                    isConnected = true;
                    connectedPeerIp = ngrokUrl;
                    lastSeenTimestamp = System.currentTimeMillis() - 60000; // fetch recent messages

                    notifyStatus(true, "Connected to Cloud Relay ✨ (" + ngrokUrl + ")");

                    // Start background SSE & polling sync
                    isHttpPollingActive = true;
                    startSseListener(baseUrl);
                    startPollingFallback(baseUrl);
                } else {
                    isConnected = false;
                    currentMode = Mode.NONE;
                    notifyStatus(false, "Server returned HTTP " + respCode + ". Check Ngrok URL.");
                }
            } catch (Exception e) {
                isConnected = false;
                currentMode = Mode.NONE;
                notifyStatus(false, "Failed to connect: " + e.getMessage());
            }
        }).start();
    }

    private void startSseListener(String baseUrl) {
        sseThread = new Thread(() -> {
            while (isHttpPollingActive && isConnected) {
                try {
                    URL eventsUrl = new URL(baseUrl + "/events");
                    HttpURLConnection conn = (HttpURLConnection) eventsUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setRequestProperty("User-Agent", "LumaChat-Android");
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setReadTimeout(0); // keep alive stream

                    InputStream in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while (isHttpPollingActive && (line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            try {
                                JSONObject json = new JSONObject(data);
                                if (json.has("text")) {
                                    handleIncomingJson(json);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception e) {
                    // Reconnect after brief pause
                    try { Thread.sleep(3000); } catch (Exception ignored) {}
                }
            }
        });
        sseThread.start();
    }

    private void startPollingFallback(String baseUrl) {
        pollThread = new Thread(() -> {
            while (isHttpPollingActive && isConnected) {
                try {
                    Thread.sleep(2500);
                    if (!isHttpPollingActive || !isConnected) break;

                    URL pollUrl = new URL(baseUrl + "/poll?since=" + lastSeenTimestamp);
                    HttpURLConnection conn = (HttpURLConnection) pollUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "LumaChat-Android");
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    if (conn.getResponseCode() == 200) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                        r.close();

                        JSONObject resp = new JSONObject(sb.toString());
                        JSONArray arr = resp.optJSONArray("messages");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject msgJson = arr.getJSONObject(i);
                                handleIncomingJson(msgJson);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
        pollThread.start();
    }

    // ==========================================
    // 4. DIRECT TCP SOCKET MODE
    // ==========================================
    private void connectDirectTcp(String hostInput, int defaultPort) {
        new Thread(() -> {
            String host = hostInput;
            int port = defaultPort;

            if (host.startsWith("tcp://")) {
                host = host.substring(6);
            }
            if (host.contains(":")) {
                int colonIdx = host.lastIndexOf(":");
                String portStr = host.substring(colonIdx + 1);
                host = host.substring(0, colonIdx);
                try {
                    port = Integer.parseInt(portStr);
                } catch (Exception ignored) {}
            }

            try {
                notifyStatus(false, "Connecting to " + host + ":" + port + "...");
                clientSocket = new Socket(host, port);
                connectedPeerIp = host + ":" + port;
                setupStreams(clientSocket);
                currentMode = Mode.DIRECT_TCP;
                isConnected = true;
                notifyStatus(true, "Connected to: " + connectedPeerIp);

                listenForIncomingTcp();
            } catch (Exception e) {
                currentMode = Mode.NONE;
                isConnected = false;
                notifyStatus(false, "Failed to connect: " + e.getMessage());
            }
        }).start();
    }

    private void setupStreams(Socket socket) throws Exception {
        outWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        inReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
    }

    private void listenForIncomingTcp() {
        try {
            String line;
            while ((line = inReader.readLine()) != null) {
                try {
                    JSONObject json = new JSONObject(line);
                    handleIncomingJson(json);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            isConnected = false;
            currentMode = Mode.NONE;
            notifyStatus(false, "Peer disconnected: " + e.getMessage());
        }
    }

    // ==========================================
    // 5. INCOMING MESSAGE DISPATCHER & DEDUPLICATION
    // ==========================================
    private void handleIncomingJson(JSONObject json) {
        try {
            String text = json.optString("text", "");
            if (text.isEmpty()) return;

            String convId = json.optString("convId", "chat_peer");
            String senderName = json.optString("senderName", "Peer Friend");
            String senderAvatar = json.optString("senderAvatar", "📱");
            String time = json.optString("time", "Now");
            long timestamp = json.optLong("timestamp", System.currentTimeMillis());

            // Deduplication key
            String dedupeKey = senderName + "_" + timestamp + "_" + text;
            synchronized (processedKeys) {
                if (processedKeys.contains(dedupeKey)) return;
                processedKeys.add(dedupeKey);
                if (processedKeys.size() > 500) {
                    // Evict oldest
                    String first = processedKeys.iterator().next();
                    processedKeys.remove(first);
                }
            }

            if (timestamp > lastSeenTimestamp) {
                lastSeenTimestamp = timestamp;
            }

            Message incoming = new Message(
                    0, convId, senderName, senderAvatar, text,
                    timestamp, time, false, "read"
            );

            // Persist to local SQLite
            if (appContext != null) {
                DatabaseHelper db = DatabaseHelper.getInstance(appContext);
                if (db.getConversation(convId) == null) {
                    db.insertConversation(new Conversation(
                            convId, senderName, senderAvatar, false, "",
                            text, time, 1, false, "Online", timestamp
                    ));
                }
                db.insertMessage(incoming);
            }

            mainHandler.post(() -> {
                if (messageListener != null) {
                    messageListener.onMessageReceived(incoming);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // 6. SEND MESSAGE (HTTP POST OR TCP SOCKET)
    // ==========================================
    public boolean sendMessage(Message message, String convId) {
        if (!isConnected) return false;

        final JSONObject json = new JSONObject();
        try {
            json.put("convId", convId);
            json.put("senderName", message.getSenderName());
            json.put("senderAvatar", message.getSenderAvatar());
            json.put("text", message.getMessageText());
            json.put("time", message.getTimeFormatted());
            json.put("timestamp", message.getTimestamp());
        } catch (Exception e) {
            return false;
        }

        // Record key so sender doesn't re-dispatch own message if received via poll/SSE
        String dedupeKey = message.getSenderName() + "_" + message.getTimestamp() + "_" + message.getMessageText();
        synchronized (processedKeys) {
            processedKeys.add(dedupeKey);
        }
        if (message.getTimestamp() > lastSeenTimestamp) {
            lastSeenTimestamp = message.getTimestamp();
        }

        if (currentMode == Mode.HTTP_RELAY) {
            new Thread(() -> {
                try {
                    URL sendUrl = new URL(httpBaseUrl + "/send");
                    HttpURLConnection conn = (HttpURLConnection) sendUrl.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("User-Agent", "LumaChat-Android");
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    if (code != 200) {
                        notifyStatus(true, "Sent (HTTP " + code + ")");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            return true;
        } else if (outWriter != null) {
            new Thread(() -> {
                try {
                    outWriter.println(json.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            return true;
        }

        return false;
    }

    private void notifyStatus(boolean connected, String message) {
        mainHandler.post(() -> {
            if (statusListener != null) {
                statusListener.onStatusChanged(connected, message);
            }
        });
    }

    // ==========================================
    // 7. CLEAN DISCONNECT
    // ==========================================
    public void disconnect() {
        isConnected = false;
        isHttpPollingActive = false;
        currentMode = Mode.NONE;

        if (sseThread != null) {
            sseThread.interrupt();
            sseThread = null;
        }
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }

        try {
            if (outWriter != null) outWriter.close();
            if (inReader != null) inReader.close();
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}

        outWriter = null;
        inReader = null;
        clientSocket = null;
        serverSocket = null;

        notifyStatus(false, "Disconnected");
    }
}
