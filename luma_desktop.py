#!/usr/bin/env python3
"""
=============================================================================
🌸 LUMACHAT DESKTOP - PYTHON TKINTER GUI CLIENT
=============================================================================
A modern, cross-platform Tkinter GUI application for LumaChat.
Connects to the LumaChat Node.js Relay Server & Ngrok Cloud Tunnel to enable:
  - Real-time chatting between PC (Tkinter) and Mobile (Android phones)
  - Live Online User Discovery (detects Android phones & other PCs)
  - Incoming & Outgoing Voice and Video Call signaling with Ringtone audio
  - Sound notifications on new messages
  - Live Server Diagnostics & Network Monitor

Designed for dual-submission: Demonstrates a Python Tkinter GUI alongside
the Native Android Mobile App.
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

# Windows sound support
try:
    import winsound
    HAS_WINSOUND = True
except ImportError:
    HAS_WINSOUND = False

# Configuration and State
DEFAULT_SERVER = "http://localhost:8080"
CONFIG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "luma_profile.json")

# Visual Styling Palette (Modern Luma Theme)
THEME = {
    "bg_dark": "#13111C",         # Deep luxury dark
    "bg_card": "#1E1B2E",         # Card background
    "bg_sidebar": "#181528",      # Sidebar background
    "bg_chat": "#0F0D17",         # Message log canvas
    "accent_primary": "#7C3AED",  # Luma Purple / Indigo
    "accent_hover": "#6D28D9",
    "accent_green": "#10B981",    # Online indicator
    "accent_coral": "#EF4444",    # Call end / alert
    "accent_blue": "#3B82F6",     # Voice call
    "bubble_incoming": "#242038", # Bubble for messages from friends
    "bubble_outgoing": "#4C1D95", # Bubble for our own messages
    "text_primary": "#F3F4F6",    # Crisp white
    "text_secondary": "#9CA3AF",  # Muted grey
    "text_muted": "#6B7280",
    "border_color": "#2D2845",
    "font_family": "Segoe UI" if sys.platform == "win32" else "Helvetica"
}

class LumaChatDesktopApp:
    def __init__(self, root):
        self.root = root
        self.root.title("🌸 LumaChat Desktop - Tkinter GUI Client")
        self.root.geometry("1060x720")
        self.root.minsize(860, 580)
        self.root.configure(bg=THEME["bg_dark"])

        # Load or initialize user profile
        self.load_profile()

        # Network state
        self.server_url = self.profile.get("server_url", DEFAULT_SERVER)
        self.user_id = self.profile.get("user_id", f"pc_{int(time.time())}")
        self.my_name = self.profile.get("name", "Desktop User 💻")
        self.my_avatar = self.profile.get("avatar", "💻")
        self.my_bio = self.profile.get("bio", "Connected via Tkinter GUI")

        self.active_peer = None  # {userId, name, avatar}
        self.online_users = []
        self.last_poll_time = int(time.time() * 1000) - 30000  # last 30s
        self.is_connected = False
        self.is_ringing = False
        self.current_call = None
        self.active_call_window = None

        # Thread communication queues
        self.msg_queue = queue.Queue()

        # Setup GUI Components
        self.setup_styles()
        self.build_ui()

        # Start Background Networking Threads
        self.running = True
        self.network_thread = threading.Thread(target=self.network_loop, daemon=True)
        self.network_thread.start()

        # Start Tkinter event processor
        self.process_incoming_events()

        # Initial register
        self.register_with_server()

        # Window closing handler
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    # =========================================================================
    # 1. PROFILE & CONFIGURATION
    # =========================================================================
    def load_profile(self):
        default = {
            "name": "Desktop User 💻",
            "avatar": "💻",
            "bio": "Chatting from Python Tkinter Client",
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
            print("[Config] Error saving:", e)

    # =========================================================================
    # 2. UI STYLING & SETUP
    # =========================================================================
    def setup_styles(self):
        self.style = ttk.Style()
        self.style.theme_use("clam")

        # Configure generic styles
        self.style.configure(".", background=THEME["bg_dark"], foreground=THEME["text_primary"])
        self.style.configure("TFrame", background=THEME["bg_dark"])
        self.style.configure("Card.TFrame", background=THEME["bg_card"])
        self.style.configure("Sidebar.TFrame", background=THEME["bg_sidebar"])

        # Scrollbar
        self.style.configure("Vertical.TScrollbar", background=THEME["border_color"],
                             troughcolor=THEME["bg_dark"], bordercolor=THEME["bg_dark"])

    def build_ui(self):
        # Master Layout: Top Header + Main Body (Sidebar + Chat Area) + Bottom Status
        self.build_top_header()

        self.body_frame = tk.Frame(self.root, bg=THEME["bg_dark"])
        self.body_frame.pack(fill=tk.BOTH, expand=True, padx=12, pady=(0, 6))

        self.build_sidebar()
        self.build_chat_panel()
        self.build_status_bar()

    # -------------------------------------------------------------------------
    # Top Navigation & Header
    # -------------------------------------------------------------------------
    def build_top_header(self):
        header = tk.Frame(self.root, bg=THEME["bg_card"], height=64, padx=16, pady=10)
        header.pack(fill=tk.X, side=tk.TOP, pady=(0, 10))

        # Brand / App Title
        brand_frame = tk.Frame(header, bg=THEME["bg_card"])
        brand_frame.pack(side=tk.LEFT)

        logo_label = tk.Label(brand_frame, text="🌸", font=(THEME["font_family"], 20),
                              bg=THEME["bg_card"], fg="#EC4899")
        logo_label.pack(side=tk.LEFT, padx=(0, 8))

        title_box = tk.Frame(brand_frame, bg=THEME["bg_card"])
        title_box.pack(side=tk.LEFT)

        title_lbl = tk.Label(title_box, text="LumaChat Desktop", font=(THEME["font_family"], 14, "bold"),
                             bg=THEME["bg_card"], fg=THEME["text_primary"])
        title_lbl.pack(anchor="w")

        subtitle_lbl = tk.Label(title_box, text="Tkinter Cross-City GUI Client • Connected to Mobile Network",
                                font=(THEME["font_family"], 8), bg=THEME["bg_card"], fg=THEME["text_secondary"])
        subtitle_lbl.pack(anchor="w")

        # Right Controls: Server Switcher & Profile Badge
        ctrl_frame = tk.Frame(header, bg=THEME["bg_card"])
        ctrl_frame.pack(side=tk.RIGHT)

        # Server Status Pill
        self.status_pill = tk.Label(ctrl_frame, text="🟡 Connecting...", font=(THEME["font_family"], 9, "bold"),
                                    bg="#374151", fg="#FBBF24", padx=10, pady=4, relief=tk.FLAT)
        self.status_pill.pack(side=tk.LEFT, padx=(0, 10))

        # Server Change Button
        server_btn = tk.Button(ctrl_frame, text="🌐 Server URL", font=(THEME["font_family"], 9),
                               bg=THEME["border_color"], fg=THEME["text_primary"],
                               activebackground=THEME["accent_primary"], activeforeground="#FFF",
                               relief=tk.FLAT, padx=8, pady=3, command=self.change_server_dialog, cursor="hand2")
        server_btn.pack(side=tk.LEFT, padx=(0, 12))

        # Profile Badge (Clickable)
        self.profile_btn = tk.Button(ctrl_frame, text=f"{self.my_avatar} {self.my_name}",
                                     font=(THEME["font_family"], 10, "bold"),
                                     bg=THEME["accent_primary"], fg="#FFFFFF",
                                     activebackground=THEME["accent_hover"], activeforeground="#FFFFFF",
                                     relief=tk.FLAT, padx=12, pady=4, cursor="hand2", command=self.edit_profile_dialog)
        self.profile_btn.pack(side=tk.LEFT)

    # -------------------------------------------------------------------------
    # Left Sidebar: Online Friends (Android Phones & PCs)
    # -------------------------------------------------------------------------
    def build_sidebar(self):
        sidebar = tk.Frame(self.body_frame, bg=THEME["bg_sidebar"], width=280)
        sidebar.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 10))
        sidebar.pack_propagate(False)

        # Sidebar Title
        sb_header = tk.Frame(sidebar, bg=THEME["bg_sidebar"], padx=14, pady=12)
        sb_header.pack(fill=tk.X)

        sb_title = tk.Label(sb_header, text="🟢 ONLINE FRIENDS", font=(THEME["font_family"], 10, "bold"),
                            bg=THEME["bg_sidebar"], fg=THEME["accent_green"])
        sb_title.pack(side=tk.LEFT)

        refresh_btn = tk.Button(sb_header, text="🔄", font=(THEME["font_family"], 9),
                                bg=THEME["border_color"], fg=THEME["text_secondary"],
                                activebackground=THEME["accent_primary"], activeforeground="#FFF",
                                relief=tk.FLAT, padx=6, pady=1, command=self.fetch_users_now, cursor="hand2")
        refresh_btn.pack(side=tk.RIGHT)

        # User List Scrollable Container
        list_container = tk.Frame(sidebar, bg=THEME["bg_sidebar"])
        list_container.pack(fill=tk.BOTH, expand=True, padx=6, pady=4)

        self.users_canvas = tk.Canvas(list_container, bg=THEME["bg_sidebar"], highlightthickness=0)
        self.users_scrollbar = tk.Scrollbar(list_container, orient="vertical", command=self.users_canvas.yview, bg=THEME["bg_sidebar"])
        self.users_scroll_frame = tk.Frame(self.users_canvas, bg=THEME["bg_sidebar"])

        self.users_scroll_frame.bind(
            "<Configure>",
            lambda e: self.users_canvas.configure(scrollregion=self.users_canvas.bbox("all"))
        )

        self.users_canvas_window = self.users_canvas.create_window((0, 0), window=self.users_scroll_frame, anchor="nw", width=256)
        self.users_canvas.configure(yscrollcommand=self.users_scrollbar.set)

        self.users_canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.users_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        # Broadcast Channel / Everyone Option
        broadcast_card = tk.Frame(sidebar, bg=THEME["bg_card"], padx=12, pady=10)
        broadcast_card.pack(fill=tk.X, side=tk.BOTTOM, padx=8, pady=8)

        b_btn = tk.Button(broadcast_card, text="📢 Global Broadcast (All Devices)", font=(THEME["font_family"], 9, "bold"),
                          bg=THEME["accent_primary"], fg="#FFF", activebackground=THEME["accent_hover"],
                          relief=tk.FLAT, pady=6, cursor="hand2",
                          command=lambda: self.select_peer(None))
        b_btn.pack(fill=tk.X)

    # -------------------------------------------------------------------------
    # Main Chat & Calling Panel
    # -------------------------------------------------------------------------
    def build_chat_panel(self):
        self.chat_panel = tk.Frame(self.body_frame, bg=THEME["bg_card"])
        self.chat_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        # Peer Info Bar / Chat Header
        self.peer_header = tk.Frame(self.chat_panel, bg=THEME["bg_card"], padx=16, pady=12,
                                    highlightthickness=1, highlightbackground=THEME["border_color"])
        self.peer_header.pack(fill=tk.X, side=tk.TOP)

        # Left: Peer Avatar & Name
        peer_info = tk.Frame(self.peer_header, bg=THEME["bg_card"])
        peer_info.pack(side=tk.LEFT)

        self.peer_avatar_lbl = tk.Label(peer_info, text="📢", font=(THEME["font_family"], 18),
                                        bg=THEME["border_color"], fg="#FFF", width=3, pady=2)
        self.peer_avatar_lbl.pack(side=tk.LEFT, padx=(0, 10))

        name_box = tk.Frame(peer_info, bg=THEME["bg_card"])
        name_box.pack(side=tk.LEFT)

        self.peer_name_lbl = tk.Label(name_box, text="Global Network Room", font=(THEME["font_family"], 13, "bold"),
                                      bg=THEME["bg_card"], fg=THEME["text_primary"])
        self.peer_name_lbl.pack(anchor="w")

        self.peer_status_lbl = tk.Label(name_box, text="Broadcasting to all Android phones & PCs across cities",
                                        font=(THEME["font_family"], 8), bg=THEME["bg_card"], fg=THEME["text_secondary"])
        self.peer_status_lbl.pack(anchor="w")

        # Right: Voice & Video Call Action Buttons
        self.call_actions = tk.Frame(self.peer_header, bg=THEME["bg_card"])
        self.call_actions.pack(side=tk.RIGHT)

        self.voice_call_btn = tk.Button(self.call_actions, text="📞 Voice Call", font=(THEME["font_family"], 9, "bold"),
                                        bg=THEME["accent_blue"], fg="#FFFFFF", activebackground="#2563EB",
                                        relief=tk.FLAT, padx=12, pady=6, cursor="hand2",
                                        command=lambda: self.initiate_call("voice"))
        self.voice_call_btn.pack(side=tk.LEFT, padx=(0, 8))

        self.video_call_btn = tk.Button(self.call_actions, text="📹 Video Call", font=(THEME["font_family"], 9, "bold"),
                                        bg=THEME["accent_green"], fg="#FFFFFF", activebackground="#059669",
                                        relief=tk.FLAT, padx=12, pady=6, cursor="hand2",
                                        command=lambda: self.initiate_call("video"))
        self.video_call_btn.pack(side=tk.LEFT)

        # Message History Canvas / Scrollable View
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

        # Bottom Input Area
        input_bar = tk.Frame(self.chat_panel, bg=THEME["bg_card"], padx=12, pady=10,
                             highlightthickness=1, highlightbackground=THEME["border_color"])
        input_bar.pack(fill=tk.X, side=tk.BOTTOM)

        # Emoji Shortcuts
        emoji_bar = tk.Frame(input_bar, bg=THEME["bg_card"])
        emoji_bar.pack(fill=tk.X, pady=(0, 6))

        for em in ["🌸", "❤️", "😂", "👍", "🔥", "🎉", "✨", "📱", "💻"]:
            b = tk.Button(emoji_bar, text=em, font=(THEME["font_family"], 10),
                          bg=THEME["bg_card"], fg=THEME["text_primary"],
                          activebackground=THEME["border_color"], relief=tk.FLAT, padx=4, pady=1,
                          command=lambda e=em: self.insert_emoji(e), cursor="hand2")
            b.pack(side=tk.LEFT, padx=2)

        # Input Row: Text Field + Send Button
        row = tk.Frame(input_bar, bg=THEME["bg_card"])
        row.pack(fill=tk.X)

        self.msg_entry = tk.Entry(row, font=(THEME["font_family"], 11),
                                  bg=THEME["bg_chat"], fg=THEME["text_primary"],
                                  insertbackground="#FFF", relief=tk.FLAT,
                                  highlightthickness=1, highlightbackground=THEME["border_color"])
        self.msg_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, ipady=8, padx=(0, 8))
        self.msg_entry.bind("<Return>", lambda e: self.send_message())

        send_btn = tk.Button(row, text="Send 🚀", font=(THEME["font_family"], 10, "bold"),
                             bg=THEME["accent_primary"], fg="#FFFFFF",
                             activebackground=THEME["accent_hover"], activeforeground="#FFFFFF",
                             relief=tk.FLAT, padx=18, ipady=5, cursor="hand2",
                             command=self.send_message)
        send_btn.pack(side=tk.RIGHT)

    def on_canvas_resize(self, event):
        self.msg_canvas.itemconfig(self.msg_window, width=event.width)

    def scroll_messages_to_bottom(self):
        self.msg_canvas.configure(scrollregion=self.msg_canvas.bbox("all"))
        self.msg_canvas.yview_moveto(1.0)

    def insert_emoji(self, em):
        self.msg_entry.insert(tk.END, em)
        self.msg_entry.focus_set()

    # -------------------------------------------------------------------------
    # Bottom Status Bar (Network latency, Ngrok status, Diagnostics)
    # -------------------------------------------------------------------------
    def build_status_bar(self):
        status_bar = tk.Frame(self.root, bg=THEME["bg_dark"], height=26, padx=12, pady=4)
        status_bar.pack(fill=tk.X, side=tk.BOTTOM)

        self.net_status_lbl = tk.Label(status_bar, text="Ready", font=(THEME["font_family"], 8),
                                       bg=THEME["bg_dark"], fg=THEME["text_muted"])
        self.net_status_lbl.pack(side=tk.LEFT)

        self.tech_stack_lbl = tk.Label(status_bar,
                                       text="Platform: Python 3.12 (Tkinter GUI) 💻  ↔  Android (Java 17/XML) 📱",
                                       font=(THEME["font_family"], 8, "italic"),
                                       bg=THEME["bg_dark"], fg=THEME["text_secondary"])
        self.tech_stack_lbl.pack(side=tk.RIGHT)

    # =========================================================================
    # 3. MESSAGE DISPLAY (Modern Chat Bubbles)
    # =========================================================================
    def append_message_bubble(self, sender_name, sender_avatar, text, time_str, is_outgoing=False):
        bubble_row = tk.Frame(self.msg_container, bg=THEME["bg_chat"], padx=8, pady=4)
        bubble_row.pack(fill=tk.X, anchor="e" if is_outgoing else "w")

        # Bubble Card
        card_bg = THEME["bubble_outgoing"] if is_outgoing else THEME["bubble_incoming"]
        card = tk.Frame(bubble_row, bg=card_bg, padx=12, pady=8,
                        highlightthickness=1, highlightbackground=THEME["border_color"])

        if is_outgoing:
            card.pack(side=tk.RIGHT, anchor="e", padx=(60, 4))
        else:
            card.pack(side=tk.LEFT, anchor="w", padx=(4, 60))

        # Sender & Time Header inside card
        header = tk.Frame(card, bg=card_bg)
        header.pack(fill=tk.X, pady=(0, 3))

        sender_tag = f"{sender_avatar} {sender_name}" if not is_outgoing else "You (Desktop)"
        name_lbl = tk.Label(header, text=sender_tag, font=(THEME["font_family"], 9, "bold"),
                            bg=card_bg, fg="#FDE047" if not is_outgoing else "#A7F3D0")
        name_lbl.pack(side=tk.LEFT, padx=(0, 8))

        time_lbl = tk.Label(header, text=time_str, font=(THEME["font_family"], 7),
                            bg=card_bg, fg=THEME["text_muted"])
        time_lbl.pack(side=tk.RIGHT)

        # Message Text
        msg_lbl = tk.Label(card, text=text, font=(THEME["font_family"], 10),
                           bg=card_bg, fg=THEME["text_primary"],
                           wraplength=520, justify=tk.LEFT)
        msg_lbl.pack(anchor="w")

        # Play sound on incoming message
        if not is_outgoing and HAS_WINSOUND:
            try:
                winsound.MessageBeep(winsound.MB_ICONASTERISK)
            except Exception:
                pass

        self.root.after(10, self.scroll_messages_to_bottom)

    # =========================================================================
    # 4. PEER SELECTION & ONLINE USERS LIST
    # =========================================================================
    def select_peer(self, user):
        self.active_peer = user
        if user is None:
            self.peer_name_lbl.config(text="Global Network Room")
            self.peer_avatar_lbl.config(text="📢")
            self.peer_status_lbl.config(text="Broadcasting to all Android phones & PCs across cities")
        else:
            self.peer_name_lbl.config(text=user.get("name", "Unknown Friend"))
            self.peer_avatar_lbl.config(text=user.get("avatar", "📱"))
            bio = user.get("bio", "Active on LumaChat")
            self.peer_status_lbl.config(text=f"🟢 Online Mobile Device • {bio}")

    def render_users_list(self):
        # Clear existing children
        for widget in self.users_scroll_frame.winfo_children():
            widget.destroy()

        if not self.online_users:
            empty_lbl = tk.Label(self.users_scroll_frame, text="No other users online yet.\nOpen LumaChat on your phone!",
                                 font=(THEME["font_family"], 9), bg=THEME["bg_sidebar"], fg=THEME["text_muted"],
                                 justify=tk.CENTER, pady=20)
            empty_lbl.pack(fill=tk.X)
            return

        for user in self.online_users:
            uid = user.get("userId")
            if uid == self.user_id:
                continue  # Don't list ourselves

            u_name = user.get("name", "Friend")
            u_avatar = user.get("avatar", "📱")
            u_bio = user.get("bio", "Online")

            card = tk.Frame(self.users_scroll_frame, bg=THEME["bg_card"], padx=10, pady=8,
                            highlightthickness=1, highlightbackground=THEME["border_color"], cursor="hand2")
            card.pack(fill=tk.X, pady=3, padx=2)

            # Left: Avatar
            av_lbl = tk.Label(card, text=u_avatar, font=(THEME["font_family"], 14),
                              bg=THEME["border_color"], fg="#FFF", width=3, pady=2)
            av_lbl.pack(side=tk.LEFT, padx=(0, 8))

            # Center: Name & Bio
            center = tk.Frame(card, bg=THEME["bg_card"])
            center.pack(side=tk.LEFT, fill=tk.X, expand=True)

            name_lbl = tk.Label(center, text=u_name, font=(THEME["font_family"], 10, "bold"),
                                bg=THEME["bg_card"], fg=THEME["text_primary"], anchor="w")
            name_lbl.pack(fill=tk.X)

            bio_lbl = tk.Label(center, text=u_bio[:28] + ("..." if len(u_bio) > 28 else ""),
                               font=(THEME["font_family"], 7), bg=THEME["bg_card"], fg=THEME["text_secondary"], anchor="w")
            bio_lbl.pack(fill=tk.X)

            # Right: Online Dot + Chat Button
            dot_lbl = tk.Label(card, text="●", font=(THEME["font_family"], 10),
                               bg=THEME["bg_card"], fg=THEME["accent_green"])
            dot_lbl.pack(side=tk.RIGHT)

            # Bind clicks
            for w in [card, av_lbl, center, name_lbl, bio_lbl, dot_lbl]:
                w.bind("<Button-1>", lambda e, u=user: self.select_peer(u))

    # =========================================================================
    # 5. NETWORKING & HTTP RELAY CALLS
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
                self.msg_queue.put(("STATUS", "🟢 Connected"))
            except Exception as e:
                self.msg_queue.put(("STATUS", f"🔴 Reconnecting ({str(e)[:18]})"))

        threading.Thread(target=_reg, daemon=True).start()

    def send_message(self):
        text = self.msg_entry.get().strip()
        if not text:
            return

        self.msg_entry.delete(0, tk.END)
        time_str = datetime.now().strftime("%I:%M %p")

        # Display immediately in our own chat
        self.append_message_bubble(self.my_name, self.my_avatar, text, time_str, is_outgoing=True)

        # Send to server in background
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
                self.msg_queue.put(("LOG", f"Failed to send: {e}"))

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
                self.msg_queue.put(("LOG", f"Error fetching users: {e}"))

        threading.Thread(target=_get, daemon=True).start()

    # -------------------------------------------------------------------------
    # Background Network Poller & Heartbeat
    # -------------------------------------------------------------------------
    def network_loop(self):
        last_heartbeat = 0
        while self.running:
            now = time.time()

            # 1. Heartbeat every 15s to stay marked online on Android phones
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

            # 2. Poll for new messages and active users
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

                    self.msg_queue.put(("STATUS", "🟢 Connected"))
            except Exception as e:
                self.msg_queue.put(("STATUS", "🟡 Reconnecting..."))

            time.sleep(2.0)

    # =========================================================================
    # 6. VOICE & VIDEO CALL SIGNALING & RINGTONE
    # =========================================================================
    def initiate_call(self, call_type="voice"):
        target_name = self.active_peer.get("name") if self.active_peer else "Everyone on Network"
        target_id = self.active_peer.get("userId") if self.active_peer else None

        # Send call_offer to server
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

        def _send_call():
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
                self.msg_queue.put(("LOG", f"Call signal error: {e}"))

        threading.Thread(target=_send_call, daemon=True).start()

        # Open Outgoing Call Window
        self.show_call_window(call_type, target_name, is_outgoing=True)

    def handle_incoming_call_signal(self, call_data):
        caller_name = call_data.get("senderName", "Android Phone")
        caller_avatar = call_data.get("senderAvatar", "📱")
        call_type = call_data.get("callType", "voice")

        # Start ringing sound loop
        self.start_ringtone()

        # Display Incoming Call Modal Dialog
        call_dialog = tk.Toplevel(self.root)
        call_dialog.title(f"📞 Incoming {call_type.title()} Call!")
        call_dialog.geometry("380x300")
        call_dialog.configure(bg=THEME["bg_card"])
        call_dialog.attributes("-topmost", True)

        lbl_icon = tk.Label(call_dialog, text=caller_avatar, font=(THEME["font_family"], 48),
                            bg=THEME["bg_card"], fg="#FFF")
        lbl_icon.pack(pady=(20, 10))

        lbl_caller = tk.Label(call_dialog, text=f"{caller_name}", font=(THEME["font_family"], 16, "bold"),
                              bg=THEME["bg_card"], fg=THEME["text_primary"])
        lbl_caller.pack()

        lbl_type = tk.Label(call_dialog, text=f"Incoming {call_type.title()} Call from Android Phone...",
                            font=(THEME["font_family"], 10), bg=THEME["bg_card"], fg=THEME["text_secondary"])
        lbl_type.pack(pady=(4, 20))

        # Accept / Decline Buttons
        btn_box = tk.Frame(call_dialog, bg=THEME["bg_card"])
        btn_box.pack(pady=10)

        def _accept():
            self.stop_ringtone()
            call_dialog.destroy()
            # Send call_answer
            self.send_call_signal("call_answer", call_type, caller_name)
            self.show_call_window(call_type, caller_name, is_outgoing=False)

        def _decline():
            self.stop_ringtone()
            call_dialog.destroy()
            self.send_call_signal("call_reject", call_type, caller_name)

        btn_accept = tk.Button(btn_box, text="📞 Accept", font=(THEME["font_family"], 11, "bold"),
                               bg=THEME["accent_green"], fg="#FFF", activebackground="#059669",
                               relief=tk.FLAT, padx=16, pady=8, cursor="hand2", command=_accept)
        btn_accept.pack(side=tk.LEFT, padx=10)

        btn_decline = tk.Button(btn_box, text="✕ Decline", font=(THEME["font_family"], 11, "bold"),
                                bg=THEME["accent_coral"], fg="#FFF", activebackground="#DC2626",
                                relief=tk.FLAT, padx=16, pady=8, cursor="hand2", command=_decline)
        btn_decline.pack(side=tk.LEFT, padx=10)

    def show_call_window(self, call_type, peer_name, is_outgoing=True):
        if self.active_call_window and self.active_call_window.winfo_exists():
            self.active_call_window.destroy()

        call_win = tk.Toplevel(self.root)
        call_win.title(f"🌸 LumaCall - {call_type.title()} ({peer_name})")
        call_win.geometry("460x520")
        call_win.configure(bg="#0B0914")
        self.active_call_window = call_win

        # Video / Avatar Display Area
        disp_frame = tk.Frame(call_win, bg="#131024", highlightthickness=1, highlightbackground=THEME["border_color"])
        disp_frame.pack(fill=tk.BOTH, expand=True, padx=20, pady=20)

        if call_type == "video":
            cam_lbl = tk.Label(disp_frame, text="📹\n[Peer Video Active]\nConnected to Android Camera Stream",
                               font=(THEME["font_family"], 12, "bold"), bg="#131024", fg="#A7F3D0")
            cam_lbl.pack(expand=True)

            pip_box = tk.Label(disp_frame, text="🤳 Selfie PiP", font=(THEME["font_family"], 8),
                               bg="#242038", fg="#FFF", padx=8, pady=4, relief=tk.SOLID, borderwidth=1)
            pip_box.place(relx=0.75, rely=0.08)
        else:
            av_lbl = tk.Label(disp_frame, text="🎙️\n[Voice Call Connected]",
                              font=(THEME["font_family"], 14, "bold"), bg="#131024", fg="#93C5FD")
            av_lbl.pack(expand=True)

        # Peer Name & Call Status
        name_lbl = tk.Label(call_win, text=peer_name, font=(THEME["font_family"], 15, "bold"),
                            bg="#0B0914", fg=THEME["text_primary"])
        name_lbl.pack()

        timer_lbl = tk.Label(call_win, text="00:00", font=(THEME["font_family"], 11),
                             bg="#0B0914", fg=THEME["accent_green"])
        timer_lbl.pack(pady=(2, 16))

        # Call Duration Timer
        start_t = time.time()
        def _update_timer():
            if call_win.winfo_exists():
                elapsed = int(time.time() - start_t)
                mins = elapsed // 60
                secs = elapsed % 60
                timer_lbl.config(text=f"{mins:02d}:{secs:02d}")
                call_win.after(1000, _update_timer)

        _update_timer()

        # Call Controls Bar (Mute, Camera Flip, End Call)
        ctrl_bar = tk.Frame(call_win, bg="#0B0914")
        ctrl_bar.pack(pady=(0, 24))

        is_muted = [False]
        def _toggle_mute():
            is_muted[0] = not is_muted[0]
            mute_btn.config(text="🔇 Unmute" if is_muted[0] else "🎙️ Mute",
                            bg="#4B5563" if is_muted[0] else THEME["border_color"])

        mute_btn = tk.Button(ctrl_bar, text="🎙️ Mute", font=(THEME["font_family"], 9, "bold"),
                             bg=THEME["border_color"], fg="#FFF", relief=tk.FLAT, padx=12, pady=6,
                             cursor="hand2", command=_toggle_mute)
        mute_btn.pack(side=tk.LEFT, padx=8)

        def _end_call():
            self.stop_ringtone()
            self.send_call_signal("call_end", call_type, peer_name)
            call_win.destroy()

        end_btn = tk.Button(ctrl_bar, text="🛑 End Call", font=(THEME["font_family"], 10, "bold"),
                            bg=THEME["accent_coral"], fg="#FFF", activebackground="#DC2626",
                            relief=tk.FLAT, padx=18, pady=6, cursor="hand2", command=_end_call)
        end_btn.pack(side=tk.LEFT, padx=8)

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
    # 7. EVENT PROCESSOR & DIALOGS
    # =========================================================================
    def process_incoming_events(self):
        try:
            while not self.msg_queue.empty():
                evt, data = self.msg_queue.get_nowait()
                if evt == "STATUS":
                    self.status_pill.config(
                        text=data,
                        bg="#064E3B" if "Connected" in data else "#7F1D1D",
                        fg="#34D399" if "Connected" in data else "#FCA5A5"
                    )
                elif evt == "USER_LIST":
                    self.online_users = data
                    self.render_users_list()
                elif evt == "MESSAGE":
                    s_name = data.get("senderName", "Friend")
                    s_av = data.get("senderAvatar", "📱")
                    txt = data.get("text", "")
                    tm = data.get("time", "")
                    self.append_message_bubble(s_name, s_av, txt, tm, is_outgoing=False)
                elif evt == "CALL":
                    self.handle_incoming_call_signal(data)
                elif evt == "LOG":
                    self.net_status_lbl.config(text=str(data))
        except Exception:
            pass

        self.root.after(100, self.process_incoming_events)

    def change_server_dialog(self):
        new_url = simpledialog.askstring("Server URL",
                                         "Enter Relay Server or Ngrok URL:\n(e.g. http://localhost:8080 or https://...ngrok-free.dev)",
                                         initialvalue=self.server_url, parent=self.root)
        if new_url:
            self.server_url = new_url.strip()
            self.profile["server_url"] = self.server_url
            self.save_profile()
            self.register_with_server()
            messagebox.showinfo("Server Updated", f"Connected to server:\n{self.server_url}", parent=self.root)

    def edit_profile_dialog(self):
        dlg = tk.Toplevel(self.root)
        dlg.title("Edit Your Profile")
        dlg.geometry("400x320")
        dlg.configure(bg=THEME["bg_card"], padx=18, pady=18)
        dlg.transient(self.root)

        tk.Label(dlg, text="Your Name:", font=(THEME["font_family"], 10, "bold"),
                 bg=THEME["bg_card"], fg=THEME["text_primary"]).pack(anchor="w")
        name_ent = tk.Entry(dlg, font=(THEME["font_family"], 11), bg=THEME["bg_chat"], fg="#FFF")
        name_ent.insert(0, self.my_name)
        name_ent.pack(fill=tk.X, pady=(2, 10), ipady=4)

        tk.Label(dlg, text="Choose Avatar Emoji:", font=(THEME["font_family"], 10, "bold"),
                 bg=THEME["bg_card"], fg=THEME["text_primary"]).pack(anchor="w")
        av_box = tk.Frame(dlg, bg=THEME["bg_card"])
        av_box.pack(fill=tk.X, pady=(2, 10))

        selected_av = [self.my_avatar]
        av_buttons = []

        def _pick(em, btn):
            selected_av[0] = em
            for b in av_buttons:
                b.config(bg=THEME["border_color"])
            btn.config(bg=THEME["accent_primary"])

        for em in ["💻", "🚀", "⭐", "🦊", "🌸", "🐱", "🎮", "🎧"]:
            b = tk.Button(av_box, text=em, font=(THEME["font_family"], 12),
                          bg=THEME["accent_primary"] if em == self.my_avatar else THEME["border_color"],
                          fg="#FFF", relief=tk.FLAT, padx=6, pady=2, cursor="hand2")
            b.config(command=lambda e=em, btn=b: _pick(e, btn))
            b.pack(side=tk.LEFT, padx=3)
            av_buttons.append(b)

        tk.Label(dlg, text="Bio / Status:", font=(THEME["font_family"], 10, "bold"),
                 bg=THEME["bg_card"], fg=THEME["text_primary"]).pack(anchor="w")
        bio_ent = tk.Entry(dlg, font=(THEME["font_family"], 11), bg=THEME["bg_chat"], fg="#FFF")
        bio_ent.insert(0, self.my_bio)
        bio_ent.pack(fill=tk.X, pady=(2, 16), ipady=4)

        def _save():
            self.my_name = name_ent.get().strip() or "Desktop User 💻"
            self.my_avatar = selected_av[0]
            self.my_bio = bio_ent.get().strip() or "Chatting from Tkinter"
            self.profile["name"] = self.my_name
            self.profile["avatar"] = self.my_avatar
            self.profile["bio"] = self.my_bio
            self.save_profile()
            self.profile_btn.config(text=f"{self.my_avatar} {self.my_name}")
            self.register_with_server()
            dlg.destroy()

        save_btn = tk.Button(dlg, text="Save & Update Profile ✨", font=(THEME["font_family"], 10, "bold"),
                             bg=THEME["accent_primary"], fg="#FFF", relief=tk.FLAT, pady=6,
                             cursor="hand2", command=_save)
        save_btn.pack(fill=tk.X)

    def on_close(self):
        self.running = False
        self.stop_ringtone()
        self.root.destroy()


# =============================================================================
# MAIN ENTRYPOINT
# =============================================================================
if __name__ == "__main__":
    root = tk.Tk()
    app = LumaChatDesktopApp(root)
    root.mainloop()
