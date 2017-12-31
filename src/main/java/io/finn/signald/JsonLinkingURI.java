package io.finn.signald;

import java.net.URI;
import java.io.IOException;

import java.util.concurrent.TimeoutException;

class JsonLinkingURI {
  public URI uri;

  JsonLinkingURI(URI uri) {
    this.uri = uri;
  }

  JsonLinkingURI(Manager m) throws TimeoutException, IOException {
    this.uri = m.getDeviceLinkUri();
  }
}
