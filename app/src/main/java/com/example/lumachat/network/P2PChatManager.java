package com.example.lumachat.network;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;
import com.example.lumachat.model.Conversation;
import com.example.lumachat.model.Message;
import com.example.lumachat.model.OnlineUser;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import com.example.lumachat.call.CallRingtoneManager;
import com.example.lumachat.notification.LumaNotificationManager;

import java.util.List;
import java.util.Set;

public class P2PChatManager {

    public interface OnMessageReceivedListener {
        void onMessageReceived(Message message);
    }

    public interface OnPeerStatusListener {
        void onStatusChanged(boolean isConnected, String message);
    }

    public interface OnOnlineUsersListener {
        void onUsersUpdated(List<OnlineUser> users);
    }

    public interface OnCallSignalListener {
        void onCallSignal(String signalType, String callType, String callId, String senderName, String senderAvatar);
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
    private Thread heartbeatThread;

    private Mode currentMode = Mode.NONE;
    private boolean isConnected = false;
    private String connectedPeerIp = "";

    // Message deduplication
    private final Set<String> processedKeys = Collections.synchronizedSet(new LinkedHashSet<>());

    // Online Users list
    private final List<OnlineUser> onlineUsers = new ArrayList<>();

    private OnMessageReceivedListener messageListener;
    private OnPeerStatusListener statusListener;
    private OnOnlineUsersListener usersListener;
    private OnCallSignalListener callListener;
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

    public void setUsersListener(OnOnlineUsersListener listener) {
        this.usersListener = listener;
        if (usersListener != null) {
            synchronized (onlineUsers) {
                usersListener.onUsersUpdated(new ArrayList<>(onlineUsers));
            }
        }
    }

    public void setCallListener(OnCallSignalListener listener) {
        this.callListener = listener;
    }

