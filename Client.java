import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Client {
  private final Random randomGenerator;
  protected String host;
  protected int port;
  protected List<String> actions = new ArrayList<String>();
  protected String name = "DanglingPointers";

  Socket pingSocket = null;
  PrintWriter socketWriter = null;
  BufferedReader inputReader = null;

  public Client(String host, int port) {
    this.host = host;
    this.port = port;
    initialize_actions();
    randomGenerator = new Random();
  }

  private void initialize_actions() {
    actions.add("north west");
    actions.add("north");
    actions.add("north east");
    actions.add("east");
    actions.add("south east");
    actions.add("south");
    actions.add("south west");
    actions.add("west");
  }

  public static void main(String[] args) {
    Client c = new Client("localhost", 8023);
    try {
      c.connectAndRegister();
      c.play_game();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void sendMessage(String msg) {
    System.out.println("Sending message: " + msg);
    socketWriter.println(msg + "\r");
  }

  public String getMessage() throws IOException {
    String msg = inputReader.readLine();
    System.out.println("Got message: " + msg);
    return msg;
  }

  private void connectAndRegister() throws IOException {
    pingSocket = new Socket(this.host, this.port);
    socketWriter = new PrintWriter(pingSocket.getOutputStream(), true);
    inputReader = new BufferedReader(new InputStreamReader(pingSocket.getInputStream()));

    String server_response = getMessage();
    if (server_response.equals("What's your name?")) {
      sendMessage(name);
    }
  }

  private void play_game() throws IOException {
    while (true) {
      String server_response = getMessage();
      if (server_response.startsWith(name + " is active player")
          || server_response.startsWith("invalid move")) {
        String msg = actions.get(randomGenerator.nextInt(actions.size()));
        sendMessage(msg);
      } else if (server_response.startsWith("Game is on")) {
        System.out.println("Game ON");
      } else if (server_response.contains("won a goal was made")
          || server_response.contains("checkmate")) {
        System.out.println("Game is done");
        break;
      }
    }

    socketWriter.close();
    inputReader.close();
    pingSocket.close();
  }
}
