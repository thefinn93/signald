package io.finn.signald;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RunnableMain implements Runnable {
  private String socketPath;

  public RunnableMain(String s) {
    this.socketPath = s;
  }

  public void run() {
    System.out.println("Starting signald with socket " + this.socketPath);
    Main.main(new String[]{"-v", "-s", this.socketPath});
  }
}
