package com.example.lumachat.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.model.Message;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

public class P2PChatManager {

    public interface OnMessageReceivedListener {
        void onMessageReceived(Message message);
    }

    public interface OnPeerStatusListener {
        void onStatusChanged(boolean isConnected, String message);
    }

    public static final int DEFAULT_PORT = 8888;
    private static P2PChatManager instance;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter outWriter;
    private BufferedReader inReader;

    private boolean isServerRunning = false;
    private boolean isConnected = false;
    private String connectedPeerIp = "";

    private OnMessageReceivedListener messageListener;
    private OnPeerStatusListener statusListener;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
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
        return isConnected && clientSocket != null && !clientSocket.isClosed();
    }

    public String getConnectedPeerIp() {
        return connectedPeerIp;
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

    public void startServer(int port) {
        if (isServerRunning) return;

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isServerRunning = true;
                notifyStatus(false, "Hosting chat on port " + port + "... Waiting for peer.");

                // Accept connection from peer
                clientSocket = serverSocket.accept();
                connectedPeerIp = clientSocket.getInetAddress().getHostAddress();
                setupStreams(clientSocket);
                isConnected = true;
                notifyStatus(true, "Peer connected: " + connectedPeerIp);

                listenForIncoming();
            } catch (Exception e) {
                isServerRunning = false;
                isConnected = false;
                notifyStatus(false, "Server stopped: " + e.getMessage());
            }
        }).start();
    }

    public void connectToPeer(String peerIp, int port) {
        new Thread(() -> {
            try {
                notifyStatus(false, "Connecting to " + peerIp + ":" + port + "...");
                clientSocket = new Socket(peerIp, port);
                connectedPeerIp = peerIp;
                setupStreams(clientSocket);
                isConnected = true;
                notifyStatus(true, "Connected to peer: " + peerIp);

                listenForIncoming();
            } catch (Exception e) {
                isConnected = false;
                notifyStatus(false, "Failed to connect: " + e.getMessage());
            }
        }).start();
    }

    private void setupStreams(Socket socket) throws Exception {
        outWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        inReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
    }

    private void listenForIncoming() {
        try {
            String line;
            while ((line = inReader.readLine()) != null) {
                try {
                    JSONObject json = new JSONObject(line);
                    String convId = json.optString("convId", "chat_peer");
                    String senderName = json.optString("senderName", "Peer Friend");
                    String senderAvatar = json.optString("senderAvatar", "P");
                    String text = json.optString("text", "");
                    String time = json.optString("time", "Now");
                    long timestamp = json.optLong("timestamp", System.currentTimeMillis());

                    Message incoming = new Message(
                            0, convId, senderName, senderAvatar, text,
                            timestamp, time, false, "read"
                    );

                    // Save to local SQLite
                    if (appContext != null) {
                        DatabaseHelper.getInstance(appContext).insertMessage(incoming);
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
        } catch (Exception e) {
            isConnected = false;
            notifyStatus(false, "Peer disconnected: " + e.getMessage());
        }
    }

    public boolean sendMessage(Message message, String convId) {
        if (!isConnected || outWriter == null) return false;

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("convId", convId);
                json.put("senderName", message.getSenderName());
                json.put("senderAvatar", message.getSenderAvatar());
                json.put("text", message.getMessageText());
                json.put("time", message.getTimeFormatted());
                json.put("timestamp", message.getTimestamp());

                outWriter.println(json.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return true;
    }

    private void notifyStatus(boolean connected, String message) {
        mainHandler.post(() -> {
            if (statusListener != null) {
                statusListener.onStatusChanged(connected, message);
            }
        });
    }

    public void disconnect() {
        try {
            isConnected = false;
            isServerRunning = false;
            if (outWriter != null) outWriter.close();
            if (inReader != null) inReader.close();
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
        notifyStatus(false, "Disconnected");
    }
}
