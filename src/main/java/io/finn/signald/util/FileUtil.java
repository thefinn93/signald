package io.finn.signald.util;

import static java.nio.file.attribute.PosixFilePermission.*;

import io.finn.signald.Config;
import io.finn.signald.db.Database;
import io.finn.signald.db.Recipient;
import io.finn.signald.storage.LegacyAccountData;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

// FileUtil should be used for all disk operations. Not everything has been moved to this yet, move stuff if you find it
// eventually we should make this able to support multiple backends like s3
public class FileUtil {
  private static String dataPath;
  private static String attachmentsPath;
  private static String avatarsPath;
  private static String stickersPath;

  public static void setDataPath() throws IOException {
    LogManager.getLogger().debug("Using data folder {}", Config.getDataPath());
    dataPath = Config.getDataPath() + "/data";
    attachmentsPath = Config.getDataPath() + "/attachments";
    avatarsPath = Config.getDataPath() + "/avatars";
    stickersPath = Config.getDataPath() + "/stickers";
    Database.Get().GroupsTable.setGroupAvatarPath(avatarsPath);
    createPrivateDirectories(dataPath);
    setLegacyDataPath();
  }

  @Deprecated
  private static void setLegacyDataPath() {
    LegacyAccountData.setDataPath(dataPath);
  }

  public static void createPrivateDirectories(String path) throws IOException {
    final Path file = new File(path).toPath();
    try {
      Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE);
      Files.createDirectories(file, PosixFilePermissions.asFileAttribute(perms));
    } catch (UnsupportedOperationException e) {
      Files.createDirectories(file);
    }
  }

  public static String getProfileAvatarPath(Recipient recipient) {
    if (recipient.getUUID() == null) {
      return null;
    }

    File f = getProfileAvatarFile(recipient);
    if (!f.exists()) {
      return null;
    }

    return f.getAbsolutePath();
  }

  public static File getProfileAvatarFile(Recipient recipient) { return new File(avatarsPath, recipient.getUUID().toString()); }

  public static File createTempFile() throws IOException { return File.createTempFile("signald_tmp_", ".tmp"); }

  public static Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(Recipient recipient) throws IOException {
    File file = getContactAvatarFile(recipient);
    if (!file.exists()) {
      return Optional.empty();
    }

    return Optional.of(createAttachment(file));
  }

  public static File getContactAvatarFile(Recipient recipient) {
    SignalServiceAddress address = recipient.getAddress();
    if (address.getNumber().isPresent()) {
      return new File(avatarsPath, "contact-" + address.getNumber().get());
    }
    return new File(avatarsPath, "contact-" + address.getIdentifier());
  }

  private static SignalServiceAttachmentStream createAttachment(File attachmentFile) throws IOException {
    InputStream attachmentStream = new FileInputStream(attachmentFile);
    final long attachmentSize = attachmentFile.length();
    String mime = Files.probeContentType(attachmentFile.toPath());
    if (mime == null) {
      mime = "application/octet-stream";
    }
    return new SignalServiceAttachmentStream(attachmentStream, mime, attachmentSize, Optional.of(attachmentFile.getName()), false, false, false, Optional.empty(), 0, 0,
                                             System.currentTimeMillis(), Optional.empty(), Optional.empty(), null, null, Optional.empty());
  }
}
