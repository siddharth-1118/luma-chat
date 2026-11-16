#!/usr/bin/env python3
"""
=============================================================================
LUMACHAT DESKTOP - PROFESSIONAL TKINTER CLIENT
=============================================================================
Cross-platform desktop messaging and call-signaling client for LumaChat.
Communicates with the Node.js Relay Server and Ngrok Cloud Tunnel.
Supports:
  - Real-time peer-to-peer and relayed messaging with Android devices
  - Live online participant discovery across networks and cities
  - Voice and video call signaling with local audio ringtones
  - Low-latency SSE / polling fallback networking
=============================================================================
"""

import os
import sys
import json
import time
import queue
import urllib.request
import urllib.error
import urllib.parse
import threading
import tkinter as tk
from tkinter import ttk, messagebox, simpledialog
from datetime import datetime

# Windows native sound support
try:
    import winsound
    HAS_WINSOUND = True
except ImportError:
    HAS_WINSOUND = False

DEFAULT_SERVER = "http://localhost:8080"
CONFIG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "luma_profile.json")

# Unslopped Design System (Studio Slate Theme on a strict 4px grid)
THEME = {
    "bg_app": "#0F172A",        # Slate 900
    "bg_sidebar": "#090D16",    # Deep Slate
    "bg_surface": "#1E293B",    # Slate 800
    "bg_chat": "#090D16",       # Dark canvas
    "bubble_in": "#1E293B",     # Incoming bubble (Slate 800)
    "bubble_out": "#4338CA",    # Outgoing bubble (Indigo 700)
    "border": "#273449",        # Quiet hairline border
    "border_focus": "#4F46E5",
    "accent": "#4F46E5",        # Indigo 600
    "accent_hover": "#4338CA",
    "online_green": "#10B981",  # Emerald 500
    "danger": "#E11D48",        # Rose 600
    "text_main": "#F8FAFC",     # Crisp White
    "text_sub": "#94A3B8",      # Slate 400
    "text_mute": "#64748B",     # Slate 500
    "font_main": "Segoe UI" if sys.platform == "win32" else "Helvetica"
}


