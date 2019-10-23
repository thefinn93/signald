package io.finn.signald;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Assertions;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.File;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import io.finn.signald.testhelpers.RequestBuilder;
import io.finn.signald.testhelpers.MiscHelpers;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestRequest {

    private Thread signaldMain;
    private static File SOCKET_FILE = new File(System.getProperty("java.io.tmpdir"), "signald.sock");
    private AFUNIXSocket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    static final Logger logger = LoggerFactory.getLogger(TestRequest.class);
    private ObjectMapper mpr = new ObjectMapper();


    @BeforeAll
    public void startSignald() throws InterruptedException {
        signaldMain = new Thread(new RunnableMain(SOCKET_FILE.getAbsolutePath()), "main");
        signaldMain.start();
        while(!SOCKET_FILE.exists()) {
            logger.info("Waiting for " + SOCKET_FILE.getAbsolutePath() + " to exist...");
            TimeUnit.SECONDS.sleep(1);
        }
    }


    @AfterAll
    public void stopSignald() {
        signaldMain.interrupt();
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
    public void RegisterAccount() throws IOException {
        String username = String.format("+1202555%04d", ThreadLocalRandom.current().nextInt(0, 10000));

        writer.println(RequestBuilder.register(username));
        JsonNode root = mpr.readTree(reader.readLine());
        Assertions.assertEquals(root.findValue("type").textValue(), "verification_required");

        String code = MiscHelpers.getVerificationCode(username);

        System.out.println("Got verification code " + code);

        writer.println(RequestBuilder.verify(username, code));

        root = (JsonNode)mpr.readTree(reader.readLine());
        System.out.println(root);
        Assertions.assertEquals(root.findValue("type").textValue(), "verification_succeeded");
    }


    @DisplayName("List registered accounts")
    @Test
    public void ListAccounts() throws IOException {
        writer.println(RequestBuilder.listAccounts());

	JsonNode root = mpr.readTree(reader.readLine());

	Assertions.assertEquals(root.findValue("type").textValue(), "account_list");

	for(final JsonNode user : root.get("data")) {
             if(user != null && user.get("username") != null) {
                 System.out.println("Got account " + user.get("username").textValue());
             } else {
                 System.out.println("No accounts found, but no errors either!");
             }
        }
    }
}
