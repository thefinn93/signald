package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.annotations.ExactlyOneOfRequired.SYNC_REQUEST;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExactlyOneOfRequired;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

/**
 * Note: This is tied to the fields in {@link io.finn.signald.clientprotocol.v1.JsonSyncMessage}, but it seems more
 * convenient from a protocol standpoint to enforce that only one field is set at once (this is how sync messages work).
 * Other fields can be added if needed (although signald probably automatically handles some of these sync message
 * types)
 */
@ProtocolType("send_sync_message")
@Doc("Sends a sync message to the account's devices")
public class SendSyncMessageRequest implements RequestType<JsonSendMessageResult> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Required public String account;

  @JsonProperty("view_once_open_message")
  @ExactlyOneOfRequired(SYNC_REQUEST)
  @Doc("This can be set to indicate to other devices about having viewed a view-once message.")
  public JsonViewOnceOpenMessage viewOnceOpenMessage;

  @JsonProperty("message_request_response")
  @ExactlyOneOfRequired(SYNC_REQUEST)
  @Doc("This can be set to indicate to other devices about a response to an incoming message request from an "
       + "unknown user or group. Warning: Using the BLOCK and BLOCK_AND_DELETE options relies on other devices to do "
       + "the blocking, and it does not make you leave the group!")
  public JsonMessageRequestResponseMessage messageRequestResponse;

  @Override
  public JsonSendMessageResult run(Request request) throws InvalidRequestError, RateLimitError, InternalError, UnregisteredUserError, NoSuchAccountError, ServerNotFoundError,
                                                           InvalidProxyError, AuthorizationFailedError, SQLError {
    Manager manager = Common.getManager(account);

    final SignalServiceSyncMessage syncMessage;
    try {
      if (messageRequestResponse != null) {
        syncMessage = SignalServiceSyncMessage.forMessageRequestResponse(messageRequestResponse.toLibSignalClass());
      } else if (viewOnceOpenMessage != null) {
        syncMessage = SignalServiceSyncMessage.forViewOnceOpen(viewOnceOpenMessage.toLibSignalClass());
      } else {
        throw new InvalidRequestError("missing fields");
      }
    } catch (IOException e) {
      throw new InvalidRequestError(e.getMessage());
    }

    final SendMessageResult result;
    try {
      result = manager.getMessageSender().sendSyncMessage(syncMessage, Optional.absent());
    } catch (UnregisteredUserException e) {
      throw new UnregisteredUserError(e);
    } catch (RateLimitException e) {
      throw new RateLimitError(e);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedError(e);
    } catch (IOException e) {
      throw new InternalError("error sending message", e);
    } catch (UntrustedIdentityException e) {
      throw new InternalError("untrusted identity when sending sync message to self", e);
    }

    return new JsonSendMessageResult(result);
  }
}
