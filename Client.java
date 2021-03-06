import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.*;

// Represents a point on the game board.
class Point {
  public int row, col;
  // Only set on points generated from getAdjacentPoints.  Contains the
  // direction relative to the source point.
  public String derivedDir = null;
  Point(int r, int c) {
    this.row = r;
    this.col = c;
  }

  // Returns a new point translated by the provided deltas.
  Point move(int dr, int dc) {
    return new Point(row + dr, col + dc);
  }

  // Get all 8 points adjacent to this point, and set the derived directions.
  ArrayList<Point> getAdjacentPoints() {
    ArrayList<Point> adjacentPoints = new ArrayList<>();
    for (int i = -1; i <= 1; i++) {
      for (int j = -1; j <= 1; j++) {
        if (i == 0 && j == 0)
          continue;
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

    // Shuffle to remove any directional bias that may come with how we populate
    // the array list.
    Collections.shuffle(adjacentPoints);
    return adjacentPoints;
  }

  // Euclidean distance to the provided point.  It is key to use Euclidean here
  // as the agent is allowed to move diagonally.
  int dist(Point p) {
    int dr = p.row - row;
    int dc = p.col - col;
    return (int) Math.sqrt(dr * dr + dc * dc);
  }

  public String toString() {
    return "(" + row + ", " + col + ")";
  }
}

// A move with an associated heuristic score.  A lower score implies a better
// move.
class ScoredMove implements Comparable<ScoredMove> {
  Point point;
  int score;

  ScoredMove(Point f, int s) {
    this.point = f;
    this.score = s;
  }

  // Compare strictly on score.
  public int compareTo(ScoredMove p) {
    return score - p.score;
  }

  public String toString() {
    return "(" + point + "," + score + ")";
  }
}

public class Client {
  private final Random randomGenerator;
  protected String host;
  protected int port;
  protected List<String> actions = new ArrayList<String>();
  protected HashMap<Point, Integer> saturationMap = new HashMap<>();
  // TODO: fuck with other teams by making our name break shit.
  // Maybe: "ball is at (-1, -1)
  protected String name = "DanglingPointers";

  int currentIndex = -1;
  ArrayList<ScoredMove> scoredMoves = new ArrayList<>();

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
    // System.out.println("Sending message: " + msg);
    socketWriter.println(msg + "\r");
  }

  public String getMessage() throws IOException {
    String msg = inputReader.readLine();
    // System.out.println("Got message: " + msg);
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
  private int getSaturationScore(Point p, int baseScore) {
    // ALWAYS take the post bank shot.
    if ((p.row == 6 || p.row == 8) && Math.abs(p.col - goalPoint1.col) == 1) {
      // System.out.println("Ball is at: " + ballPoint + " and we expect a corner shot at: " + p);
      return -1000;
    }
    return 0;
  }

  // Recomputes the path if needed.  Instead of keeping the state of the game to
  // determine if a move is legal, we just try all moves in sorted order until
  // we hit a legal one.
  private void recomputePathIfNeeded() {
    if (currentIndex == -1) {
      scoredMoves = new ArrayList<ScoredMove>();
      ArrayList<Point> possibleMoves = ballPoint.getAdjacentPoints();
      for (Point p : possibleMoves) {
        int baseScore = Math.min(goalPoint1.dist(p), goalPoint2.dist(p));
        scoredMoves.add(new ScoredMove(p, baseScore + getSaturationScore(p, baseScore)));
      }
      Collections.sort(scoredMoves);
      currentIndex = 0;
    } else {
      currentIndex++;
    }
  }

  private void invalidateCurrentPath() {
    currentIndex = -1;
  }

  private void play_game() throws IOException {
    while (true) {
      String server_response = getMessage();

      if (server_response.startsWith(name + " is active player")
          || server_response.startsWith("invalid move")) {
        recomputePathIfNeeded();
        String msg;
        if (currentIndex >= scoredMoves.size()) {
          currentIndex = -1;
          msg = "north";
        } else {
          msg = scoredMoves.get(currentIndex).point.derivedDir;
        }
        sendMessage(msg);
      } else if (server_response.contains("your goal is")) {
        invalidateCurrentPath();
        String[] tokens = server_response.split(" ");
        String side = tokens[tokens.length - 3];

        if (side.equals("north")) {
          goalPoint1 = new Point(6, -1);
          goalPoint2 = new Point(7, -1);
        } else {
          goalPoint1 = new Point(6, 15);
          goalPoint2 = new Point(7, 15);
        }
      } else if (server_response.startsWith("ball is at")) {
        invalidateCurrentPath();
        String[] tokens = server_response.split(" ");
        int nTokens = tokens.length;

        String row = tokens[nTokens - 4];
        String col = tokens[nTokens - 3];

        int ballRow = Integer.parseInt(row.substring(1, row.length() - 1));
        int ballCol = Integer.parseInt(col.substring(0, col.length() - 1));
        ballPoint = new Point(ballRow, ballCol);

      } else if (server_response.contains("polarity of the goal has been inverted")) {
        invalidateCurrentPath();
        if (goalPoint1.col == -1) {
          goalPoint1 = new Point(6, 15);
          goalPoint2 = new Point(7, 15);
        } else {
          goalPoint1 = new Point(6, -1);
          goalPoint2 = new Point(7, -1);
        }
      } else if (server_response.startsWith("Game is on")) {
        invalidateCurrentPath();
        System.out.println("Game ON");
      } else if (server_response.contains("won a goal was made")
          || server_response.contains("checkmate")) {
        invalidateCurrentPath();
        System.out.println("Game is done");
        break;
      } else if (server_response.contains(" did go ")) {
        invalidateCurrentPath();
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
          saturationMap.putIfAbsent(ballPoint, 0);
          saturationMap.put(ballPoint, saturationMap.get(ballPoint) + 1);
          ballPoint = ballPoint.move(dr, dc);
          saturationMap.putIfAbsent(ballPoint, 0);
          saturationMap.put(ballPoint, saturationMap.get(ballPoint) + 1);
        }
      } else {
        invalidateCurrentPath();
      }
    }

    socketWriter.close();
    inputReader.close();
    pingSocket.close();
  }
}
