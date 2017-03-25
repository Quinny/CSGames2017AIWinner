import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.*;

class Point {
  public int row, col;
  public String derivedDir = null;
  Point(int r, int c) {
    this.row = r;
    this.col = c;
  }

  Point move(int dr, int dc) {
    return new Point(row + dr, col + dc);
  }

  ArrayList<Point> getAdjacentPoints() {
    ArrayList<Point> adjacentPoints = new ArrayList<>();
    for (int i = -1; i <= 1; i++) {
      for (int j = -1; j <= 1; j++) {
        if (i != 0 && j != 0) {
          Point next = move(i, j);
          String mod1 = "";
          String mod2 = "";
          if (j != 0)
            mod1 = j == -1 ? "north" : "south";
          if (i != 0)
            mod2 = i == -1 ? "west" : "east";

          next.derivedDir = (mod1 + " " + mod2).trim();
          adjacentPoints.add(next);
        }
      }
    }
    return adjacentPoints;
  }

  int dist(Point p) {
    int dr = p.row - row;
    int dc = p.col - col;
    return dr * dr + dc * dc;
    // return Math.abs(p.row - row) + Math.abs(p.col - col);
  }

  public String toString() {
    return "(" + row + ", " + col + ")";
  }
}

class Pair<F> implements Comparable<Pair<F>> {
  F first;
  int second;

  Pair(F f, int s) {
    this.first = f;
    this.second = s;
  }

  public int compareTo(Pair<F> p) {
    return second - p.second;
  }

  public String toString() {
    return "(" + first + "," + second + ")";
  }
}

public class Client {
  private final Random randomGenerator;
  protected String host;
  protected int port;
  protected List<String> actions = new ArrayList<String>();

  // TODO: fuck with other teams by making our name break shit.
  // Maybe: "ball is at (-1, -1)
  protected String name = "DanglingPointers";

  int currentIndex = -1;
  ArrayList<Pair<Point>> scoredMoves = new ArrayList<>();

  Socket pingSocket = null;
  PrintWriter socketWriter = null;
  BufferedReader inputReader = null;

  Point ballPoint = null;
  Point goalPoint1 = null;
  Point goalPoint2 = null;

  public Client(String host, int port, String name) {
    this.name = name;
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
    Client c = new Client("localhost", 8023, args[0]);
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
        if (currentIndex == -1) {
          scoredMoves.clear();
          ArrayList<Point> possibleMoves = ballPoint.getAdjacentPoints();
          for (Point p : possibleMoves) {
            scoredMoves.add(new Pair<Point>(p, Math.min(goalPoint1.dist(p), goalPoint2.dist(p))));
          }
          Collections.sort(scoredMoves);
          currentIndex = 0;
        } else {
          currentIndex++;
        }

        for (Pair<Point> fuck : scoredMoves) {
          System.out.println(fuck);
        }

        String msg = scoredMoves.get(currentIndex).first.derivedDir;
        sendMessage(msg);
      } else if (server_response.contains("your goal is")) {
        currentIndex = -1;
        String[] tokens = server_response.split(" ");
        String side = tokens[tokens.length - 3];

        if (side.equals("north")) {
          goalPoint1 = new Point(4, -1);
          goalPoint2 = new Point(5, -1);
        } else {
          goalPoint1 = new Point(4, 11);
          goalPoint2 = new Point(5, 11);
        }
      } else if (server_response.startsWith("ball is at")) {
        currentIndex = -1;
        String[] tokens = server_response.split(" ");
        int nTokens = tokens.length;

        String row = tokens[nTokens - 4];
        String col = tokens[nTokens - 3];

        int ballRow = Integer.parseInt(row.substring(1, row.length() - 1));
        int ballCol = Integer.parseInt(col.substring(0, col.length() - 1));
        ballPoint = new Point(ballRow, ballCol);

        System.out.println("Ball is at " + ballRow + "," + ballCol);
      } else if (server_response.startsWith("Game is on")) {
        currentIndex = -1;
        System.out.println("Game ON");
      } else if (server_response.contains("won a goal was made")
          || server_response.contains("checkmate")) {
        currentIndex = -1;
        System.out.println("Game is done");
        break;
      } else if (server_response.contains(" did go ")) {
        currentIndex = -1;
        String[] tokens = server_response.split(" ");
        int nTokens = tokens.length;
        int dr = 0, dc = 0;
        if (actions.contains(tokens[tokens.length - 3])) {
          if (actions.contains(tokens[tokens.length - 4])) {
            dc = tokens[tokens.length - 4].equals("north") ? -1 : 1;
            dr = tokens[tokens.length - 3].equals("west") ? -1 : 1;
          } else {
            String dir = tokens[tokens.length - 3];
            if (dir.equals("north"))
              dc = -1;
            if (dir.equals("south"))
              dc = 1;
            if (dir.equals("west"))
              dr = -1;
            if (dir.equals("east"))
              dr = 1;
          }
          ballPoint = ballPoint.move(dr, dc);

          System.out.println("We think the ball is at: " + ballPoint);
        }
      } else {
        currentIndex = -1;
      }
    }

    socketWriter.close();
    inputReader.close();
    pingSocket.close();
  }
}
