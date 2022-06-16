package io.finn.signald.db;

import java.sql.SQLException;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public interface IMessageQueueTable {
  // column names
  String ID = "id";
  String ACCOUNT = "account";
  String VERSION = "version";
  String TYPE = "type";
  String SOURCE_E164 = "source_e164";
  String SOURCE_UUID = "source_uuid";
  String SOURCE_DEVICE = "source_device";
  String TIMESTAMP = "timestamp";
  String CONTENT = "content";
  String LEGACY_MESSAGE = "legacy_message";
  String SERVER_RECEIVED_TIMESTAMP = "server_received_timestamp";
  String SERVER_UUID = "server_uuid";
  String SERVER_DELIVERED_TIMESTAMP = "server_delivered_timestamp";
  String DESTINATION_UUID = "destination_uuid";

  long storeEnvelope(SignalServiceEnvelope envelope) throws SQLException;
  void deleteEnvelope(long id) throws SQLException;
  StoredEnvelope nextEnvelope() throws SQLException;
  void deleteAccount(String account) throws SQLException;
}
