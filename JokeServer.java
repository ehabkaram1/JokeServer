import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final ToggleMode modeManager = new ToggleMode();
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
    private static final AtomicInteger jokeIndex = new AtomicInteger(0);
    private static final AtomicInteger proverbIndex = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        int q_len = 6;
        int clientPort = 45565;
        int adminPort = 45566;

        ServerSocket clientSock = new ServerSocket(clientPort, q_len);
        ServerSocket adminSock = new ServerSocket(adminPort, q_len);

        System.out.println("Joke Server starting up, listening at port " + clientPort + " for clients and port " + adminPort + " for admin.");

        // Client handler thread
        new Thread(() -> {
            while (true) {
                try {
                    Socket sock = clientSock.accept();
                    System.out.println("Client connection from " + sock);
                    new JokeWorker(sock).start();
                } catch (IOException e) {
                    System.out.println("Server client connection error: " + e.getMessage());
                }
            }
        }).start();

        // Admin handler thread
        new Thread(() -> {
            while (true) {
                try (Socket sock = adminSock.accept();
                     PrintWriter out = new PrintWriter(sock.getOutputStream(), true)) {
                    System.out.println("Admin connection from " + sock);

                    boolean inJokeMode = modeManager.toggleMode();
                    String modeString = inJokeMode ? "Joke Mode" : "Proverb Mode";
                    System.out.println("Mode changed to: " + modeString);
                    out.println("Mode is now: " + modeString);
                } catch (IOException e) {
                    System.out.println("Server admin connection error: " + e.getMessage());
                }
            }
        }).start();
    }

    // JokeWorker as an inner class of JokeServer
   // JokeWorker as an inner class of JokeServer
// JokeWorker as an inner class of JokeServer
// JokeWorker as an inner class of JokeServer

static class JokeWorker extends Thread {
    private final Socket sock;

    JokeWorker(Socket sock) {
        this.sock = sock;
    }

    public void run() {
        try (ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {
            String message;
            int index;
            boolean isJokeMode;
            synchronized (modeManager) {
                isJokeMode = modeManager.getMode();
            }
            if (isJokeMode) { // Joke mode
                index = jokeIndex.get();
                message = jokes[index];
                out.writeObject(message);
                System.out.println("Sent: " + message);
                if (index == jokes.length - 1) {
                    System.out.println("JOKE CYCLE COMPLETED");
                }
                jokeIndex.set((index + 1) % jokes.length);
            } else { // Proverb mode
                index = proverbIndex.get();
                message = proverbs[index];
                out.writeObject(message);
                System.out.println("Sent: " + message);
                if (index == proverbs.length - 1) {
                    System.out.println("PROVERB CYCLE COMPLETED");
                }
                proverbIndex.set((index + 1) % proverbs.length);
            }
        } catch (IOException e) {
            System.out.println("JokeWorker encountered an error: " + e.getMessage());
        }
    }
}


// JokeClient.java
// JokeClient.java

class JokeClient {
    public static void main(String argv[]) {
        String serverName = argv.length < 1 ? "localhost" : argv[0];
        int port = 45565; // Ensure this matches the server's client port

        try (Socket socket = new Socket(serverName, port);
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            System.out.println("Connected to the JokeServer at port " + port + ".");
            String response = (String) ois.readObject(); // Read the joke or proverb
            System.out.println("Received: " + response);
        } catch (Exception e) {
            System.out.println("JokeClient error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

class JokeClientAdmin {
    public static void main(String argv[]) {
        String serverName = argv.length < 1 ? "localhost" : argv[0];
        int adminPort = 45566; // Ensure this matches the server's admin port

        try (Socket socket = new Socket(serverName, adminPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            System.out.println("Connected to the JokeServer Admin interface at port " + adminPort + ".");
            String modeUpdate = reader.readLine(); // Read the mode change confirmation
            System.out.println("Server responded: " + modeUpdate);
        } catch (Exception e) {
            System.out.println("JokeClientAdmin error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
}