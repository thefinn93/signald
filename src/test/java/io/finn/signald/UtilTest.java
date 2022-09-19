package io.finn.signald;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;

public class UtilTest {
  @Test
  @DisplayName("redaction tests")
  void redact() {
    Assertions.assertEquals("[redacted 1]est", Util.redact("test"));
    Assertions.assertEquals("[redacted 3]", Util.redact("aaa"));
    Assertions.assertEquals("[redacted 1]", Util.redact("a"));
    Assertions.assertEquals("[redacted 0]", Util.redact(""));

    UUID u = UUID.fromString("a59eef85-fd26-4f4f-bf7b-d4948e28b230");
    Assertions.assertEquals("[redacted 33]230", Util.redact(u));
    Assertions.assertEquals("[redacted 33]230", Util.redact(ACI.from(u)));
    Assertions.assertEquals("[redacted 33]230", Util.redact(PNI.from(u)));
  }
}