    public List<OnlineUser> getOnlineUsers() {
        synchronized (onlineUsers) {
            return new ArrayList<>(onlineUsers);
        }
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

                registerWithServer();
                listenForIncomingTcp();
            } catch (Exception e) {
                currentMode = Mode.NONE;
                isConnected = false;
                notifyStatus(false, "Host stopped: " + e.getMessage());
            }
        }).start();
    }

    // ==========================================
    // 2. CONNECT TO PEER / SERVER
    // ==========================================
    public void connectToPeer(String rawInput, int defaultPort) {
        if (rawInput == null || rawInput.trim().isEmpty()) return;
        final String input = rawInput.trim();

        if (appContext != null) {
            saveLastAddress(appContext, input);
        }

        disconnect();

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
                    lastSeenTimestamp = System.currentTimeMillis() - 60000;

                    notifyStatus(true, "Connected to Cloud Relay ✨ (" + ngrokUrl + ")");

                    // Register this phone's profile with the server
                    registerWithServer();

                    // Start background SSE, polling, & heartbeat
                    isHttpPollingActive = true;
                    startSseListener(baseUrl);
                    startPollingFallback(baseUrl);
                    startHeartbeatLoop();
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

    // Register user profile on the server
    public void registerWithServer() {
        if (!isConnected || appContext == null) return;
        String userId = UserPreferences.getUserId(appContext);
        String name = UserPreferences.getUserName(appContext);
        String avatar = UserPreferences.getUserAvatar(appContext);
        String bio = UserPreferences.getUserBio(appContext);

        updateMyProfile(userId, name, avatar, bio);
    }

    public void updateMyProfile(String userId, String name, String avatar, String bio) {
        new Thread(() -> {
            try {
                if (currentMode == Mode.HTTP_RELAY && !httpBaseUrl.isEmpty()) {
                    URL regUrl = new URL(httpBaseUrl + "/register");
                    HttpURLConnection conn = (HttpURLConnection) regUrl.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("User-Agent", "LumaChat-Android");
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);

                    JSONObject json = new JSONObject();
                    json.put("userId", userId);
                    json.put("name", name);
                    json.put("avatar", avatar);
                    json.put("bio", bio);
                    json.put("status", "online");

                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    if (conn.getResponseCode() == 200) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                        r.close();

                        JSONObject resp = new JSONObject(sb.toString());
                        JSONArray arr = resp.optJSONArray("users");
                        if (arr != null) {
                            parseAndDispatchUsers(arr);
                        }
                    }
                } else if ((currentMode == Mode.DIRECT_TCP || currentMode == Mode.SERVER_TCP) && outWriter != null) {
                    JSONObject json = new JSONObject();
                    json.put("type", "register");
                    json.put("userId", userId);
                    json.put("name", name);
                    json.put("avatar", avatar);
                    json.put("bio", bio);
                    outWriter.println(json.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startHeartbeatLoop() {
        heartbeatThread = new Thread(() -> {
            while (isHttpPollingActive && isConnected) {
                try {
                    Thread.sleep(15000); // 15s heartbeat
                    if (!isHttpPollingActive || !isConnected) break;

                    if (currentMode == Mode.HTTP_RELAY && !httpBaseUrl.isEmpty() && appContext != null) {
                        URL hbUrl = new URL(httpBaseUrl + "/heartbeat");
                        HttpURLConnection conn = (HttpURLConnection) hbUrl.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestProperty("User-Agent", "LumaChat-Android");
                        conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(5000);

                        JSONObject json = new JSONObject();
                        json.put("userId", UserPreferences.getUserId(appContext));

                        OutputStream os = conn.getOutputStream();
                        os.write(json.toString().getBytes("UTF-8"));
                        os.flush();
                        os.close();
                        conn.getResponseCode();
                    } else if (outWriter != null) {
                        JSONObject json = new JSONObject();
                        json.put("type", "heartbeat");
                        outWriter.println(json.toString());
                    }
                } catch (Exception ignored) {}
            }
        });
        heartbeatThread.start();
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
                    conn.setReadTimeout(0);

                    InputStream in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while (isHttpPollingActive && (line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            try {
                                JSONObject json = new JSONObject(data);
                                if (json.has("users")) {
                                    parseAndDispatchUsers(json.getJSONArray("users"));
                                } else if (json.has("text") || (json.has("type") && json.optString("type").startsWith("call_"))) {
                                    handleIncomingJson(json);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception e) {
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
                        if (resp.has("users")) {
                            parseAndDispatchUsers(resp.getJSONArray("users"));
                        }
                        JSONArray arr = resp.optJSONArray("messages");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject msgJson = arr.getJSONObject(i);
                                handleIncomingJson(msgJson);
                            }
                        }
                        JSONArray callArr = resp.optJSONArray("calls");
                        if (callArr != null) {
                            for (int i = 0; i < callArr.length(); i++) {
                                JSONObject callJson = callArr.getJSONObject(i);
                                handleIncomingJson(callJson);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
        pollThread.start();
    }

    private void parseAndDispatchUsers(JSONArray arr) {
        try {
            List<OnlineUser> list = new ArrayList<>();
            String myUserId = (appContext != null) ? UserPreferences.getUserId(appContext) : "";

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String uid = obj.optString("userId", "");
                String name = obj.optString("name", "Friend");
                String avatar = obj.optString("avatar", "🌸");
                String bio = obj.optString("bio", "");
                String status = obj.optString("status", "online");
                boolean online = obj.optBoolean("isOnline", true);
                long lastSeen = obj.optLong("lastSeen", System.currentTimeMillis());

                // Exclude myself from the friend discovery list
                if (!uid.equals(myUserId)) {
                    list.add(new OnlineUser(uid, name, avatar, bio, status, online, lastSeen));
                }
            }

            synchronized (onlineUsers) {
                onlineUsers.clear();
                onlineUsers.addAll(list);
            }

            mainHandler.post(() -> {
                if (usersListener != null) {
                    usersListener.onUsersUpdated(list);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
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

                registerWithServer();
                startHeartbeatLoop();
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
                    if (json.has("type") && json.optString("type").equals("user_list")) {
                        parseAndDispatchUsers(json.getJSONArray("users"));
                    } else {
                        handleIncomingJson(json);
                    }
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
    // 5. INCOMING MESSAGE DISPATCHER & CALL SIGNALING
    // ==========================================
    private void handleIncomingJson(JSONObject json) {
        try {
            // Handle Call Signals (offer, answer, reject, end)
            String signalType = json.optString("type", "");
            if (signalType.startsWith("call_")) {
                String callType = json.optString("callType", "voice");
                String callId = json.optString("callId", "");
                String senderName = json.optString("senderName", "Friend");
                String senderAvatar = json.optString("senderAvatar", "🌸");

                String myName = (appContext != null) ? UserPreferences.getUserName(appContext) : "";
                if (senderName.equalsIgnoreCase(myName)) {
                    return; // ignore own signals
                }

                if ("call_offer".equals(signalType)) {
                    if (appContext != null) {
                        LumaNotificationManager.showIncomingCallNotification(appContext, callType, senderName, senderAvatar, callId);
                        CallRingtoneManager.getInstance().startIncomingRingtone(appContext);

                        Intent callIntent = new Intent(appContext, com.example.lumachat.CallActivity.class);
                        callIntent.putExtra("name", senderName);
                        callIntent.putExtra("avatar", senderAvatar);
                        callIntent.putExtra("call_type", callType);
                        callIntent.putExtra("call_id", callId);
                        callIntent.putExtra("is_incoming", true);
                        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        appContext.startActivity(callIntent);
                    }
                } else if ("call_reject".equals(signalType) || "call_end".equals(signalType)) {
                    CallRingtoneManager.getInstance().stopAll();
                    if (appContext != null) {
                        LumaNotificationManager.cancelCallNotification(appContext);
                    }
                }

                if (callListener != null) {
                    mainHandler.post(() -> callListener.onCallSignal(signalType, callType, callId, senderName, senderAvatar));
                }
                return;
            }

            String text = json.optString("text", "");
            if (text.isEmpty()) return;

            String senderName = json.optString("senderName", "Peer Friend");
            String senderAvatar = json.optString("senderAvatar", "📱");
            String time = json.optString("time", "Now");
            long timestamp = json.optLong("timestamp", System.currentTimeMillis());

            String myName = (appContext != null) ? UserPreferences.getUserName(appContext) : "";
            if (senderName.equalsIgnoreCase(myName)) {
                return; // ignore own echoed message
            }

            // Deduplication key
            String dedupeKey = senderName + "_" + timestamp + "_" + text;
            synchronized (processedKeys) {
                if (processedKeys.contains(dedupeKey)) return;
                processedKeys.add(dedupeKey);
                if (processedKeys.size() > 500) {
                    String first = processedKeys.iterator().next();
                    processedKeys.remove(first);
                }
            }

            if (timestamp > lastSeenTimestamp) {
                lastSeenTimestamp = timestamp;
            }

            // Clean conversation routing: check existing conversation by friend's name!
            String resolvedConvId;
            if (appContext != null) {
                DatabaseHelper db = DatabaseHelper.getInstance(appContext);
                Conversation existing = db.getConversationByName(senderName);
                if (existing != null) {
                    resolvedConvId = existing.getId();
                } else {
                    String rawConvId = json.optString("convId", "");
                    if (!rawConvId.isEmpty() && db.getConversation(rawConvId) != null) {
                        resolvedConvId = rawConvId;
                    } else {
                        resolvedConvId = "chat_" + senderName.toLowerCase().replaceAll("[^a-zA-Z0-9]", "_");
                        db.insertConversation(new Conversation(
                                resolvedConvId, senderName, senderAvatar, false, "",
                                text, time, 0, false, "Online", timestamp
                        ));
                    }
                }
            } else {
                resolvedConvId = json.optString("convId", "chat_peer");
            }

            Message incoming = new Message(
                    0, resolvedConvId, senderName, senderAvatar, text,
                    timestamp, time, false, "read"
            );

            // Persist to local SQLite
            if (appContext != null) {
                DatabaseHelper db = DatabaseHelper.getInstance(appContext);
                db.insertMessage(incoming);

                // Show notification if user is not in active chat
                if (!com.example.lumachat.ChatActivity.isCurrentlyActive(resolvedConvId)) {
                    LumaNotificationManager.showMessageNotification(appContext, resolvedConvId, senderName, senderAvatar, text);
                }
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

    public void sendCallSignal(String signalType, String callId, String callType, String senderName, String senderAvatar) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("type", signalType);
                json.put("callId", callId);
                json.put("callType", callType);
                json.put("senderName", senderName);
                json.put("senderAvatar", senderAvatar);
                json.put("timestamp", System.currentTimeMillis());

                if (currentMode == Mode.HTTP_RELAY && !httpBaseUrl.isEmpty()) {
                    URL sendUrl = new URL(httpBaseUrl + "/send");
                    HttpURLConnection conn = (HttpURLConnection) sendUrl.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("User-Agent", "LumaChat-Android");
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(6000);

                    OutputStream os = conn.getOutputStream();
                    os.write(json.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();
                    conn.getResponseCode();
                } else if (outWriter != null) {
                    outWriter.println(json.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
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

        synchronized (onlineUsers) {
            onlineUsers.clear();
        }

        notifyStatus(false, "Disconnected");
        if (usersListener != null) {
            mainHandler.post(() -> usersListener.onUsersUpdated(new ArrayList<>()));
        }
    }
}
