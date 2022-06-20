package io.finn.signald;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;

public class SenderKeyGroupEventsLogger implements SignalServiceMessageSender.SenderKeyGroupEvents {
  private static final Logger logger = LogManager.getLogger();
  public static final SenderKeyGroupEventsLogger INSTANCE = new SenderKeyGroupEventsLogger();
  private boolean syncMessageSent = false;

  @Override
  public void onSenderKeyShared() {
    logger.debug("sender key shared");
  }

  @Override
  public void onMessageEncrypted() {
    logger.debug("message encrypted");
  }

  @Override
  public void onMessageSent() {
    logger.debug("message sent");
  }

  @Override
  public void onSyncMessageSent() {
    logger.debug("sync message sent");
    syncMessageSent = true;
  }

  public boolean isSyncMessageSent() { return syncMessageSent; }
}
