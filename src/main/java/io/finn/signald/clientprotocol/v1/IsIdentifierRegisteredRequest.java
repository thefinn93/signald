package io.finn.signald.clientprotocol.v1;

import io.finn.signald.SignalDependencies;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.whispersystems.signalservice.api.push.ACI;

@ProtocolType("is_identifier_registered")
@Doc("Determine whether an account identifier is registered on the Signal service.")
public class IsIdentifierRegisteredRequest implements RequestType<BooleanMessage> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use to retrieve the remote config") @Required public String account;

  @ExampleValue(ExampleValue.REMOTE_UUID)
  @Doc("The UUID of an identifier to check if it is registered on Signal. This UUID is either a Phone Number Identity (PNI) or an Account Identity (ACI).")
  @Required
  public String identifier;

  @Override
  public BooleanMessage run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError {
    final UUID accountUUID;
    try {
      accountUUID = AccountsTable.getUUID(account);
    } catch (SQLException e) {
      throw new InternalError("error getting local account UUID", e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }

    // We don't distinguish between an ACI and a PNI here, as they're both just strongly typed UUIDs. We just choose one
    // of them to get a subclass of AccountIdentifier.
    final ACI aci = ACI.from(UUID.fromString(identifier));
    final boolean isRegistered;
    try {
      // TODO: The endpoint used by this call doesn't actually need authentication. Maybe we can get rid of the
      //  account requirement later.
      isRegistered = SignalDependencies.get(accountUUID).getAccountManager().isIdentifierRegistered(aci);
    } catch (IOException | SQLException e) {
      throw new InternalError("error determining whether the identifier is registered", e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }

    return new BooleanMessage(isRegistered);
  }
}
