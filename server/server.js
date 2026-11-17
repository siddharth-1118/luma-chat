const net = require('net');
const http = require('http');
const os = require('os');

const TCP_PORT = 8888;
const HTTP_PORT = 8080;

// Find all local IPv4 addresses
function getLocalIps() {
    const ips = [];
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                ips.push({ name, address: iface.address });
            }
        }
    }
    return ips;
}

const localIps = getLocalIps();
const primaryIp = localIps.find(i => i.name.toLowerCase().includes('wi-fi') || i.name.toLowerCase().includes('wlan'))?.address || localIps[0]?.address || '127.0.0.1';

// Active Android TCP client connections
const tcpClients = new Set();
// Active Web / HTTP SSE client connections
const sseClients = new Set();
// Message history for display and polling fallback (max 300)
const messageHistory = [];
// Call signals buffer for polling fallback (max 50)
const recentCalls = [];

// Online Users Registry: userId -> { userId, name, avatar, bio, status, lastSeen, ip }
const onlineUsers = new Map();

function getActiveUsersList() {
    const now = Date.now();
    const list = [];
    for (const [id, user] of onlineUsers.entries()) {
        if (now - user.lastSeen < 45000) {
            list.push({
                userId: user.userId,
                name: user.name,
                avatar: user.avatar || '🌸',
                bio: user.bio || '',
                status: user.status || 'online',
                isOnline: true,
                lastSeen: user.lastSeen
            });
        } else {
            onlineUsers.delete(id);
        }
    }
    return list;
}

// Cleanup inactive users every 10 seconds
setInterval(() => {
    const beforeCount = onlineUsers.size;
    const list = getActiveUsersList();
    if (list.length !== beforeCount) {
        broadcastToWeb('user_list', { users: list });
        broadcastToPhones(JSON.stringify({ type: 'user_list', users: list }));
    }
}, 10000);

let activeNgrokUrl = '';

// Check ngrok local management API for active public tunnel URL
function checkNgrokUrl() {
    http.get('http://127.0.0.1:4040/api/tunnels', (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => {
            try {
                const json = JSON.parse(data);
                if (json.tunnels && json.tunnels.length > 0) {
                    activeNgrokUrl = json.tunnels[0].public_url;
                }
            } catch (err) {}
        });
    }).on('error', () => {
        // ngrok not running
    });
}
checkNgrokUrl();
setInterval(checkNgrokUrl, 5000);

function broadcastToPhones(jsonLine, senderSocket = null) {
    for (const client of tcpClients) {
        if (client !== senderSocket && !client.destroyed) {
            try {
                client.write(jsonLine + '\n');
            } catch (err) {
                console.error('[Error sending to phone]', err.message);
            }
        }
    }
}

function broadcastToWeb(event, data) {
    const payload = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
    for (const res of sseClients) {
        try {
            res.write(payload);
        } catch (err) {}
    }
}

function logActivity(text) {
    const time = new Date().toLocaleTimeString();
    const entry = `[${time}] ${text}`;
    console.log(entry);
    broadcastToWeb('log', { entry });
}

