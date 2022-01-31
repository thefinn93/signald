package io.finn.signald.jobs;

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.metadata.ProtocolException;
import org.signal.libsignal.metadata.protocol.UnidentifiedSenderMessageContent;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class SendRetryMessageRequestRequest implements Job {
  private static final Logger logger = LogManager.getLogger();
  private final Account account;
  private final ProtocolException protocolException;
  private final SignalServiceEnvelope envelope;

  public SendRetryMessageRequestRequest(Account account, ProtocolException e, SignalServiceEnvelope envelope) {
    this.account = account;
    protocolException = e;
    this.envelope = envelope;
  }

  @Override
  public void run() throws SQLException, IOException, NoSuchAccountException, ServerNotFoundException, InvalidProxyException, InvalidKeyException {
    Recipient sender = account.getRecipients().get(protocolException.getSender());
    account.getProtocolStore().archiveAllSessions(sender);

    int senderDevice = protocolException.getSenderDevice();
    Optional<byte[]> groupId = protocolException.getGroupId();

    byte[] originalContent;
    int envelopeType;
    if (protocolException.getUnidentifiedSenderMessageContent().isPresent()) {
      UnidentifiedSenderMessageContent messageContent = protocolException.getUnidentifiedSenderMessageContent().get();
      originalContent = messageContent.getContent();
      envelopeType = messageContent.getType();
    } else {
      originalContent = envelope.getContent();
      envelopeType = envelopeTypeToCiphertextMessageType(envelope.getType());
    }

    DecryptionErrorMessage decryptionErrorMessage = DecryptionErrorMessage.forOriginalMessage(originalContent, envelopeType, envelope.getTimestamp(), senderDevice);
    Optional<UnidentifiedAccessPair> unidentifiedAccessPair = Manager.get(account.getACI()).getAccessPairFor(sender);
    logger.debug("Sending decryption error message");
    try {
      account.getSignalDependencies().getMessageSender().sendRetryReceipt(sender.getAddress(), unidentifiedAccessPair, groupId, decryptionErrorMessage);
    } catch (UntrustedIdentityException e) {
      account.getProtocolStore().handleUntrustedIdentityException(e);
    }
  }

  private static int envelopeTypeToCiphertextMessageType(int envelopeType) {
    switch (envelopeType) {
    case SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE_VALUE:
      return CiphertextMessage.PREKEY_TYPE;
    case SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER_VALUE:
      return CiphertextMessage.SENDERKEY_TYPE;
    case SignalServiceProtos.Envelope.Type.PLAINTEXT_CONTENT_VALUE:
      return CiphertextMessage.PLAINTEXT_CONTENT_TYPE;
    default:
      return CiphertextMessage.WHISPER_TYPE;
    }
  }
}
