package com.example.lumachat.engine;

import java.util.Random;

public class SmartReplyEngine {

    public static class BotReply {
        public String senderName;
        public String senderAvatar;
        public String replyText;

        public BotReply(String senderName, String senderAvatar, String replyText) {
            this.senderName = senderName;
            this.senderAvatar = senderAvatar;
            this.replyText = replyText;
        }
    }

    private static final Random RANDOM = new Random();

    public static BotReply generateReply(String convId, String contactName, String contactAvatar,
                                         boolean isGroup, String userMessage) {
        String msg = (userMessage != null) ? userMessage.toLowerCase().trim() : "";

        // Saved Messages handled separately
        if ("saved_messages".equals(convId)) {
            return new BotReply("Saved Messages", "♡", "Note saved to your personal space 📌");
        }

        if (isGroup) {
            return generateGroupReply(convId, msg);
        } else {
            String text = generateDirectReply(contactName, msg);
            return new BotReply(contactName, contactAvatar, text);
        }
    }

    private static BotReply generateGroupReply(String convId, String msg) {
        if ("circle_besties".equals(convId)) {
            String[] members = {"Ananya", "Sravani", "Priya"};
            String[] avatars = {"A", "S", "P"};
            int idx = RANDOM.nextInt(members.length);
            String name = members[idx];
            String avatar = avatars[idx];

            String reply;
            if (msg.contains("meet") || msg.contains("plan") || msg.contains("today") || msg.contains("time")) {
                reply = name + ": Count me in! What time works best for everyone? ✨";
            } else if (msg.contains("hi") || msg.contains("hey") || msg.contains("hello")) {
                reply = name + ": Hey! Hope everyone is having a fabulous day! 💕";
            } else if (msg.contains("food") || msg.contains("coffee") || msg.contains("eat")) {
                reply = name + ": I'm craving boba or iced coffee right now 🧋";
            } else {
                String[] replies = {
                        name + ": Totally agree! 🥳",
                        name + ": Haha that's awesome! 😂",
                        name + ": Yay! Can't wait for our get-together!",
                        name + ": Sending you all good vibes today! ✨",
                        name + ": Let's definitely do this soon!"
                };
                reply = replies[RANDOM.nextInt(replies.length)];
            }
            return new BotReply(name, avatar, reply);
        } else if ("circle_college".equals(convId)) {
            String[] members = {"Rahul", "Neha", "Arjun"};
            String[] avatars = {"R", "N", "A"};
            int idx = RANDOM.nextInt(members.length);
            String name = members[idx];
            String avatar = avatars[idx];

            String reply;
            if (msg.contains("assignment") || msg.contains("project") || msg.contains("submit") || msg.contains("code")) {
                reply = name + ": The rubric says we need to submit the documentation too! 📚";
            } else if (msg.contains("exam") || msg.contains("class") || msg.contains("prof")) {
                reply = name + ": Prof said tomorrow's lecture starts sharp at 10 AM ⏰";
            } else {
                String[] replies = {
                        name + ": On it, let's sync up after class!",
                        name + ": Thanks for the reminder 👍",
                        name + ": Did anyone test the latest build?",
                        name + ": Sounds like a solid plan 🎓"
                };
                reply = replies[RANDOM.nextInt(replies.length)];
            }
            return new BotReply(name, avatar, reply);
        } else if ("circle_family".equals(convId)) {
            return new BotReply("Mom", "M", "Mom: Have you eaten lunch yet, beta? Take good care of yourself ❤️");
        } else {
            return new BotReply("Member", "★", "Sounds great! Looking forward to it 😊");
        }
    }

    private static String generateDirectReply(String contactName, String msg) {
        if (msg.contains("hi") || msg.contains("hello") || msg.contains("hey")) {
            String[] greetings = {
                    "Hey! So glad you messaged! How's your day going? ✨",
                    "Hello! Hope you're having a wonderful day! 😊",
                    "Hey there! What's new with you today? 🌸"
            };
            return greetings[RANDOM.nextInt(greetings.length)];
        }

        if (msg.contains("how are you") || msg.contains("how r u") || msg.contains("how're you")) {
            return "I'm doing great, thank you for asking! How are things on your side? 💫";
        }

        if (msg.contains("coffee") || msg.contains("tea") || msg.contains("cafe") || msg.contains("drink")) {
            return "Coffee sounds wonderful! Let me know when and where ☕🥐";
        }

        if (msg.contains("where") || msg.contains("when") || msg.contains("time")) {
            return "Does 5:00 PM work for you? Or earlier if you'd like! ⌚";
        }

        if (msg.contains("thank") || msg.contains("thx") || msg.contains("appreciate")) {
            return "You're most welcome! Always here for you 🥰";
        }

        if (msg.contains("yes") || msg.contains("ok") || msg.contains("cool") || msg.contains("sure") || msg.contains("great") || msg.contains("awesome")) {
            return "Perfect! It's settled then 🎉";
        }

        if (msg.contains("bye") || msg.contains("good night") || msg.contains("gn") || msg.contains("see you")) {
            return "Take care and talk soon! Have a peaceful rest 🌙✨";
        }

        if (msg.contains("photo") || msg.contains("pic") || msg.contains("image")) {
            return "Oh wow, that looks beautiful! Thanks for sharing 📸💖";
        }

        String[] general = {
                "That sounds so exciting! Tell me more about it! ✨",
                "I was just thinking about that earlier today! What a coincidence! 💭",
                "Aww love that! You always make my day brighter 😊",
                "Haha that made me smile! You're the best! 🌸",
                "Totally agree with you on this! 💖",
                "Let's catch up properly over the weekend! 🌟"
        };
        return general[RANDOM.nextInt(general.length)];
    }
}