// ==========================================
// 1. TCP SOCKET SERVER (For Android Phones)
// ==========================================
const tcpServer = net.createServer((socket) => {
    const remote = `${socket.remoteAddress}:${socket.remotePort}`;
    tcpClients.add(socket);
    let boundUserId = null;

    logActivity(`📱 Android Phone Connected from ${remote}`);
    broadcastToWeb('clientsCount', { count: tcpClients.size, sseCount: sseClients.size });

    let buffer = '';

    socket.on('data', (chunk) => {
        buffer += chunk.toString('utf-8');
        let lines = buffer.split('\n');
        buffer = lines.pop();

        for (const line of lines) {
            const clean = line.trim();
            if (!clean) continue;

            try {
                const msg = JSON.parse(clean);

                // Handle registration packet from TCP phone
                if (msg.type === 'register') {
                    boundUserId = msg.userId || `tcp_${Date.now()}`;
                    onlineUsers.set(boundUserId, {
                        userId: boundUserId,
                        name: msg.name || 'Friend',
                        avatar: msg.avatar || '📱',
                        bio: msg.bio || 'Connected via TCP',
                        status: 'online',
                        lastSeen: Date.now(),
                        ip: remote
                    });
                    logActivity(`👤 [TCP Online] ${msg.name} (${msg.avatar}) registered!`);
                    const usersList = getActiveUsersList();
                    broadcastToWeb('user_list', { users: usersList });
                    broadcastToPhones(JSON.stringify({ type: 'user_list', users: usersList }));
                    socket.write(JSON.stringify({ type: 'user_list', users: usersList }) + '\n');
                    continue;
                }

                // Handle heartbeat packet
                if (msg.type === 'heartbeat') {
                    if (boundUserId && onlineUsers.has(boundUserId)) {
                        onlineUsers.get(boundUserId).lastSeen = Date.now();
                    }
                    continue;
                }

                // Normal chat message
                logActivity(`💬 [TCP] Message from ${msg.senderName || 'Phone'}: "${msg.text}"`);

                const entry = {
                    convId: msg.convId || 'chat_peer',
                    senderName: msg.senderName || 'Phone',
                    senderAvatar: msg.senderAvatar || '📱',
                    text: msg.text || '',
                    time: msg.time || new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
                    timestamp: msg.timestamp || Date.now()
                };

                messageHistory.push(entry);
                if (messageHistory.length > 300) messageHistory.shift();

                // Broadcast to other phones on TCP
                broadcastToPhones(clean, socket);
                // Broadcast to web & HTTP SSE clients
                broadcastToWeb('chat', entry);

            } catch (err) {
                console.error('[Invalid JSON from phone]', clean);
            }
        }
    });

    socket.on('close', () => {
        tcpClients.delete(socket);
        if (boundUserId && onlineUsers.has(boundUserId)) {
            const u = onlineUsers.get(boundUserId);
            onlineUsers.delete(boundUserId);
            logActivity(`❌ [Offline] ${u.name} disconnected`);
            broadcastToWeb('user_list', { users: getActiveUsersList() });
        }
        broadcastToWeb('clientsCount', { count: tcpClients.size, sseCount: sseClients.size });
    });

    socket.on('error', (err) => {
        console.error(`[Socket Error ${remote}]`, err.message);
    });
});

tcpServer.listen(TCP_PORT, '0.0.0.0', () => {
    console.log(`\n=================================================`);
    console.log(`🚀 LumaChat Local Relay Server is LIVE!`);
    console.log(`📡 TCP Socket Server:   0.0.0.0:${TCP_PORT}`);
    console.log(`💻 Primary Wi-Fi IP:    ${primaryIp}:${TCP_PORT}`);
    localIps.forEach(i => console.log(`   - ${i.name}: ${i.address}:${TCP_PORT}`));
    console.log(`🌐 Web UI Dashboard:    http://localhost:${HTTP_PORT}`);
    console.log(`                        http://${primaryIp}:${HTTP_PORT}`);
    console.log(`=================================================\n`);
});

