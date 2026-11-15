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
    logActivity(`📱 Android Phone Connected from ${remote}`);
    broadcastToWeb('clientsCount', { count: tcpClients.size, sseCount: sseClients.size });

    let buffer = '';

    socket.on('data', (chunk) => {
        buffer += chunk.toString('utf-8');
        let lines = buffer.split('\n');
        buffer = lines.pop(); // Keep partial line in buffer

        for (const line of lines) {
            const clean = line.trim();
            if (!clean) continue;

            try {
                const msg = JSON.parse(clean);
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
        logActivity(`❌ Android Phone Disconnected: ${remote}`);
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
    // Add Universal CORS headers
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
            version: '2.0',
            tcpPort: TCP_PORT,
            httpPort: HTTP_PORT,
            localIp: `${primaryIp}:${TCP_PORT}`,
            ngrokUrl: activeNgrokUrl || null,
            tcpClients: tcpClients.size,
            sseClients: sseClients.size,
            totalMessages: messageHistory.length,
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

        // Send initial stats
        res.write(`event: clientsCount\ndata: ${JSON.stringify({ count: tcpClients.size, sseCount: sseClients.size, ngrokUrl: activeNgrokUrl })}\n\n`);

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

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            messages: newMessages,
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

    // Web UI Dashboard
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>LumaChat Cross-City Relay Console</title>
<style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        background: #FFF9ED;
        color: #19152F;
        padding: 24px;
        display: flex;
        justify-content: center;
    }
    .container {
        width: 100%;
        max-width: 920px;
        display: flex;
        flex-direction: column;
        gap: 20px;
    }
    .header {
        background: #FFFFFF;
        padding: 22px 26px;
        border-radius: 24px;
        border: 1px solid #E5DDF0;
        display: flex;
        justify-content: space-between;
        align-items: center;
        box-shadow: 0 4px 16px rgba(205, 184, 246, 0.12);
    }
    .status-badge {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        background: #EAF7EE;
        color: #2E7D32;
        padding: 8px 16px;
        border-radius: 20px;
        font-weight: bold;
        font-size: 13px;
    }
    .card {
        background: #FFFFFF;
        border-radius: 24px;
        border: 1px solid #E5DDF0;
        padding: 22px;
        box-shadow: 0 4px 16px rgba(205, 184, 246, 0.08);
    }
    .info-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
        gap: 16px;
    }
    .info-box {
        background: #F1ECFA;
        padding: 18px;
        border-radius: 18px;
        border: 1px solid #CDB8F6;
        position: relative;
    }
    .info-box.ngrok {
        background: #FEF3F0;
        border-color: #F4B9A9;
    }
    .info-label { font-size: 11px; font-weight: bold; color: #77718A; letter-spacing: 0.5px; }
    .info-val { font-size: 17px; font-weight: bold; margin-top: 6px; color: #19152F; word-break: break-all; }
    .copy-btn {
        margin-top: 10px;
        padding: 6px 14px;
        background: #FFFFFF;
        border: 1px solid #CDB8F6;
        border-radius: 12px;
        font-size: 11px;
        font-weight: bold;
        cursor: pointer;
        color: #7C5CBF;
        transition: all 0.2s ease;
    }
    .copy-btn:hover { background: #7C5CBF; color: #FFF; }

    .chat-box {
        height: 340px;
        overflow-y: auto;
        padding: 16px;
        background: #FFF9ED;
        border-radius: 18px;
        border: 1px solid #E5DDF0;
        display: flex;
        flex-direction: column;
        gap: 12px;
    }
    .bubble {
        max-width: 75%;
        padding: 10px 14px;
        border-radius: 18px;
        font-size: 14px;
        line-height: 1.4;
    }
    .bubble.phone {
        align-self: flex-start;
        background: #FFFFFF;
        border: 1px solid #E5DDF0;
        border-bottom-left-radius: 4px;
    }
    .bubble.pc {
        align-self: flex-end;
        background: #D7C5F9;
        color: #19152F;
        border-bottom-right-radius: 4px;
    }
    .bubble-meta {
        font-size: 10px;
        color: #77718A;
        margin-top: 4px;
        text-align: right;
    }
    .composer {
        display: flex;
        gap: 10px;
        margin-top: 14px;
    }
    input[type="text"] {
        flex: 1;
        padding: 12px 18px;
        border-radius: 16px;
        border: 1px solid #CDB8F6;
        background: #F1ECFA;
        font-size: 14px;
        color: #19152F;
        outline: none;
    }
    button[type="submit"] {
        padding: 12px 24px;
        border-radius: 16px;
        border: 1px solid #F4B9A9;
        background: linear-gradient(90deg, #CDB8F6, #F4B9A9);
        color: #19152F;
        font-weight: bold;
        font-size: 14px;
        cursor: pointer;
        transition: opacity 0.2s;
    }
    button[type="submit"]:hover { opacity: 0.9; }
    .logs {
        font-family: monospace;
        font-size: 12px;
        background: #19152F;
        color: #E9DFFC;
        padding: 14px;
        border-radius: 16px;
        height: 130px;
        overflow-y: auto;
    }
    .step-card {
        background: #F8F5FD;
        border-radius: 16px;
        padding: 16px 20px;
        margin-top: 14px;
        font-size: 13px;
        line-height: 1.6;
        color: #4A4458;
    }
    .step-card ol { margin-left: 20px; }
</style>
</head>
<body>

<div class="container">
    <div class="header">
        <div>
            <h2>✨ LumaChat Relay Server & Cloud Hub</h2>
            <p style="font-size: 12px; color: #77718A; margin-top: 4px;">Enabling real-time two-person chat across local Wi-Fi & different cities</p>
        </div>
        <div class="status-badge">● SERVER ACTIVE</div>
    </div>

    <div class="info-grid">
        <div class="info-box ngrok">
            <div class="info-label">🌍 PUBLIC NGROK URL (FOR OTHER CITIES)</div>
            <div class="info-val" id="ngrokDisplay">${activeNgrokUrl || 'Loading Ngrok URL...'}</div>
            <button class="copy-btn" onclick="copyText(document.getElementById('ngrokDisplay').innerText)">📋 Copy Cloud URL</button>
            <p style="font-size: 11px; color: #9A5A4A; margin-top: 6px;">Share this URL with friend in another city!</p>
        </div>
        <div class="info-box">
            <div class="info-label">🏠 LOCAL WI-FI IP (FOR SAME HOUSE/ROOM)</div>
            <div class="info-val">${primaryIp}:${TCP_PORT}</div>
            <button class="copy-btn" onclick="copyText('${primaryIp}:${TCP_PORT}')">📋 Copy Local IP</button>
            <p style="font-size: 11px; color: #77718A; margin-top: 6px;">Use when both phones are on same Wi-Fi</p>
        </div>
        <div class="info-box">
            <div class="info-label">👥 CONNECTED DEVICES</div>
            <div class="info-val" id="clientCount">0 Devices Connected</div>
            <p style="font-size: 11px; color: #77718A; margin-top: 6px;">Phones active on TCP & HTTP relay</p>
        </div>
    </div>

    <!-- Instructions Card -->
    <div class="card">
        <h3>🚀 How to Chat with Someone in Another City:</h3>
        <div class="step-card">
            <ol>
                <li>Install <b>LumaChat.apk</b> on both phones (your phone & your friend's phone).</li>
                <li>Tap <b>Live P2P Chat 🌐</b> icon in the top header (or tap 🌐 on any chat screen).</li>
                <li>In the box, paste the <b>Public Cloud URL</b> shown above (e.g. <span id="hintUrl">${activeNgrokUrl || 'https://...ngrok-free.dev'}</span>).</li>
                <li>Tap <b>Connect to Friend</b> on both phones.</li>
                <li>Start chatting! Messages will instantly fly across cities in real time! ✨</li>
            </ol>
        </div>
    </div>

    <!-- Live Test Chat with Phones -->
    <div class="card">
        <h3 style="margin-bottom: 8px;">💬 Live Chat Console</h3>
        <p style="font-size: 12px; color: #77718A; margin-bottom: 12px;">Send and receive real-time messages directly with connected phones right from this browser tab!</p>
        
        <div class="chat-box" id="chatBox">
            <div class="bubble phone">
                <strong>✨ LumaChat Relay</strong><br>
                Server initialized and ready! Connect your phone using the Cloud URL or Local IP above.
                <div class="bubble-meta">System</div>
            </div>
        </div>

        <form class="composer" onsubmit="sendFromPc(event)">
            <input type="text" id="pcInput" placeholder="Type a message to phones across all cities..." autocomplete="off">
            <button type="submit">Send Message ➤</button>
        </form>
    </div>

    <!-- Server Activity Log -->
    <div class="card">
        <h4 style="margin-bottom: 8px;">📋 Relay Activity Logs</h4>
        <div class="logs" id="logBox">
            <div>[Ready] TCP Socket Server listening on port ${TCP_PORT}...</div>
            <div>[Ready] HTTP / SSE Cloud Server listening on port ${HTTP_PORT}...</div>
        </div>
    </div>
</div>

<script>
    const chatBox = document.getElementById('chatBox');
    const logBox = document.getElementById('logBox');
    const clientCount = document.getElementById('clientCount');
    const pcInput = document.getElementById('pcInput');
    const ngrokDisplay = document.getElementById('ngrokDisplay');
    const hintUrl = document.getElementById('hintUrl');

    const evtSource = new EventSource('/events');

    evtSource.addEventListener('clientsCount', (e) => {
        const data = JSON.parse(e.data);
        const total = (data.count || 0) + (data.sseCount || 0);
        clientCount.textContent = total + ' Device(s) Connected';
        if (data.ngrokUrl) {
            ngrokDisplay.textContent = data.ngrokUrl;
            hintUrl.textContent = data.ngrokUrl;
        }
    });

    evtSource.addEventListener('log', (e) => {
        const data = JSON.parse(e.data);
        const div = document.createElement('div');
        div.textContent = data.entry;
        logBox.appendChild(div);
        logBox.scrollTop = logBox.scrollHeight;
    });

    evtSource.addEventListener('chat', (e) => {
        const msg = JSON.parse(e.data);
        const isFromPc = msg.senderName && msg.senderName.includes('PC');
        
        const bubble = document.createElement('div');
        bubble.className = 'bubble ' + (isFromPc ? 'pc' : 'phone');
        bubble.innerHTML = '<strong>' + (msg.senderAvatar || '') + ' ' + (msg.senderName || 'Phone') + '</strong><br>' + 
                           escapeHtml(msg.text) + 
                           '<div class="bubble-meta">' + (msg.time || '') + '</div>';
        
        chatBox.appendChild(bubble);
        chatBox.scrollTop = chatBox.scrollHeight;
    });

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    async function sendFromPc(e) {
        e.preventDefault();
        const text = pcInput.value.trim();
        if (!text) return;

        try {
            await fetch('/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ 
                    senderName: 'PC User 💻',
                    senderAvatar: '💻',
                    text: text 
                })
            });
            pcInput.value = '';
        } catch (err) {
            alert('Error sending message: ' + err.message);
        }
    }

    function copyText(txt) {
        navigator.clipboard.writeText(txt);
        alert('Copied to clipboard: ' + txt);
    }
</script>
</body>
</html>`);
});

httpServer.listen(HTTP_PORT, '0.0.0.0', () => {
    console.log(`🌐 Web UI Dashboard running at http://localhost:${HTTP_PORT}`);
});