class LumaChatDesktopApp:
    def __init__(self, root):
        self.root = root
        self.root.title("LumaChat Desktop")
        self.root.geometry("1040x700")
        self.root.minsize(840, 560)
        self.root.configure(bg=THEME["bg_app"])

        self.load_profile()

        # State
        self.server_url = self.profile.get("server_url", DEFAULT_SERVER)
        self.user_id = self.profile.get("user_id", f"pc_{int(time.time())}")
        self.my_name = self.profile.get("name", "Desktop User")
        self.my_avatar = self.profile.get("avatar", "D")
        self.my_bio = self.profile.get("bio", "Desktop Client")

        self.active_peer = None
        self.online_users = []
        self.last_poll_time = int(time.time() * 1000) - 30000
        self.is_connected = False
        self.is_ringing = False
        self.active_call_window = None

        self.msg_queue = queue.Queue()

        self.setup_styles()
        self.build_ui()

        # Start networking threads
        self.running = True
        self.network_thread = threading.Thread(target=self.network_loop, daemon=True)
        self.network_thread.start()

        self.process_events()
        self.register_with_server()
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    # =========================================================================
    # Configuration
    # =========================================================================
    def load_profile(self):
        default = {
            "name": "Desktop User",
            "avatar": "D",
            "bio": "Desktop Client",
            "user_id": f"pc_{int(time.time())}",
            "server_url": DEFAULT_SERVER
        }
        if os.path.exists(CONFIG_FILE):
            try:
                with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                    self.profile = json.load(f)
            except Exception:
                self.profile = default
        else:
            self.profile = default
            self.save_profile()

    def save_profile(self):
        try:
            with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(self.profile, f, indent=2, ensure_ascii=False)
        except Exception as e:
            print("[Config error]", e)

    # =========================================================================
    # UI Layout & Components
    # =========================================================================
    def setup_styles(self):
        self.style = ttk.Style()
        self.style.theme_use("clam")
        self.style.configure(".", background=THEME["bg_app"], foreground=THEME["text_main"])
        self.style.configure("TFrame", background=THEME["bg_app"])

    def build_ui(self):
        self.build_header()

        self.body_frame = tk.Frame(self.root, bg=THEME["bg_app"])
        self.body_frame.pack(fill=tk.BOTH, expand=True, padx=16, pady=(0, 8))

        self.build_sidebar()
        self.build_chat_panel()
        self.build_status_bar()

    # -------------------------------------------------------------------------
    # Header Bar
    # -------------------------------------------------------------------------
    def build_header(self):
        header = tk.Frame(self.root, bg=THEME["bg_surface"], height=60, padx=20, pady=12)
        header.pack(fill=tk.X, side=tk.TOP, pady=(0, 12))

        # Brand mark & Title
        brand_box = tk.Frame(header, bg=THEME["bg_surface"])
        brand_box.pack(side=tk.LEFT)

        logo_mark = tk.Label(brand_box, text="L", font=(THEME["font_main"], 12, "bold"),
                             bg=THEME["accent"], fg="#FFFFFF", width=3, pady=3)
        logo_mark.pack(side=tk.LEFT, padx=(0, 10))

        title_box = tk.Frame(brand_box, bg=THEME["bg_surface"])
        title_box.pack(side=tk.LEFT)

        title_lbl = tk.Label(title_box, text="LumaChat", font=(THEME["font_main"], 13, "bold"),
                             bg=THEME["bg_surface"], fg=THEME["text_main"])
        title_lbl.pack(anchor="w")

        sub_lbl = tk.Label(title_box, text="Desktop Relay Client", font=(THEME["font_main"], 8),
                           bg=THEME["bg_surface"], fg=THEME["text_sub"])
        sub_lbl.pack(anchor="w")

        # Right Controls: Status Badge + Server URL + User Badge
        controls = tk.Frame(header, bg=THEME["bg_surface"])
        controls.pack(side=tk.RIGHT)

        self.status_pill = tk.Label(controls, text="Connecting", font=(THEME["font_main"], 9),
                                    bg="#1E293B", fg=THEME["text_mute"], padx=10, pady=4, relief=tk.FLAT)
        self.status_pill.pack(side=tk.LEFT, padx=(0, 10))

        server_btn = tk.Button(controls, text="Server URL", font=(THEME["font_main"], 9),
                               bg=THEME["border"], fg=THEME["text_main"],
                               activebackground=THEME["accent"], activeforeground="#FFFFFF",
                               relief=tk.FLAT, padx=10, pady=3, command=self.change_server_dialog, cursor="hand2")
        server_btn.pack(side=tk.LEFT, padx=(0, 10))

        self.profile_btn = tk.Button(controls, text=self.my_name, font=(THEME["font_main"], 9, "bold"),
                                     bg=THEME["accent"], fg="#FFFFFF",
                                     activebackground=THEME["accent_hover"], activeforeground="#FFFFFF",
                                     relief=tk.FLAT, padx=12, pady=4, cursor="hand2", command=self.edit_profile_dialog)
        self.profile_btn.pack(side=tk.LEFT)

    # -------------------------------------------------------------------------
    # Left Sidebar: Online Directory
    # -------------------------------------------------------------------------
    def build_sidebar(self):
        sidebar = tk.Frame(self.body_frame, bg=THEME["bg_sidebar"], width=270)
        sidebar.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 12))
        sidebar.pack_propagate(False)

        # Header with Refresh
        sb_header = tk.Frame(sidebar, bg=THEME["bg_sidebar"], padx=14, pady=12)
        sb_header.pack(fill=tk.X)

        sb_title = tk.Label(sb_header, text="Online participants", font=(THEME["font_main"], 9, "bold"),
                            bg=THEME["bg_sidebar"], fg=THEME["text_sub"])
        sb_title.pack(side=tk.LEFT)

        refresh_btn = tk.Button(sb_header, text="Refresh", font=(THEME["font_main"], 8),
                                bg=THEME["bg_surface"], fg=THEME["text_sub"],
                                activebackground=THEME["accent"], activeforeground="#FFF",
                                relief=tk.FLAT, padx=6, pady=2, command=self.fetch_users_now, cursor="hand2")
        refresh_btn.pack(side=tk.RIGHT)

        # Scrollable container for users
        list_container = tk.Frame(sidebar, bg=THEME["bg_sidebar"])
        list_container.pack(fill=tk.BOTH, expand=True, padx=6, pady=4)

        self.users_canvas = tk.Canvas(list_container, bg=THEME["bg_sidebar"], highlightthickness=0)
        self.users_scrollbar = tk.Scrollbar(list_container, orient="vertical", command=self.users_canvas.yview, bg=THEME["bg_sidebar"])
        self.users_scroll_frame = tk.Frame(self.users_canvas, bg=THEME["bg_sidebar"])

        self.users_scroll_frame.bind(
            "<Configure>",
            lambda e: self.users_canvas.configure(scrollregion=self.users_canvas.bbox("all"))
        )

        self.users_canvas_window = self.users_canvas.create_window((0, 0), window=self.users_scroll_frame, anchor="nw", width=248)
        self.users_canvas.configure(yscrollcommand=self.users_scrollbar.set)

        self.users_canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.users_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        # Broadcast Room Item
        broadcast_card = tk.Frame(sidebar, bg=THEME["bg_surface"], padx=10, pady=8)
        broadcast_card.pack(fill=tk.X, side=tk.BOTTOM, padx=8, pady=8)

        b_btn = tk.Button(broadcast_card, text="Broadcast to all devices", font=(THEME["font_main"], 9),
                          bg=THEME["border"], fg=THEME["text_main"], activebackground=THEME["accent"],
                          activeforeground="#FFF", relief=tk.FLAT, pady=6, cursor="hand2",
                          command=lambda: self.select_peer(None))
        b_btn.pack(fill=tk.X)

    # -------------------------------------------------------------------------
    # Main Chat Panel
    # -------------------------------------------------------------------------
    def build_chat_panel(self):
        self.chat_panel = tk.Frame(self.body_frame, bg=THEME["bg_surface"])
        self.chat_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        # Chat Peer Info Header
        self.peer_header = tk.Frame(self.chat_panel, bg=THEME["bg_surface"], padx=16, pady=12,
                                    highlightthickness=1, highlightbackground=THEME["border"])
        self.peer_header.pack(fill=tk.X, side=tk.TOP)

        info_box = tk.Frame(self.peer_header, bg=THEME["bg_surface"])
        info_box.pack(side=tk.LEFT)

        self.peer_avatar_lbl = tk.Label(info_box, text="●", font=(THEME["font_main"], 12),
                                        bg=THEME["border"], fg=THEME["online_green"], width=3, pady=2)
        self.peer_avatar_lbl.pack(side=tk.LEFT, padx=(0, 10))

        text_col = tk.Frame(info_box, bg=THEME["bg_surface"])
        text_col.pack(side=tk.LEFT)

        self.peer_name_lbl = tk.Label(text_col, text="Global room", font=(THEME["font_main"], 12, "bold"),
                                      bg=THEME["bg_surface"], fg=THEME["text_main"])
        self.peer_name_lbl.pack(anchor="w")

        self.peer_status_lbl = tk.Label(text_col, text="All connected mobile and desktop peers",
                                        font=(THEME["font_main"], 8), bg=THEME["bg_surface"], fg=THEME["text_sub"])
        self.peer_status_lbl.pack(anchor="w")

        # Call Action Buttons
        self.call_actions = tk.Frame(self.peer_header, bg=THEME["bg_surface"])
        self.call_actions.pack(side=tk.RIGHT)

        self.voice_call_btn = tk.Button(self.call_actions, text="Voice call", font=(THEME["font_main"], 9),
                                        bg=THEME["border"], fg=THEME["text_main"], activebackground=THEME["accent"],
                                        activeforeground="#FFFFFF", relief=tk.FLAT, padx=12, pady=5, cursor="hand2",
                                        command=lambda: self.initiate_call("voice"))
        self.voice_call_btn.pack(side=tk.LEFT, padx=(0, 8))

        self.video_call_btn = tk.Button(self.call_actions, text="Video call", font=(THEME["font_main"], 9),
                                        bg=THEME["accent"], fg="#FFFFFF", activebackground=THEME["accent_hover"],
                                        activeforeground="#FFFFFF", relief=tk.FLAT, padx=12, pady=5, cursor="hand2",
                                        command=lambda: self.initiate_call("video"))
        self.video_call_btn.pack(side=tk.LEFT)

        # Message Container Canvas
        msg_frame = tk.Frame(self.chat_panel, bg=THEME["bg_chat"])
        msg_frame.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)

        self.msg_canvas = tk.Canvas(msg_frame, bg=THEME["bg_chat"], highlightthickness=0)
        self.msg_scrollbar = tk.Scrollbar(msg_frame, orient="vertical", command=self.msg_canvas.yview, bg=THEME["bg_chat"])
        self.msg_container = tk.Frame(self.msg_canvas, bg=THEME["bg_chat"])

        self.msg_container.bind(
            "<Configure>",
            lambda e: self.scroll_messages_to_bottom()
        )

        self.msg_window = self.msg_canvas.create_window((0, 0), window=self.msg_container, anchor="nw")
        self.msg_canvas.configure(yscrollcommand=self.msg_scrollbar.set)
        self.msg_canvas.bind("<Configure>", self.on_canvas_resize)

        self.msg_canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.msg_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        # Bottom Composer
        composer = tk.Frame(self.chat_panel, bg=THEME["bg_surface"], padx=12, pady=10,
                            highlightthickness=1, highlightbackground=THEME["border"])
        composer.pack(fill=tk.X, side=tk.BOTTOM)

        row = tk.Frame(composer, bg=THEME["bg_surface"])
        row.pack(fill=tk.X)

        self.msg_entry = tk.Entry(row, font=(THEME["font_main"], 10),
                                  bg=THEME["bg_chat"], fg=THEME["text_main"],
                                  insertbackground="#FFF", relief=tk.FLAT,
                                  highlightthickness=1, highlightbackground=THEME["border"])
        self.msg_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, ipady=7, padx=(0, 8))
        self.msg_entry.bind("<Return>", lambda e: self.send_message())

        send_btn = tk.Button(row, text="Send", font=(THEME["font_main"], 9, "bold"),
                             bg=THEME["accent"], fg="#FFFFFF", activebackground=THEME["accent_hover"],
                             activeforeground="#FFFFFF", relief=tk.FLAT, padx=16, ipady=4, cursor="hand2",
                             command=self.send_message)
        send_btn.pack(side=tk.RIGHT)

    def on_canvas_resize(self, event):
        self.msg_canvas.itemconfig(self.msg_window, width=event.width)

    def scroll_messages_to_bottom(self):
        self.msg_canvas.configure(scrollregion=self.msg_canvas.bbox("all"))
        self.msg_canvas.yview_moveto(1.0)

    # -------------------------------------------------------------------------
    # Status Bar
    # -------------------------------------------------------------------------
    def build_status_bar(self):
        status_bar = tk.Frame(self.root, bg=THEME["bg_app"], height=24, padx=16, pady=4)
        status_bar.pack(fill=tk.X, side=tk.BOTTOM)

        self.net_status_lbl = tk.Label(status_bar, text="Ready", font=(THEME["font_main"], 8),
                                       bg=THEME["bg_app"], fg=THEME["text_mute"])
        self.net_status_lbl.pack(side=tk.LEFT)

        arch_lbl = tk.Label(status_bar, text="Python Tkinter Client  •  Android Java 17  •  Relay Protocol 2.1",
                            font=(THEME["font_main"], 8), bg=THEME["bg_app"], fg=THEME["text_sub"])
        arch_lbl.pack(side=tk.RIGHT)

    # =========================================================================
    # Messages Rendering
    # =========================================================================
    def append_message_bubble(self, sender_name, sender_avatar, text, time_str, is_outgoing=False):
        row = tk.Frame(self.msg_container, bg=THEME["bg_chat"], padx=8, pady=3)
        row.pack(fill=tk.X, anchor="e" if is_outgoing else "w")

        card_bg = THEME["bubble_out"] if is_outgoing else THEME["bubble_in"]
        card = tk.Frame(row, bg=card_bg, padx=12, pady=8,
                        highlightthickness=1, highlightbackground=THEME["border"])

        if is_outgoing:
            card.pack(side=tk.RIGHT, anchor="e", padx=(64, 4))
        else:
            card.pack(side=tk.LEFT, anchor="w", padx=(4, 64))

        meta = tk.Frame(card, bg=card_bg)
        meta.pack(fill=tk.X, pady=(0, 2))

        author = "You" if is_outgoing else sender_name
        author_lbl = tk.Label(meta, text=author, font=(THEME["font_main"], 8, "bold"),
                              bg=card_bg, fg="#CBD5E1" if is_outgoing else "#94A3B8")
        author_lbl.pack(side=tk.LEFT, padx=(0, 8))

        time_lbl = tk.Label(meta, text=time_str, font=(THEME["font_main"], 7),
                            bg=card_bg, fg=THEME["text_mute"])
        time_lbl.pack(side=tk.RIGHT)

        body = tk.Label(card, text=text, font=(THEME["font_main"], 10),
                        bg=card_bg, fg=THEME["text_main"],
                        wraplength=480, justify=tk.LEFT)
        body.pack(anchor="w")

        if not is_outgoing and HAS_WINSOUND:
            try:
                winsound.MessageBeep(winsound.MB_ICONASTERISK)
            except Exception:
                pass

        self.root.after(10, self.scroll_messages_to_bottom)

    # =========================================================================
    # Directory & Peer Selection
    # =========================================================================
    def select_peer(self, user):
        self.active_peer = user
        if user is None:
            self.peer_name_lbl.config(text="Global room")
            self.peer_avatar_lbl.config(text="●", fg=THEME["online_green"])
            self.peer_status_lbl.config(text="All connected mobile and desktop peers")
        else:
            self.peer_name_lbl.config(text=user.get("name", "Participant"))
            self.peer_avatar_lbl.config(text=user.get("avatar", "●")[:2], fg=THEME["text_main"])
            bio = user.get("bio", "Online")
            self.peer_status_lbl.config(text=f"Online participant  •  {bio}")

    def render_users_list(self):
        for widget in self.users_scroll_frame.winfo_children():
            widget.destroy()

        peers = [u for u in self.online_users if u.get("userId") != self.user_id]
        if not peers:
            empty_lbl = tk.Label(self.users_scroll_frame, text="No participants online.\nOpen the app on mobile to connect.",
                                 font=(THEME["font_main"], 8), bg=THEME["bg_sidebar"], fg=THEME["text_mute"],
                                 justify=tk.CENTER, pady=24)
            empty_lbl.pack(fill=tk.X)
            return

        for user in peers:
            u_name = user.get("name", "Participant")
            u_avatar = user.get("avatar", "●")
            u_bio = user.get("bio", "Online")

            card = tk.Frame(self.users_scroll_frame, bg=THEME["bg_surface"], padx=10, pady=8,
                            highlightthickness=1, highlightbackground=THEME["border"], cursor="hand2")
            card.pack(fill=tk.X, pady=2, padx=2)

            av_lbl = tk.Label(card, text=u_avatar[:2], font=(THEME["font_main"], 9, "bold"),
                              bg=THEME["border"], fg=THEME["text_main"], width=3, pady=2)
            av_lbl.pack(side=tk.LEFT, padx=(0, 8))

            meta = tk.Frame(card, bg=THEME["bg_surface"])
            meta.pack(side=tk.LEFT, fill=tk.X, expand=True)

            name_lbl = tk.Label(meta, text=u_name, font=(THEME["font_main"], 9, "bold"),
                                bg=THEME["bg_surface"], fg=THEME["text_main"], anchor="w")
            name_lbl.pack(fill=tk.X)

            bio_lbl = tk.Label(meta, text=u_bio[:24], font=(THEME["font_main"], 7),
                               bg=THEME["bg_surface"], fg=THEME["text_sub"], anchor="w")
            bio_lbl.pack(fill=tk.X)

            dot_lbl = tk.Label(card, text="●", font=(THEME["font_main"], 8),
                               bg=THEME["bg_surface"], fg=THEME["online_green"])
            dot_lbl.pack(side=tk.RIGHT)

            for w in [card, av_lbl, meta, name_lbl, bio_lbl, dot_lbl]:
                w.bind("<Button-1>", lambda e, u=user: self.select_peer(u))

    # =========================================================================
    # Networking & REST Dispatch
    # =========================================================================
    def register_with_server(self):
        def _reg():
            payload = {
                "userId": self.user_id,
                "name": self.my_name,
                "avatar": self.my_avatar,
                "bio": self.my_bio,
                "status": "online"
            }
            try:
                url = f"{self.server_url.rstrip('/')}/register"
                req = urllib.request.Request(
                    url,
                    data=json.dumps(payload).encode("utf-8"),
                    headers={"Content-Type": "application/json", "ngrok-skip-browser-warning": "true"}
                )
                with urllib.request.urlopen(req, timeout=5) as res:
                    data = json.loads(res.read().decode())
                    if "users" in data:
                        self.msg_queue.put(("USER_LIST", data["users"]))
                self.msg_queue.put(("STATUS", "Connected"))
            except Exception as e:
                self.msg_queue.put(("STATUS", "Connecting..."))

        threading.Thread(target=_reg, daemon=True).start()

    def send_message(self):
        text = self.msg_entry.get().strip()
        if not text:
            return

        self.msg_entry.delete(0, tk.END)
        time_str = datetime.now().strftime("%I:%M %p")

        self.append_message_bubble(self.my_name, self.my_avatar, text, time_str, is_outgoing=True)

        def _send():
            conv_id = self.active_peer.get("userId") if self.active_peer else "chat_peer"
            payload = {
                "convId": conv_id,
                "senderName": self.my_name,
                "senderAvatar": self.my_avatar,
                "text": text,
                "time": time_str,
                "timestamp": int(time.time() * 1000)
            }
            try:
                url = f"{self.server_url.rstrip('/')}/send"
                req = urllib.request.Request(
                    url,
                    data=json.dumps(payload).encode("utf-8"),
                    headers={"Content-Type": "application/json", "ngrok-skip-browser-warning": "true"}
                )
                with urllib.request.urlopen(req, timeout=5) as res:
                    res.read()
            except Exception as e:
                self.msg_queue.put(("LOG", f"Delivery failed: {e}"))

        threading.Thread(target=_send, daemon=True).start()

    def fetch_users_now(self):
        def _get():
            try:
                url = f"{self.server_url.rstrip('/')}/users"
                req = urllib.request.Request(url, headers={"ngrok-skip-browser-warning": "true"})
                with urllib.request.urlopen(req, timeout=5) as res:
                    data = json.loads(res.read().decode())
                    if "users" in data:
                        self.msg_queue.put(("USER_LIST", data["users"]))
            except Exception as e:
                self.msg_queue.put(("LOG", f"Users fetch error: {e}"))

        threading.Thread(target=_get, daemon=True).start()

    def network_loop(self):
        last_heartbeat = 0
        while self.running:
            now = time.time()

            if now - last_heartbeat > 15:
                last_heartbeat = now
                try:
                    hb_url = f"{self.server_url.rstrip('/')}/heartbeat"
                    req = urllib.request.Request(
                        hb_url,
                        data=json.dumps({"userId": self.user_id}).encode("utf-8"),
                        headers={"Content-Type": "application/json", "ngrok-skip-browser-warning": "true"}
                    )
                    with urllib.request.urlopen(req, timeout=4) as res:
                        res.read()
                except Exception:
                    pass

            try:
                poll_url = f"{self.server_url.rstrip('/')}/poll?since={self.last_poll_time}&sender={urllib.parse.quote(self.my_name)}"
                req = urllib.request.Request(poll_url, headers={"ngrok-skip-browser-warning": "true"})
                with urllib.request.urlopen(req, timeout=6) as res:
                    data = json.loads(res.read().decode())

                    if "users" in data:
                        self.msg_queue.put(("USER_LIST", data["users"]))

                    if "messages" in data and data["messages"]:
                        for m in data["messages"]:
                            self.msg_queue.put(("MESSAGE", m))

                    if "calls" in data and data["calls"]:
                        for c in data["calls"]:
                            if c.get("senderName") != self.my_name:
                                self.msg_queue.put(("CALL", c))

                    if "serverTime" in data:
                        self.last_poll_time = data["serverTime"]

                    self.msg_queue.put(("STATUS", "Connected"))
            except Exception:
                self.msg_queue.put(("STATUS", "Connecting..."))

            time.sleep(2.0)

    # =========================================================================
    # Voice & Video Call Signaling
    # =========================================================================
    def initiate_call(self, call_type="voice"):
        target_name = self.active_peer.get("name") if self.active_peer else "Global participants"
        target_id = self.active_peer.get("userId") if self.active_peer else None

        payload = {
            "type": "call_offer",
            "callType": call_type,
            "senderId": self.user_id,
            "senderName": self.my_name,
            "senderAvatar": self.my_avatar,
            "targetId": target_id,
            "targetName": target_name,
            "timestamp": int(time.time() * 1000)
        }

        def _send():
            try:
                url = f"{self.server_url.rstrip('/')}/send"
                req = urllib.request.Request(
                    url,
                    data=json.dumps(payload).encode("utf-8"),
                    headers={"Content-Type": "application/json", "ngrok-skip-browser-warning": "true"}
                )
                with urllib.request.urlopen(req, timeout=5) as res:
                    res.read()
            except Exception:
                pass

        threading.Thread(target=_send, daemon=True).start()
        self.show_call_window(call_type, target_name, is_outgoing=True)

    def handle_incoming_call_signal(self, call_data):
        caller_name = call_data.get("senderName", "Mobile device")
        call_type = call_data.get("callType", "voice")

        self.start_ringtone()

        dlg = tk.Toplevel(self.root)
        dlg.title("Incoming call")
        dlg.geometry("340x260")
        dlg.configure(bg=THEME["bg_surface"])
        dlg.attributes("-topmost", True)

        lbl_icon = tk.Label(dlg, text="●", font=(THEME["font_main"], 28),
                            bg=THEME["bg_surface"], fg=THEME["online_green"])
        lbl_icon.pack(pady=(24, 8))

        lbl_caller = tk.Label(dlg, text=caller_name, font=(THEME["font_main"], 14, "bold"),
                              bg=THEME["bg_surface"], fg=THEME["text_main"])
        lbl_caller.pack()

        lbl_type = tk.Label(dlg, text=f"Incoming {call_type} call from mobile",
                            font=(THEME["font_main"], 9), bg=THEME["bg_surface"], fg=THEME["text_sub"])
        lbl_type.pack(pady=(4, 24))

        btn_box = tk.Frame(dlg, bg=THEME["bg_surface"])
        btn_box.pack()

        def _accept():
            self.stop_ringtone()
            dlg.destroy()
            self.send_call_signal("call_answer", call_type, caller_name)
            self.show_call_window(call_type, caller_name, is_outgoing=False)

        def _decline():
            self.stop_ringtone()
            dlg.destroy()
            self.send_call_signal("call_reject", call_type, caller_name)

        btn_accept = tk.Button(btn_box, text="Accept", font=(THEME["font_main"], 9, "bold"),
                               bg=THEME["online_green"], fg="#FFFFFF", activebackground="#059669",
                               relief=tk.FLAT, padx=16, pady=6, cursor="hand2", command=_accept)
        btn_accept.pack(side=tk.LEFT, padx=8)

        btn_decline = tk.Button(btn_box, text="Decline", font=(THEME["font_main"], 9, "bold"),
                                bg=THEME["danger"], fg="#FFFFFF", activebackground="#BE123C",
                                relief=tk.FLAT, padx=16, pady=6, cursor="hand2", command=_decline)
        btn_decline.pack(side=tk.LEFT, padx=8)

    def show_call_window(self, call_type, peer_name, is_outgoing=True):
        if self.active_call_window and self.active_call_window.winfo_exists():
            self.active_call_window.destroy()

        call_win = tk.Toplevel(self.root)
        call_win.title(f"Call - {peer_name}")
        call_win.geometry("440x480")
        call_win.configure(bg=THEME["bg_app"])
        self.active_call_window = call_win

        disp = tk.Frame(call_win, bg=THEME["bg_surface"], highlightthickness=1, highlightbackground=THEME["border"])
        disp.pack(fill=tk.BOTH, expand=True, padx=20, pady=20)

        status_txt = "Video feed active\nConnected to peer stream" if call_type == "video" else "Voice call active\nAudio connected"
        feed_lbl = tk.Label(disp, text=status_txt, font=(THEME["font_main"], 11),
                            bg=THEME["bg_surface"], fg=THEME["text_sub"], justify=tk.CENTER)
        feed_lbl.pack(expand=True)

        name_lbl = tk.Label(call_win, text=peer_name, font=(THEME["font_main"], 13, "bold"),
                            bg=THEME["bg_app"], fg=THEME["text_main"])
        name_lbl.pack()

        timer_lbl = tk.Label(call_win, text="00:00", font=(THEME["font_main"], 10),
                             bg=THEME["bg_app"], fg=THEME["online_green"])
        timer_lbl.pack(pady=(2, 16))

        start_t = time.time()
        def _update():
            if call_win.winfo_exists():
                elapsed = int(time.time() - start_t)
                timer_lbl.config(text=f"{elapsed // 60:02d}:{elapsed % 60:02d}")
                call_win.after(1000, _update)
        _update()

        ctrls = tk.Frame(call_win, bg=THEME["bg_app"])
        ctrls.pack(pady=(0, 20))

        is_muted = [False]
        def _toggle_mute():
            is_muted[0] = not is_muted[0]
            mute_btn.config(text="Unmute" if is_muted[0] else "Mute",
                            bg=THEME["accent"] if is_muted[0] else THEME["border"])

        mute_btn = tk.Button(ctrls, text="Mute", font=(THEME["font_main"], 9),
                             bg=THEME["border"], fg=THEME["text_main"], relief=tk.FLAT, padx=14, pady=5,
                             cursor="hand2", command=_toggle_mute)
        mute_btn.pack(side=tk.LEFT, padx=6)

        def _end():
            self.stop_ringtone()
            self.send_call_signal("call_end", call_type, peer_name)
            call_win.destroy()

        end_btn = tk.Button(ctrls, text="End call", font=(THEME["font_main"], 9, "bold"),
                            bg=THEME["danger"], fg="#FFFFFF", relief=tk.FLAT, padx=16, pady=5,
                            cursor="hand2", command=_end)
        end_btn.pack(side=tk.LEFT, padx=6)

    def send_call_signal(self, sig_type, call_type, peer_name):
        payload = {
            "type": sig_type,
            "callType": call_type,
            "senderName": self.my_name,
            "senderAvatar": self.my_avatar,
            "peerName": peer_name,
            "timestamp": int(time.time() * 1000)
        }
        def _send():
            try:
                url = f"{self.server_url.rstrip('/')}/send"
                req = urllib.request.Request(
                    url,
                    data=json.dumps(payload).encode("utf-8"),
                    headers={"Content-Type": "application/json", "ngrok-skip-browser-warning": "true"}
                )
                with urllib.request.urlopen(req, timeout=5) as res:
                    res.read()
            except Exception:
                pass
        threading.Thread(target=_send, daemon=True).start()

    def start_ringtone(self):
        if not HAS_WINSOUND or self.is_ringing:
            return
        self.is_ringing = True
        def _ring():
            while self.is_ringing:
                try:
                    winsound.Beep(853, 250)
                    winsound.Beep(960, 250)
                    time.sleep(1.0)
                except Exception:
                    break
        threading.Thread(target=_ring, daemon=True).start()

    def stop_ringtone(self):
        self.is_ringing = False

    # =========================================================================
    # Event Processor & Dialogs
    # =========================================================================
    def process_events(self):
        try:
            while not self.msg_queue.empty():
                evt, data = self.msg_queue.get_nowait()
                if evt == "STATUS":
                    is_conn = "Connected" in data
                    self.status_pill.config(
                        text=data,
                        bg="#064E3B" if is_conn else "#1E293B",
                        fg="#34D399" if is_conn else THEME["text_mute"]
                    )
                elif evt == "USER_LIST":
                    self.online_users = data
                    self.render_users_list()
                elif evt == "MESSAGE":
                    self.append_message_bubble(data.get("senderName", "Peer"),
                                                data.get("senderAvatar", "●"),
                                                data.get("text", ""),
                                                data.get("time", ""),
                                                is_outgoing=False)
                elif evt == "CALL":
                    self.handle_incoming_call_signal(data)
                elif evt == "LOG":
                    self.net_status_lbl.config(text=str(data))
        except Exception:
            pass
        self.root.after(100, self.process_events)

    def change_server_dialog(self):
        new_url = simpledialog.askstring("Server URL",
                                         "Enter Relay Server or Ngrok URL:\n(e.g. http://localhost:8080 or https://...ngrok-free.dev)",
                                         initialvalue=self.server_url, parent=self.root)
        if new_url:
            self.server_url = new_url.strip()
            self.profile["server_url"] = self.server_url
            self.save_profile()
            self.register_with_server()

    def edit_profile_dialog(self):
        dlg = tk.Toplevel(self.root)
        dlg.title("Edit profile")
        dlg.geometry("360x260")
        dlg.configure(bg=THEME["bg_surface"], padx=20, pady=16)
        dlg.transient(self.root)

        tk.Label(dlg, text="Display name", font=(THEME["font_main"], 9, "bold"),
                 bg=THEME["bg_surface"], fg=THEME["text_main"]).pack(anchor="w")
        name_ent = tk.Entry(dlg, font=(THEME["font_main"], 10), bg=THEME["bg_chat"], fg="#FFF",
                            highlightthickness=1, highlightbackground=THEME["border"], relief=tk.FLAT)
        name_ent.insert(0, self.my_name)
        name_ent.pack(fill=tk.X, pady=(4, 12), ipady=4)

        tk.Label(dlg, text="Status or bio", font=(THEME["font_main"], 9, "bold"),
                 bg=THEME["bg_surface"], fg=THEME["text_main"]).pack(anchor="w")
        bio_ent = tk.Entry(dlg, font=(THEME["font_main"], 10), bg=THEME["bg_chat"], fg="#FFF",
                           highlightthickness=1, highlightbackground=THEME["border"], relief=tk.FLAT)
        bio_ent.insert(0, self.my_bio)
        bio_ent.pack(fill=tk.X, pady=(4, 20), ipady=4)

        def _save():
            self.my_name = name_ent.get().strip() or "Desktop User"
            self.my_bio = bio_ent.get().strip() or "Desktop Client"
            self.profile["name"] = self.my_name
            self.profile["bio"] = self.my_bio
            self.save_profile()
            self.profile_btn.config(text=self.my_name)
            self.register_with_server()
            dlg.destroy()

        save_btn = tk.Button(dlg, text="Save changes", font=(THEME["font_main"], 9, "bold"),
                             bg=THEME["accent"], fg="#FFF", relief=tk.FLAT, pady=6,
                             cursor="hand2", command=_save)
        save_btn.pack(fill=tk.X)

    def on_close(self):
        self.running = False
        self.stop_ringtone()
        self.root.destroy()


if __name__ == "__main__":
    root = tk.Tk()
    app = LumaChatDesktopApp(root)
    root.mainloop()