// ==========================================
// 2. HTTP WEB SERVER & RELAY (Port 8080)
// ==========================================
const httpServer = http.createServer((req, res) => {
    // Universal CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, ngrok-skip-browser-warning, User-Agent');
    res.setHeader('ngrok-skip-browser-warning', 'true');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const pathname = urlObj.pathname;

    // Status / Ping Endpoint
    if (pathname === '/status' || pathname === '/ping') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'online',
            app: 'LumaChat Relay',
            version: '2.1',
            tcpPort: TCP_PORT,
            httpPort: HTTP_PORT,
            localIp: `${primaryIp}:${TCP_PORT}`,
            ngrokUrl: activeNgrokUrl || null,
            tcpClients: tcpClients.size,
            sseClients: sseClients.size,
            onlineUsersCount: getActiveUsersList().length,
            onlineUsers: getActiveUsersList(),
            totalMessages: messageHistory.length,
            serverTime: Date.now()
        }));
        return;
    }

    // Register User Endpoint: POST /register
    if (pathname === '/register' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const data = JSON.parse(body);
                const userId = data.userId || `user_${Date.now()}`;
                const name = data.name || 'Anonymous User';
                const avatar = data.avatar || '🌸';
                const bio = data.bio || 'LumaChat User';
                const status = data.status || 'online';

                onlineUsers.set(userId, {
                    userId,
                    name,
                    avatar,
                    bio,
                    status,
                    lastSeen: Date.now(),
                    ip: req.socket.remoteAddress
                });

                logActivity(`👤 [Online Registered] ${name} (${avatar}) joined from city/network!`);

                const usersList = getActiveUsersList();
                // Broadcast to Web & HTTP SSE clients
                broadcastToWeb('user_list', { users: usersList });
                // Broadcast to TCP phones
                broadcastToPhones(JSON.stringify({ type: 'user_list', users: usersList }));

                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true, users: usersList }));
            } catch (err) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: err.message }));
            }
        });
        return;
    }

    // Heartbeat Endpoint: POST /heartbeat
    if (pathname === '/heartbeat' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const data = JSON.parse(body);
                const userId = data.userId;
                if (userId && onlineUsers.has(userId)) {
                    onlineUsers.get(userId).lastSeen = Date.now();
                }
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true }));
            } catch (err) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: err.message }));
            }
        });
        return;
    }

    // Get Active Users: GET /users
    if (pathname === '/users' && req.method === 'GET') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            users: getActiveUsersList(),
            serverTime: Date.now()
        }));
        return;
    }

    // SSE Stream for Real-time App & Web Updates
    if (pathname === '/events') {
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive'
        });
        res.write('\n');
        sseClients.add(res);

        // Send initial stats and online user list
        const currentUsers = getActiveUsersList();
        res.write(`event: clientsCount\ndata: ${JSON.stringify({ count: tcpClients.size, sseCount: sseClients.size, ngrokUrl: activeNgrokUrl })}\n\n`);
        res.write(`event: user_list\ndata: ${JSON.stringify({ users: currentUsers })}\n\n`);

        req.on('close', () => {
            sseClients.delete(res);
            broadcastToWeb('clientsCount', { count: tcpClients.size, sseCount: sseClients.size });
        });
        return;
    }

    // Poll endpoint: GET /poll?since=<timestamp>&sender=<myName>
    if (pathname === '/poll') {
        const since = parseInt(urlObj.searchParams.get('since') || '0', 10);
        const excludeSender = urlObj.searchParams.get('sender') || '';

        const newMessages = messageHistory.filter(m => {
            if (m.timestamp <= since) return false;
            if (excludeSender && m.senderName === excludeSender) return false;
            return true;
        });

        const newCalls = recentCalls.filter(c => {
            if (c.timestamp <= since) return false;
            if (excludeSender && c.senderName === excludeSender) return false;
            return true;
        });

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            messages: newMessages,
            calls: newCalls,
            users: getActiveUsersList(),
            serverTime: Date.now()
        }));
        return;
    }

    // API to send message: POST /send
    if (pathname === '/send' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const data = JSON.parse(body);

                // Handle Call Signals (offer, answer, reject, end)
                if (data.type && data.type.startsWith('call_')) {
                    recentCalls.push(data);
                    if (recentCalls.length > 50) recentCalls.shift();

                    const jsonLine = JSON.stringify(data);
                    broadcastToPhones(jsonLine);
                    broadcastToWeb('call', data);
                    logActivity(`📞 [${data.type.toUpperCase()}] from ${data.senderName} (${data.callType || 'voice'})`);
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ success: true, callDelivered: true }));
                    return;
                }

                const text = data.text || '';
                const timeStr = data.time || new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                const senderName = data.senderName || 'PC User 💻';
                const senderAvatar = data.senderAvatar || (senderName.includes('PC') ? '💻' : '📱');
                const convId = data.convId || 'chat_peer';
                const timestamp = data.timestamp || Date.now();

                const msgPacket = {
                    convId,
                    senderName,
                    senderAvatar,
                    text,
                    time: timeStr,
                    timestamp
                };

                messageHistory.push(msgPacket);
                if (messageHistory.length > 300) messageHistory.shift();

                const jsonLine = JSON.stringify(msgPacket);
                // Send to all TCP phones!
                broadcastToPhones(jsonLine);
                // Send to web and HTTP SSE clients!
                broadcastToWeb('chat', msgPacket);

                logActivity(`💬 [HTTP] ${senderName}: "${text}"`);

                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true, deliveredTo: tcpClients.size + sseClients.size }));
            } catch (err) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Invalid JSON', message: err.message }));
            }
        });
        return;
    }

    // Web UI Dashboard (Unslopped, modern responsive interface)
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>LumaChat Relay Dashboard</title>
<style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    :root {
        --bg-page: #F8FAFC;
        --bg-surface: #FFFFFF;
        --bg-subtle: #F1F5F9;
        --border-subtle: #E2E8F0;
        --border-strong: #CBD5E1;
        --text-primary: #0F172A;
        --text-secondary: #475569;
        --text-muted: #94A3B8;
        --accent: #4F46E5;
        --accent-hover: #4338CA;
        --accent-light: #EEF2FF;
        --status-online: #10B981;
        --status-online-bg: #ECFDF5;
        --font-stack: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    }
    body {
        font-family: var(--font-stack);
        background: var(--bg-page);
        color: var(--text-primary);
        line-height: 1.5;
        padding: 32px 16px;
        display: flex;
        justify-content: center;
        -webkit-font-smoothing: antialiased;
    }
    .container {
        width: 100%;
        max-width: 960px;
        display: flex;
        flex-direction: column;
        gap: 24px;
    }
    /* Header */
    .header {
        background: var(--bg-surface);
        border: 1px solid var(--border-subtle);
        border-radius: 16px;
        padding: 20px 24px;
        display: flex;
        justify-content: space-between;
        align-items: center;
        box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    }
    .brand {
        display: flex;
        align-items: center;
        gap: 12px;
    }
    .brand-mark {
        width: 40px;
        height: 40px;
        border-radius: 10px;
        background: var(--accent);
        color: #FFFFFF;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 18px;
        font-weight: 600;
    }
    .brand-title {
        font-size: 18px;
        font-weight: 600;
        color: var(--text-primary);
        letter-spacing: -0.01em;
    }
    .brand-desc {
        font-size: 13px;
        color: var(--text-secondary);
    }
    .status-badge {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        background: var(--status-online-bg);
        color: var(--status-online);
        border: 1px solid #A7F3D0;
        padding: 6px 14px;
        border-radius: 9999px;
        font-size: 12px;
        font-weight: 600;
    }
    .pulse-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: var(--status-online);
        box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
        animation: pulse 2s infinite;
    }
    @keyframes pulse {
        0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.6); }
        70% { box-shadow: 0 0 0 8px rgba(16, 185, 129, 0); }
        100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
    }
    /* Grid */
    .grid-metrics {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
        gap: 16px;
    }
    .card {
        background: var(--bg-surface);
        border: 1px solid var(--border-subtle);
        border-radius: 16px;
        padding: 20px 24px;
        box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    }
    .metric-card {
        display: flex;
        flex-direction: column;
        justify-content: space-between;
    }
    .metric-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 12px;
    }
    .metric-label {
        font-size: 12px;
        font-weight: 500;
        color: var(--text-secondary);
    }
    .metric-icon {
        width: 28px;
        height: 28px;
        border-radius: 6px;
        background: var(--bg-subtle);
        display: flex;
        align-items: center;
        justify-content: center;
        color: var(--text-secondary);
    }
    .metric-value {
        font-size: 15px;
        font-weight: 600;
        color: var(--text-primary);
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        word-break: break-all;
        margin-bottom: 12px;
    }
    .btn-secondary {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        background: var(--bg-subtle);
        border: 1px solid var(--border-subtle);
        color: var(--text-secondary);
        padding: 6px 12px;
        border-radius: 8px;
        font-size: 12px;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.15s ease;
        align-self: flex-start;
    }
    .btn-secondary:hover {
        background: var(--border-subtle);
        color: var(--text-primary);
    }
    /* User Directory */
    .section-title {
        font-size: 15px;
        font-weight: 600;
        color: var(--text-primary);
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 4px;
    }
    .section-subtitle {
        font-size: 13px;
        color: var(--text-secondary);
        margin-bottom: 16px;
    }
    .user-list {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
    }
    .user-chip {
        display: inline-flex;
        align-items: center;
        gap: 10px;
        background: var(--bg-subtle);
        border: 1px solid var(--border-subtle);
        padding: 6px 14px 6px 8px;
        border-radius: 9999px;
        font-size: 13px;
        color: var(--text-primary);
    }
    .user-chip-avatar {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        background: #FFFFFF;
        border: 1px solid var(--border-strong);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
    }
    .user-chip-meta {
        display: flex;
        flex-direction: column;
    }
    .user-chip-name {
        font-weight: 500;
        line-height: 1.2;
    }
    .user-chip-bio {
        font-size: 11px;
        color: var(--text-secondary);
    }
    .empty-state {
        color: var(--text-muted);
        font-size: 13px;
        padding: 12px 0;
    }
    /* Chat stream */
    .chat-stream {
        height: 320px;
        overflow-y: auto;
        padding: 16px;
        background: var(--bg-subtle);
        border: 1px solid var(--border-subtle);
        border-radius: 12px;
        display: flex;
        flex-direction: column;
        gap: 12px;
    }
    .bubble {
        max-width: 70%;
        padding: 10px 14px;
        border-radius: 12px;
        font-size: 13px;
        line-height: 1.45;
        position: relative;
    }
    .bubble.incoming {
        align-self: flex-start;
        background: #FFFFFF;
        border: 1px solid var(--border-subtle);
        color: var(--text-primary);
    }
    .bubble.outgoing {
        align-self: flex-end;
        background: var(--accent);
        color: #FFFFFF;
    }
    .bubble-author {
        font-size: 11px;
        font-weight: 600;
        margin-bottom: 2px;
        opacity: 0.9;
    }
    .bubble-time {
        font-size: 10px;
        margin-top: 4px;
        opacity: 0.65;
        text-align: right;
    }
    /* Composer */
    .composer {
        display: flex;
        gap: 10px;
        margin-top: 14px;
    }
    .composer input {
        flex: 1;
        padding: 10px 14px;
        border-radius: 10px;
        border: 1px solid var(--border-strong);
        background: #FFFFFF;
        font-size: 13px;
        color: var(--text-primary);
        font-family: inherit;
        outline: none;
        transition: border-color 0.15s ease;
    }
    .composer input:focus {
        border-color: var(--accent);
        box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);
    }
    .btn-primary {
        background: var(--accent);
        color: #FFFFFF;
        border: none;
        border-radius: 10px;
        padding: 10px 18px;
        font-size: 13px;
        font-weight: 500;
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        gap: 6px;
        transition: background-color 0.15s ease;
    }
    .btn-primary:hover {
        background: var(--accent-hover);
    }
    /* Terminal logs */
    .terminal-box {
        background: #090D16;
        color: #94A3B8;
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        font-size: 12px;
        padding: 16px;
        border-radius: 12px;
        height: 140px;
        overflow-y: auto;
        line-height: 1.6;
    }
    .terminal-line { margin-bottom: 4px; }
    .terminal-line .time { color: #64748B; margin-right: 8px; }
    .terminal-line .tag { color: #38BDF8; margin-right: 6px; }

    /* Toast */
    #toast {
        position: fixed;
        bottom: 24px;
        right: 24px;
        background: #0F172A;
        color: #FFFFFF;
        padding: 10px 16px;
        border-radius: 8px;
        font-size: 12px;
        font-weight: 500;
        opacity: 0;
        transform: translateY(12px);
        transition: all 0.2s ease;
        pointer-events: none;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    }
    #toast.show {
        opacity: 1;
        transform: translateY(0);
    }
