# 💬 Java Chat Server – Socket Programming

A console-based multithreaded chat server built in Java using `Socket` and `ServerSocket`. This project allows multiple clients to connect to a single server via local network and exchange messages in real-time.

---

## 🛠 Features

- 🧵 Multithreading support for multiple clients
- 👤 Unique username validation on connect
- 💬 Broadcast messaging to all connected users
- 🔐 Private messaging via `@username` command
- 📃 Chat commands:
  - `@username message` – Send a private message
  - `LIST` – Show all connected users
  - `PING` – Get a quick response from the server
- 👑 Leader election logic among connected clients

---

## 📂 Project Structure

```bash
java-chat-app/
│
├── src/
│   ├── Server.java             # Launches the server
│   ├── ClientHandler.java      # Handles each connected client
│   ├── Coordinator.java        # Manages leader election
│
├── .gitignore                  # Ignores Eclipse settings and bin/
└── README.md                   # You're here!
