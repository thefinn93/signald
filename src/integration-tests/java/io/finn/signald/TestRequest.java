package io.finn.signald;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.testhelpers.MiscHelpers;
import io.finn.signald.testhelpers.RequestBuilder;
import org.junit.jupiter.api.*;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestRequest {
  private Thread signaldMain;
  private static final File SOCKET_FILE = new File("signald.sock");
  private AFUNIXSocket socket;
  private PrintWriter writer;
  private BufferedReader reader;
  static final Logger logger = LoggerFactory.getLogger(TestRequest.class);
  private final ObjectMapper mpr = new ObjectMapper();
  private List<String> accounts = new ArrayList<>();

  @BeforeAll
  public void startSignald() throws InterruptedException {
    if (SOCKET_FILE.exists()) {
      logger.info("Deleting socket file " + SOCKET_FILE.getAbsolutePath());
      SOCKET_FILE.delete();
    }
    signaldMain = new Thread(new RunnableMain(SOCKET_FILE.getAbsolutePath()), "main");
    signaldMain.start();
    while (!SOCKET_FILE.exists()) {
      logger.info("Waiting for " + SOCKET_FILE.getAbsolutePath() + " to exist...");
      TimeUnit.SECONDS.sleep(1);
    }
  }

  @AfterAll
  public void stopSignald() {
    signaldMain.interrupt();
    if (SOCKET_FILE.exists()) {
      logger.info("Deleting socket file " + SOCKET_FILE.getAbsolutePath());
      SOCKET_FILE.delete();
    }
  }

  @BeforeEach
  public void connectSocket() throws IOException {
    socket = AFUNIXSocket.newInstance();
    socket.connect(new AFUNIXSocketAddress(SOCKET_FILE));

    writer = new PrintWriter(socket.getOutputStream(), true);
    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    System.out.println(reader.readLine());
  }

  @AfterEach
  public void disconnectSocket() throws IOException {
    socket.close();
  }

  @DisplayName("Register a new account")
  @Test
  @Order(1)
  public void RegisterAccount() throws IOException {
    String username = String.format("+1202555%04d", ThreadLocalRandom.current().nextInt(0, 10000));

    writer.println(RequestBuilder.register(username));
    JsonNode root = mpr.readTree(reader.readLine());
    Assertions.assertEquals(root.findValue("type").textValue(), "verification_required");

    String code = MiscHelpers.getVerificationCode(username);

    System.out.println("Got verification code " + code);

    writer.println(RequestBuilder.verify(username, code));

    root = mpr.readTree(reader.readLine());
    System.out.println(root);
    Assertions.assertEquals(root.findValue("type").textValue(), "verification_succeeded");
    accounts.add(username);
  }

  @DisplayName("List registered accounts")
  @Test
  @Order(2)
  public void ListAccounts() throws IOException {
    writer.println(RequestBuilder.listAccounts());
    JsonNode root = mpr.readTree(reader.readLine());
    Assertions.assertEquals(root.findValue("type").textValue(), "account_list");
    for (final JsonNode user : root.get("data")) {
      if (user != null && user.get("username") != null) {
        System.out.println("Got account " + user.get("username").textValue());
      } else {
        System.out.println("No accounts found, but no errors either!");
      }
    }
    Assertions.assertEquals(accounts.size(), root.get("data").size());
  }

  @DisplayName("subscribe to incoming messages")
  @Test
  @Order(3)
  public void Subscribe() throws IOException, InterruptedException {
    Assertions.assertTrue(accounts.size() > 0);

    writer.println(RequestBuilder.subscribe(accounts.get(0)));
    JsonNode root = mpr.readTree(reader.readLine());
    Assertions.assertEquals(root.findValue("type").textValue(), "subscribed");

    root = mpr.readTree(reader.readLine());
    Assertions.assertEquals(root.findValue("type").textValue(), "listen_started");
    Assertions.assertEquals(root.findValue("data").textValue(), accounts.get(0));

    Thread.sleep(10000);
  }
}