</style>
</head>
<body>

<div class="container">
    <!-- Header -->
    <header class="header">
        <div class="brand">
            <div class="brand-mark">L</div>
            <div>
                <h1 class="brand-title">LumaChat Relay</h1>
                <p class="brand-desc">Cross-city discovery and messaging console</p>
            </div>
        </div>
        <div class="status-badge">
            <span class="pulse-dot"></span>
            <span>Relay active</span>
        </div>
    </header>

    <!-- Metrics Grid -->
    <section class="grid-metrics">
        <div class="card metric-card">
            <div>
                <div class="metric-header">
                    <span class="metric-label">Public cloud URL</span>
                    <div class="metric-icon">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"/></svg>
                    </div>
                </div>
                <div class="metric-value" id="ngrokDisplay">${activeNgrokUrl || 'Tunnel initializing...'}</div>
            </div>
            <button class="btn-secondary" onclick="copyValue(document.getElementById('ngrokDisplay').innerText, 'Cloud URL copied')">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                Copy URL
            </button>
        </div>

        <div class="card metric-card">
            <div>
                <div class="metric-header">
                    <span class="metric-label">Local network address</span>
                    <div class="metric-icon">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12.55a11 11 0 0 1 14.08 0M1.42 9a16 16 0 0 1 21.16 0M8.53 16.11a6 6 0 0 1 6.95 0M12 20h.01"/></svg>
                    </div>
                </div>
                <div class="metric-value">${primaryIp}:${TCP_PORT}</div>
            </div>
            <button class="btn-secondary" onclick="copyValue('${primaryIp}:${TCP_PORT}', 'Local IP copied')">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                Copy address
            </button>
        </div>

        <div class="card metric-card">
            <div>
                <div class="metric-header">
                    <span class="metric-label">Online participants</span>
                    <div class="metric-icon">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                    </div>
                </div>
                <div class="metric-value" id="clientCount">0 participants online</div>
            </div>
            <span style="font-size: 12px; color: var(--text-secondary);">Phones and desktop clients</span>
        </div>
    </section>

    <!-- User Directory -->
    <section class="card">
        <div class="section-title">
            <span>Online participants</span>
            <span style="font-size: 12px; color: var(--text-muted);" id="userCountBadge">0 active</span>
        </div>
        <p class="section-subtitle">Devices currently connected to this relay</p>
        <div class="user-list" id="userListContainer">
            <div class="empty-state">No participants online. Open the app on mobile or desktop to connect.</div>
        </div>
    </section>

    <!-- Live Test Chat Stream -->
    <section class="card">
        <div class="section-title">
            <span>Live message stream</span>
        </div>
        <p class="section-subtitle">Real-time chat feed across mobile phones and desktop clients</p>
        
        <div class="chat-stream" id="chatBox">
            <div class="bubble incoming">
                <div class="bubble-author">Relay System</div>
                Relay initialized. Devices connected to the cloud URL or local address can communicate instantly.
                <div class="bubble-time">System</div>
            </div>
        </div>

        <form class="composer" onsubmit="sendFromWeb(event)">
            <input type="text" id="pcInput" placeholder="Send a message to all devices..." autocomplete="off">
            <button type="submit" class="btn-primary">
                Send
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
            </button>
        </form>
    </section>

    <!-- Logs Box -->
    <section class="card">
        <div class="section-title">
            <span>Activity logs</span>
        </div>
        <p class="section-subtitle">Relay socket events and traffic telemetry</p>
        <div class="terminal-box" id="logBox">
            <div class="terminal-line"><span class="time">[System]</span><span class="tag">TCP</span> Socket server listening on port ${TCP_PORT}</div>
            <div class="terminal-line"><span class="time">[System]</span><span class="tag">HTTP</span> Event stream and REST API active on port ${HTTP_PORT}</div>
        </div>
    </section>
