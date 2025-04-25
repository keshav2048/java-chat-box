import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {
    private static final List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
    private final Socket socket;
    private final BufferedReader bufferedReader;
    private final BufferedWriter bufferedWriter;
    private final String clientUsername;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.clientUsername = validateUsername(bufferedReader.readLine().trim());
        
        registerClient();
        sendWelcomeMessages();
        broadcastSystemMessage(clientUsername + " has joined the chat");
    }

    private String processIncomingMessage(String rawMessage) throws IOException {
        System.out.println("[STEP 1] Raw message received: '" + rawMessage + "'");
        
        // First remove any duplicate prefix (username: username: )
        String duplicatePrefix = clientUsername + ": " + clientUsername + ": ";
        String cleanMessage = rawMessage.startsWith(duplicatePrefix)
            ? rawMessage.substring(duplicatePrefix.length())
            : rawMessage;
        
        System.out.println("[STEP 2] After duplicate check: '" + cleanMessage + "'");

        // Then remove single username prefix if present
        String singlePrefix = clientUsername + ":";
        if (cleanMessage.startsWith(singlePrefix)) {
            // Handle both "username:" and "username: " cases
            cleanMessage = cleanMessage.substring(singlePrefix.length()).trim();
            System.out.println("[STEP 3a] After single prefix removal: '" + cleanMessage + "'");
        } else {
            cleanMessage = cleanMessage.trim();
            System.out.println("[STEP 3b] No prefix to remove: '" + cleanMessage + "'");
        }
        
        // Handle commands
        if (cleanMessage.startsWith("@")) {
            System.out.println("[STEP 4a] Detected private message command");
            handlePrivateMessage(cleanMessage);
            return null;
        } else if (cleanMessage.equalsIgnoreCase("LIST")) {
            System.out.println("[STEP 4b] Detected LIST command");
            listUsers();
            return null;
        } else if (cleanMessage.equalsIgnoreCase("PING")) {
            System.out.println("[STEP 4c] Detected PING command");
            sendMessage("PONG");
            return null;
        } else if (cleanMessage.isEmpty()) {
            System.out.println("[STEP 4d] Detected empty message - ignoring");
            return null;
        }
        
        System.out.println("[STEP 5] Regular message ready for broadcasting");
        return cleanMessage;
    }

    @Override
    public void run() {
        System.out.println("[RUN START] Handler started for " + clientUsername);
        
        try {
            while (socket.isConnected() && !socket.isClosed()) {
                System.out.println("[RUN LOOP] Waiting for message...");
                
                String rawMessage = bufferedReader.readLine();
                System.out.println("[RUN READ] Received: " + 
                    (rawMessage != null ? "'" + rawMessage + "'" : "NULL (disconnected)"));
                
                if (rawMessage == null) {
                    System.out.println("[RUN EXIT] Null message detected - client disconnected");
                    break;
                }
                
                try {
                    String processedMessage = processIncomingMessage(rawMessage);
                    System.out.println("[RUN PROCESS] Result: " + 
                        (processedMessage != null ? "'" + processedMessage + "'" : "NULL (command handled)"));
                    
                    if (processedMessage != null) {
                    	String finalMessage = clientUsername + ": " + processedMessage;

                        
                        System.out.println("[RUN BROADCAST] Final formatted message: '" + finalMessage + "'");
                        broadcastChatMessage(finalMessage);
                    }
                } catch (IOException e) {
                    System.out.println("[RUN ERROR] Processing failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.out.println("[RUN FATAL] Connection error: " + e.getMessage());
        } finally {
            System.out.println("[RUN CLEANUP] Closing resources for " + clientUsername);
            closeEverything();
        }
    }
    private String cleanMessage(String message) {
        // Remove duplicate username prefix if exists
        String duplicatePrefix = clientUsername + ": " + clientUsername + ": ";
        if (message.startsWith(duplicatePrefix)) {
            return message.substring(duplicatePrefix.length());
        }
        return message.trim();
    }

    private void processClientMessage(String message) throws IOException {
        if (message.startsWith("@")) {
            handlePrivateMessage(message);
        } 
        else if (message.equalsIgnoreCase("LIST")) {
            listUsers();
        }
        else if (message.equalsIgnoreCase("PING")) {
            sendMessage("PONG");
        }
        else {
            // Add username prefix if not already present
            if (!message.startsWith(clientUsername + ":")) {
                message = clientUsername + ": " + message;
            }
            broadcastChatMessage(message);
        }
    }

    // Helper methods remain the same as original
    private String validateUsername(String username) throws IOException {
        synchronized (clientHandlers) {
            if (isUsernameTaken(username)) {
                sendMessage("SERVER: Username '" + username + "' already taken. Disconnecting...");
                throw new IOException("Duplicate username");
            }
            return username;
        }
    }

    private boolean isUsernameTaken(String username) {
        return clientHandlers.stream()
            .anyMatch(client -> client != this && 
                   client.getClientUsername().equalsIgnoreCase(username));
    }

    private void registerClient() {
        synchronized (clientHandlers) {
            clientHandlers.add(this);
            Coordinator.electLeader();
        }
    }

    private void sendWelcomeMessages() throws IOException {
        sendMessage("SERVER: Welcome " + clientUsername);
        sendMessage("SERVER: Commands:");
        sendMessage("SERVER: - @username message (private message)");
        sendMessage("SERVER: - LIST (show online users)");
        
        ClientHandler leader = Coordinator.getLeader();
        if (leader != null) {
            sendMessage("SERVER: Current leader is " + leader.getClientUsername());
        }
    }

    private void handlePrivateMessage(String message) throws IOException {
        int spaceIndex = message.indexOf(" ");
        if (spaceIndex < 1) {
            sendMessage("SERVER: Invalid private message format. Use @username message");
            return;
        }
        
        String recipientUsername = message.substring(1, spaceIndex).trim();
        String privateMessage = message.substring(spaceIndex + 1).trim();
        
        if (privateMessage.isEmpty()) {
            sendMessage("SERVER: Message cannot be empty");
            return;
        }
        
        findRecipient(recipientUsername).ifPresentOrElse(
            recipient -> {
				try {
					sendPrivateMessage(recipient, recipientUsername, privateMessage);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			},
            () -> {
				try {
					sendMessage("SERVER: User '" + recipientUsername + "' not found or offline");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
        );
    }

    private Optional<ClientHandler> findRecipient(String username) {
        return clientHandlers.stream()
            .filter(client -> client.getClientUsername().equalsIgnoreCase(username))
            .findFirst();
    }

    private void sendPrivateMessage(ClientHandler recipient, String recipientUsername, String message) throws IOException {
        recipient.sendMessage("[PM from " + clientUsername + "]: " + message);
        sendMessage("[PM to " + recipientUsername + "]: " + message);
    }

    private void listUsers() throws IOException {
        StringBuilder userList = new StringBuilder("SERVER: Online users (" + clientHandlers.size() + "):\n");
        clientHandlers.forEach(client -> {
            userList.append("- ").append(client.getClientUsername());
            if (client.equals(Coordinator.getLeader())) {
                userList.append(" (Leader)");
            }
            userList.append("\n");
        });
        sendMessage(userList.toString());
    }

    private void broadcastChatMessage(String message) {
        clientHandlers.forEach(client -> {
            if (!client.equals(this)) {
                try {
                    client.sendMessage(message);
                } catch (IOException e) {
                    client.cleanup();
                }
            }
        });
    }

    private void broadcastSystemMessage(String message) {
        String formatted = "SERVER: " + message;
        clientHandlers.forEach(client -> {
            try {
                client.sendMessage(formatted);
            } catch (IOException e) {
                client.cleanup();
            }
        });
    }

    public void sendMessage(String message) throws IOException {
        synchronized (bufferedWriter) {
            bufferedWriter.write(message);
            bufferedWriter.newLine();
            bufferedWriter.flush();
        }
    }

    public void closeEverything() {
        cleanup();
    }

    private void cleanup() {
        synchronized (clientHandlers) {
            if (clientHandlers.remove(this)) {
                if (Coordinator.getLeader() != null && Coordinator.getLeader().equals(this)) {
                    Coordinator.electLeader();
                }
                broadcastSystemMessage(clientUsername + " has left the chat");
            }
        }
        closeResources();
    }

    private void closeResources() {
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("Error closing resources for " + clientUsername);
        }
    }

    public static List<ClientHandler> getClientHandlers() {
        return new ArrayList<>(clientHandlers);
    }

    public String getClientUsername() {
        return clientUsername;
    }
}

class Coordinator {
    private static volatile ClientHandler leader = null;
    
    public static synchronized void electLeader() {
        List<ClientHandler> clients = ClientHandler.getClientHandlers();
        leader = clients.stream()
            .max(Comparator.comparing(c -> c.getClientUsername().toLowerCase()))
            .orElse(null);
        
        if (leader != null) {
            announceLeader();
        }
    }
    
    public static synchronized ClientHandler getLeader() {
        return leader;
    }
    
    private static void announceLeader() {
        String message = "SERVER: New leader elected - " + leader.getClientUsername();
        ClientHandler.getClientHandlers().forEach(client -> {
            try {
                client.sendMessage(message);
            } catch (IOException e) {
                client.closeEverything();
            }
        });
    }
}