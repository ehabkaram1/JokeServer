import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

class ToggleMode {
    private final AtomicInteger mode = new AtomicInteger(0);

    // Toggle the mode and return the NEW state
    public boolean toggleMode() {
        mode.getAndSet(mode.get() == 0 ? 1 : 0);
        return getMode(); // Return the new mode state
    }

    // True for joke mode, false for proverb mode
    public boolean getMode() {
        return mode.get() == 0;
    }
}

public class JokeServer {
    private static final String[] jokes = {
            "JA Parallel lines have so much in common. It’s a shame they’ll never meet.",
            "JB My granddad has the heart of a lion and a lifetime ban from the zoo.",
            "JC Why don’t skeletons fight each other? They don’t have the guts.",
            "JD What do you call an alligator in a vest? An investigator."
    };
    private static final String[] proverbs = {
            "PA The early bird might get the worm, but the second mouse gets the cheese.",
            "PB Fortune favors the bold.",
            "PC A watched pot never boils.",
            "PD Better to light a candle than to curse the darkness."
    };
    private static final AtomicBoolean isJokeMode = new AtomicBoolean(true);
    private static final ConcurrentHashMap<String, ClientState> clientStates = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int clientPort = 4545;
        int adminPort = 4546;

        ServerSocket clientSock = new ServerSocket(clientPort);
        ServerSocket adminSock = new ServerSocket(adminPort);

        System.out.println("Joke Server starting up, listening at port " + clientPort + " for clients and port " + adminPort + " for admin.");

        new Thread(() -> {
            while (true) {
                try {
                    Socket clientSocket = clientSock.accept();
                    new Thread(new ClientHandler(clientSocket)).start();
                } catch (IOException e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }).start();

        new Thread(() -> {
            while (true) {
                try {
                    Socket adminSocket = adminSock.accept();
                    new Thread(new AdminHandler(adminSocket)).start();
                } catch (IOException e) {
                    System.err.println("Error accepting admin connection: " + e.getMessage());
                }
            }
        }).start();
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

                String userName = (String) in.readObject();
                System.out.println("User " + userName + " connected.");

                String clientKey = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                ClientState state = clientStates.computeIfAbsent(clientKey, k -> new ClientState());

                while (true) {
                    // Assuming the client sends a specific request object or signal; adjust as necessary
                    Object request = in.readObject();
                    if (request instanceof String && "quit".equalsIgnoreCase((String) request)) {
                        break;
                    }

                    String response;
                    if (isJokeMode.get()) {
                        response = getJoke(state);
                    } else {
                        response = getProverb(state);
                    }
                    out.writeObject(response);
                    out.flush();
                }
            } catch (EOFException eof) {
                // Client disconnected
                System.out.println("Client disconnected.");
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error handling client: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private String getJoke(ClientState state) {
            int index = state.getNextJokeIndex();
            return jokes[index];
        }

        private String getProverb(ClientState state) {
            int index = state.getNextProverbIndex();
            return proverbs[index];
        }
    }

    static class ClientState {
        private int jokeIndex = 0;
        private int proverbIndex = 0;

        public synchronized int getNextJokeIndex() {
            int index = jokeIndex % jokes.length;
            jokeIndex++;
            if (jokeIndex % jokes.length == 0) {
                System.out.println("JOKE CYCLE COMPLETED");
            }
            return index;
        }

        public synchronized int getNextProverbIndex() {
            int index = proverbIndex % proverbs.length;
            proverbIndex++;
            if (proverbIndex % proverbs.length == 0) {
                System.out.println("PROVERB CYCLE COMPLETED");
            }
            return index;
        }
    }

    static class AdminHandler implements Runnable {
        private Socket adminSocket;

        AdminHandler(Socket socket) {
            this.adminSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(adminSocket.getInputStream()));
                 PrintWriter writer = new PrintWriter(adminSocket.getOutputStream(), true)) {

                String command;
                while ((command = reader.readLine()) != null) {
                    if ("toggle".equalsIgnoreCase(command)) {
                        toggleMode();
                        writer.println("Server mode changed to: " + (isJokeMode.get() ? "Joke Mode" : "Proverb Mode"));
                    }
                }
                System.out.println("Admin connection closed.");
            } catch (IOException e) {
                System.err.println("Admin handler error: " + e.getMessage());
            } finally {
                try {
                    adminSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing admin socket: " + e.getMessage());
                }
            }
        }

        private void toggleMode() {
            isJokeMode.set(!isJokeMode.get());
            System.out.println("Mode toggled to: " + (isJokeMode.get() ? "Joke" : "Proverb"));
        }
    }
}


// JokeClient.java
// JokeClient.java

class JokeClient {
    public static void main(String[] args) {
        String serverName = args.length > 0 ? args[0] : "localhost";
        int port = 4545; // Adjust if your server is listening on a different port

        System.out.println("JokeClient started. Connecting to " + serverName + " on port " + port);

        try (Socket socket = new Socket(serverName, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.print("Enter your name: ");
            String userName = consoleInput.readLine();
            out.writeObject(userName); // Send user name to the server

            String userInput;
            System.out.println("Type 'quit' to exit or press Enter to get a joke or a proverb.");
            while (!(userInput = consoleInput.readLine()).equalsIgnoreCase("quit")) {
                out.writeObject("request"); // Send a request signal to the server
                out.flush();

                String response = (String) in.readObject(); // Read response from the server
                System.out.println(response);

                System.out.println("\nType 'quit' to exit or press Enter to get another joke or a proverb.");
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Exception in JokeClient: " + e);
        }
    }
}

class JokeClientAdmin {
    public static void main(String[] args) {
        String serverName = args.length > 0 ? args[0] : "localhost";
        int adminPort = 4546; // Ensure this matches the server's admin port

        System.out.println("JokeClientAdmin started. Connecting to " + serverName + " on port " + adminPort);
        System.out.println("Press ENTER to toggle mode. Type 'quit' to exit.");

        try (Socket socket = new Socket(serverName, adminPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))) {

            while (true) {
                String userInput = consoleInput.readLine(); // Read user input from the console

                if ("quit".equalsIgnoreCase(userInput)) {
                    System.out.println("Exiting JokeClientAdmin...");
                    break; // Exit the loop and terminate the program
                }

                // Send a toggle command to the server
                out.println("toggle");
                // Read and print the server's response to the mode toggle command
                String response = in.readLine();
                System.out.println("Server response: " + response);

                // Prompt the user again after displaying the response
                System.out.println("Press ENTER to toggle mode again. Type 'quit' to exit.");
            }

        } catch (IOException e) {
            System.err.println("JokeClientAdmin encountered an error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