</div>

<div id="toast"></div>

<script>
    const chatBox = document.getElementById('chatBox');
    const logBox = document.getElementById('logBox');
    const clientCount = document.getElementById('clientCount');
    const userCountBadge = document.getElementById('userCountBadge');
    const userListContainer = document.getElementById('userListContainer');
    const pcInput = document.getElementById('pcInput');
    const ngrokDisplay = document.getElementById('ngrokDisplay');
    const toast = document.getElementById('toast');

    const evtSource = new EventSource('/events');

    evtSource.addEventListener('clientsCount', (e) => {
        try {
            const data = JSON.parse(e.data);
            if (data.ngrokUrl) ngrokDisplay.textContent = data.ngrokUrl;
        } catch (_) {}
    });

    evtSource.addEventListener('user_list', (e) => {
        try {
            const data = JSON.parse(e.data);
            renderUserList(data.users || []);
        } catch (_) {}
    });

    function renderUserList(users) {
        clientCount.textContent = users.length + ' participant' + (users.length === 1 ? '' : 's') + ' online';
        userCountBadge.textContent = users.length + ' active';
        
        if (users.length === 0) {
            userListContainer.innerHTML = '<div class="empty-state">No participants online. Open the app on mobile or desktop to connect.</div>';
            return;
        }

        userListContainer.innerHTML = '';
        users.forEach(u => {
            const chip = document.createElement('div');
            chip.className = 'user-chip';
            chip.innerHTML = '<div class="user-chip-avatar">' + (u.avatar || '●') + '</div>' +
                             '<div class="user-chip-meta">' +
                                 '<span class="user-chip-name">' + escapeHtml(u.name || 'Friend') + '</span>' +
                                 (u.bio ? '<span class="user-chip-bio">' + escapeHtml(u.bio) + '</span>' : '') +
                             '</div>';
            userListContainer.appendChild(chip);
        });
    }

    evtSource.addEventListener('log', (e) => {
        try {
            const data = JSON.parse(e.data);
            const line = document.createElement('div');
            line.className = 'terminal-line';
            line.textContent = data.entry;
            logBox.appendChild(line);
            logBox.scrollTop = logBox.scrollHeight;
        } catch (_) {}
    });

    evtSource.addEventListener('chat', (e) => {
        try {
            const msg = JSON.parse(e.data);
            const isSelf = msg.senderName && msg.senderName.includes('Web Console');
            
            const bubble = document.createElement('div');
            bubble.className = 'bubble ' + (isSelf ? 'outgoing' : 'incoming');
            bubble.innerHTML = '<div class="bubble-author">' + escapeHtml(msg.senderName || 'Participant') + '</div>' + 
                               '<div>' + escapeHtml(msg.text || '') + '</div>' + 
                               '<div class="bubble-time">' + (msg.time || '') + '</div>';
            
            chatBox.appendChild(bubble);
            chatBox.scrollTop = chatBox.scrollHeight;
        } catch (_) {}
    });

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    async function sendFromWeb(e) {
        e.preventDefault();
        const text = pcInput.value.trim();
        if (!text) return;

        try {
            await fetch('/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ 
                    senderName: 'Web Console',
                    senderAvatar: '💻',
                    text: text 
                })
            });
            pcInput.value = '';
        } catch (err) {
            showToast('Failed to deliver message');
        }
    }

    function copyValue(text, message) {
        if (!text) return;
        navigator.clipboard.writeText(text);
        showToast(message || 'Copied to clipboard');
    }

    function showToast(msg) {
        toast.textContent = msg;
        toast.classList.add('show');
        setTimeout(() => toast.classList.remove('show'), 2200);
    }
</script>
</body>
</html>`);
});

httpServer.listen(HTTP_PORT, '0.0.0.0', () => {
    console.log(`🌐 Web UI Dashboard running at http://localhost:${HTTP_PORT}`);
});
