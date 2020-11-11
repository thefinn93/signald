/*
 * Copyright (C) 2020 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import okhttp3.*;
import org.signal.storageservice.protos.groups.*;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.*;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.groupsv2.CredentialResponse;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.*;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;
import org.whispersystems.signalservice.internal.push.*;
import org.whispersystems.signalservice.internal.push.exceptions.*;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture;
import org.whispersystems.util.Base64;
import org.whispersystems.util.Base64UrlSafe;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ExpandedPushServiceSocket {

  private static final String REGISTER_CAPABILITIES = "/v1/devices/capabilities";

  // variables I copied in from PushServiceSocket
  private static final String GROUPSV2_CREDENTIAL = "/v1/certificate/group/%d/%d";
  private static final String GROUPSV2_GROUP = "/v1/groups/";
  private static final String GROUPSV2_GROUP_PASSWORD = "/v1/groups/?inviteLinkPassword=%s";
  private static final String GROUPSV2_GROUP_CHANGES = "/v1/groups/logs/%s";
  private static final String GROUPSV2_AVATAR_REQUEST = "/v1/groups/avatar/form";
  private static final String GROUPSV2_GROUP_JOIN = "/v1/groups/join/%s";

  private static final String TAG = ExpandedPushServiceSocket.class.getSimpleName();
  private static final Map<String, String> NO_HEADERS = Collections.emptyMap();
  private static final ResponseCodeHandler NO_HANDLER = new EmptyResponseCodeHandler();

  private long soTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
  private final Set<Call> connections = new HashSet<>();

  private final ServiceConnectionHolder[] serviceClients;
  private final Map<Integer, ConnectionHolder[]> cdnClientsMap;
  private final ConnectionHolder[] contactDiscoveryClients;
  private final ConnectionHolder[] keyBackupServiceClients;
  private final ConnectionHolder[] storageClients;

  private final CredentialsProvider credentialsProvider;
  private final String signalAgent;
  private final SecureRandom random;
  private final ClientZkProfileOperations clientZkProfileOperations;

  public ExpandedPushServiceSocket(SignalServiceConfiguration configuration, CredentialsProvider credentialsProvider, String signalAgent,
                                   ClientZkProfileOperations clientZkProfileOperations) {
    this.credentialsProvider = credentialsProvider;
    this.signalAgent = signalAgent;
    this.serviceClients = createServiceConnectionHolders(configuration.getSignalServiceUrls(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.cdnClientsMap = createCdnClientsMap(configuration.getSignalCdnUrlMap(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.contactDiscoveryClients = createConnectionHolders(configuration.getSignalContactDiscoveryUrls(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.keyBackupServiceClients = createConnectionHolders(configuration.getSignalKeyBackupServiceUrls(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.storageClients = createConnectionHolders(configuration.getSignalStorageUrls(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.random = new SecureRandom();
    this.clientZkProfileOperations = clientZkProfileOperations;
  }

  public void setCapabilities(Map<String, Boolean> c) throws NonSuccessfulResponseCodeException, PushNetworkException {
    String requestBody = JsonUtil.toJson(c);
    makeServiceRequest(REGISTER_CAPABILITIES, "PUT", requestBody);
  }

  /*
   * The rest of this class is helpers mostly copied from libsignal's
   * PushServiceSocket
   */
  private String makeServiceRequest(String urlFragment, String method, String jsonBody) throws NonSuccessfulResponseCodeException, PushNetworkException {
    return makeServiceRequest(urlFragment, method, jsonBody, NO_HEADERS, NO_HANDLER, Optional.<UnidentifiedAccess>absent());
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers)
      throws NonSuccessfulResponseCodeException, PushNetworkException {
    return makeServiceRequest(urlFragment, method, jsonBody, headers, NO_HANDLER, Optional.<UnidentifiedAccess>absent());
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers, ResponseCodeHandler responseCodeHandler)
      throws NonSuccessfulResponseCodeException, PushNetworkException {
    return makeServiceRequest(urlFragment, method, jsonBody, headers, responseCodeHandler, Optional.<UnidentifiedAccess>absent());
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers, Optional<UnidentifiedAccess> unidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException {
    return makeServiceRequest(urlFragment, method, jsonBody, headers, NO_HANDLER, unidentifiedAccessKey);
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers, ResponseCodeHandler responseCodeHandler,
                                    Optional<UnidentifiedAccess> unidentifiedAccessKey) throws NonSuccessfulResponseCodeException, PushNetworkException {
    ResponseBody responseBody = makeServiceBodyRequest(urlFragment, method, jsonRequestBody(jsonBody), headers, responseCodeHandler, unidentifiedAccessKey);
    try {
      return responseBody.string();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  private static RequestBody jsonRequestBody(String jsonBody) { return jsonBody != null ? RequestBody.create(MediaType.parse("application/json"), jsonBody) : null; }

  private static RequestBody protobufRequestBody(MessageLite protobufBody) {
    return protobufBody != null ? RequestBody.create(MediaType.parse("application/x-protobuf"), protobufBody.toByteArray()) : null;
  }

  private ListenableFuture<String> submitServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers,
                                                        Optional<UnidentifiedAccess> unidentifiedAccessKey) {
    OkHttpClient okHttpClient = buildOkHttpClient(unidentifiedAccessKey.isPresent());
    Call call = okHttpClient.newCall(buildServiceRequest(urlFragment, method, jsonRequestBody(jsonBody), headers, unidentifiedAccessKey));

    synchronized (connections) { connections.add(call); }

    SettableFuture<String> bodyFuture = new SettableFuture<>();

    call.enqueue(new Callback() {
      @Override
      public void onResponse(Call call, Response response) {
        try (ResponseBody body = validateServiceResponse(response).body()) {
          try {
            bodyFuture.set(readBodyString(body));
          } catch (IOException e) {
            throw new PushNetworkException(e);
          }
        } catch (IOException e) {
          bodyFuture.setException(e);
        }
      }

      @Override
      public void onFailure(Call call, IOException e) {
        bodyFuture.setException(e);
      }
    });

    return bodyFuture;
  }

  private ResponseBody makeServiceBodyRequest(String urlFragment, String method, RequestBody body, Map<String, String> headers, ResponseCodeHandler responseCodeHandler,
                                              Optional<UnidentifiedAccess> unidentifiedAccessKey) throws NonSuccessfulResponseCodeException, PushNetworkException {
    return makeServiceRequest(urlFragment, method, body, headers, responseCodeHandler, unidentifiedAccessKey).body();
  }

  private Response makeServiceRequest(String urlFragment, String method, RequestBody body, Map<String, String> headers, ResponseCodeHandler responseCodeHandler,
                                      Optional<UnidentifiedAccess> unidentifiedAccessKey) throws NonSuccessfulResponseCodeException, PushNetworkException {
    Response response = getServiceConnection(urlFragment, method, body, headers, unidentifiedAccessKey);

    responseCodeHandler.handle(response.code());

    return validateServiceResponse(response);
  }

  private Response validateServiceResponse(Response response) throws NonSuccessfulResponseCodeException, PushNetworkException {
    int responseCode = response.code();
    String responseMessage = response.message();
    ResponseBody responseBody = response.body();

    switch (responseCode) {
    case 413:
      throw new RateLimitException("Rate limit exceeded: " + responseCode);
    case 401:
    case 403:
      throw new AuthorizationFailedException("Authorization failed!");
    case 404:
      throw new NotFoundException("Not found");
    case 409:
      MismatchedDevices mismatchedDevices;

      try {
        mismatchedDevices = JsonUtil.fromJson(readBodyString(responseBody), MismatchedDevices.class);
      } catch (JsonProcessingException e) {
        Log.w(TAG, e);
        throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
      } catch (IOException e) {
        throw new PushNetworkException(e);
      }

      throw new MismatchedDevicesException(mismatchedDevices);
    case 410:
      StaleDevices staleDevices;

      try {
        staleDevices = JsonUtil.fromJson(readBodyString(responseBody), StaleDevices.class);
      } catch (JsonProcessingException e) {
        throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
      } catch (IOException e) {
        throw new PushNetworkException(e);
      }

      throw new StaleDevicesException(staleDevices);
    case 411:
      DeviceLimit deviceLimit;

      try {
        deviceLimit = JsonUtil.fromJson(readBodyString(responseBody), DeviceLimit.class);
      } catch (JsonProcessingException e) {
        throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
      } catch (IOException e) {
        throw new PushNetworkException(e);
      }

      throw new DeviceLimitExceededException(deviceLimit);
    case 417:
      throw new ExpectationFailedException();
    case 423:
      RegistrationLockFailure accountLockFailure;

      try {
        accountLockFailure = JsonUtil.fromJson(readBodyString(responseBody), RegistrationLockFailure.class);
      } catch (JsonProcessingException e) {
        Log.w(TAG, e);
        throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
      } catch (IOException e) {
        throw new PushNetworkException(e);
      }

      AuthCredentials credentials = accountLockFailure.backupCredentials;
      String basicStorageCredentials = credentials != null ? credentials.asBasic() : null;

      throw new LockedException(accountLockFailure.length, accountLockFailure.timeRemaining, basicStorageCredentials);
    case 499:
      throw new DeprecatedVersionException();
    }

    if (responseCode != 200 && responseCode != 204) {
      throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
    }

    return response;
  }

  private Response getServiceConnection(String urlFragment, String method, RequestBody body, Map<String, String> headers, Optional<UnidentifiedAccess> unidentifiedAccess)
      throws PushNetworkException {
    try {
      OkHttpClient okHttpClient = buildOkHttpClient(unidentifiedAccess.isPresent());
      Call call = okHttpClient.newCall(buildServiceRequest(urlFragment, method, body, headers, unidentifiedAccess));

      synchronized (connections) { connections.add(call); }

      try {
        return call.execute();
      } finally {
        synchronized (connections) { connections.remove(call); }
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  private OkHttpClient buildOkHttpClient(boolean unidentified) {
    ServiceConnectionHolder connectionHolder = (ServiceConnectionHolder)getRandom(serviceClients, random);
    OkHttpClient baseClient = unidentified ? connectionHolder.getUnidentifiedClient() : connectionHolder.getClient();

    return baseClient.newBuilder().connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS).readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS).build();
  }

  private Request buildServiceRequest(String urlFragment, String method, RequestBody body, Map<String, String> headers, Optional<UnidentifiedAccess> unidentifiedAccess) {
    ServiceConnectionHolder connectionHolder = (ServiceConnectionHolder)getRandom(serviceClients, random);

    //      Log.d(TAG, "Push service URL: " + connectionHolder.getUrl());
    //      Log.d(TAG, "Opening URL: " + String.format("%s%s",
    //      connectionHolder.getUrl(), urlFragment));
    Log.d(TAG, "Opening URL: <REDACTED>");

    Request.Builder request = new Request.Builder();
    request.url(String.format("%s%s", connectionHolder.getUrl(), urlFragment));
    request.method(method, body);

    for (Map.Entry<String, String> header : headers.entrySet()) {
      request.addHeader(header.getKey(), header.getValue());
    }

    if (!headers.containsKey("Authorization")) {
      if (unidentifiedAccess.isPresent()) {
        request.addHeader("Unidentified-Access-Key", Base64.encodeBytes(unidentifiedAccess.get().getUnidentifiedAccessKey()));
      } else if (credentialsProvider.getPassword() != null) {
        request.addHeader("Authorization", getAuthorizationHeader(credentialsProvider));
      }
    }

    if (signalAgent != null) {
      request.addHeader("X-Signal-Agent", signalAgent);
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    return request.build();
  }

  private ConnectionHolder[] clientsFor(ClientSet clientSet) {
    switch (clientSet) {
    case ContactDiscovery:
      return contactDiscoveryClients;
    case KeyBackup:
      return keyBackupServiceClients;
    default:
      throw new AssertionError("Unknown attestation purpose");
    }
  }

  Response makeRequest(ClientSet clientSet, String authorization, List<String> cookies, String path, String method, String body)
      throws PushNetworkException, NonSuccessfulResponseCodeException {
    ConnectionHolder connectionHolder = getRandom(clientsFor(clientSet), random);

    return makeRequest(connectionHolder, authorization, cookies, path, method, body);
  }

  private Response makeRequest(ConnectionHolder connectionHolder, String authorization, List<String> cookies, String path, String method, String body)
      throws PushNetworkException, NonSuccessfulResponseCodeException {
    OkHttpClient okHttpClient =
        connectionHolder.getClient().newBuilder().connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS).readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS).build();

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + path);

    if (body != null) {
      request.method(method, RequestBody.create(MediaType.parse("application/json"), body));
    } else {
      request.method(method, null);
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }

    if (cookies != null && !cookies.isEmpty()) {
      request.addHeader("Cookie", Util.join(cookies, "; "));
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) { connections.add(call); }

    Response response;

    try {
      response = call.execute();

      if (response.isSuccessful()) {
        return response;
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) { connections.remove(call); }
    }

    switch (response.code()) {
    case 401:
    case 403:
      throw new AuthorizationFailedException("Authorization failed!");
    case 409:
      throw new RemoteAttestationResponseExpiredException("Remote attestation response expired");
    case 429:
      throw new RateLimitException("Rate limit exceeded: " + response.code());
    }

    throw new NonSuccessfulResponseCodeException("Response: " + response);
  }

  private ResponseBody makeStorageRequest(String authorization, String path, String method, RequestBody body) throws PushNetworkException, NonSuccessfulResponseCodeException {
    return makeStorageRequest(authorization, path, method, body, NO_HANDLER);
  }

  private ResponseBody makeStorageRequest(String authorization, String path, String method, RequestBody body, ResponseCodeHandler responseCodeHandler)
      throws PushNetworkException, NonSuccessfulResponseCodeException {
    ConnectionHolder connectionHolder = getRandom(storageClients, random);
    OkHttpClient okHttpClient =
        connectionHolder.getClient().newBuilder().connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS).readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS).build();

    Log.d(TAG, "Opening URL: <REDACTED>");

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + path);
    request.method(method, body);

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) { connections.add(call); }

    Response response;

    try {
      response = call.execute();

      if (response.isSuccessful() && response.code() != 204) {
        return response.body();
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) { connections.remove(call); }
    }

    responseCodeHandler.handle(response.code());

    switch (response.code()) {
    case 204:
      throw new NoContentException("No content!");
    case 401:
    case 403:
      throw new AuthorizationFailedException("Authorization failed!");
    case 404:
      throw new NotFoundException("Not found");
    case 409:
      if (response.body() != null) {
        throw new ContactManifestMismatchException(readBodyBytes(response.body()));
      } else {
        throw new ConflictException();
      }
    case 429:
      throw new RateLimitException("Rate limit exceeded: " + response.code());
    case 499:
      throw new DeprecatedVersionException();
    }

    throw new NonSuccessfulResponseCodeException("Response: " + response);
  }

  private ServiceConnectionHolder[] createServiceConnectionHolders(SignalUrl[] urls, List<Interceptor> interceptors, Optional<Dns> dns) {
    List<ServiceConnectionHolder> serviceConnectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      serviceConnectionHolders.add(
          new ServiceConnectionHolder(createConnectionClient(url, interceptors, dns), createConnectionClient(url, interceptors, dns), url.getUrl(), url.getHostHeader()));
    }

    return serviceConnectionHolders.toArray(new ServiceConnectionHolder[0]);
  }

  private static Map<Integer, ConnectionHolder[]> createCdnClientsMap(final Map<Integer, SignalCdnUrl[]> signalCdnUrlMap, final List<Interceptor> interceptors,
                                                                      final Optional<Dns> dns) {
    validateConfiguration(signalCdnUrlMap);
    final Map<Integer, ConnectionHolder[]> result = new HashMap<>();
    for (Map.Entry<Integer, SignalCdnUrl[]> entry : signalCdnUrlMap.entrySet()) {
      result.put(entry.getKey(), createConnectionHolders(entry.getValue(), interceptors, dns));
    }
    return Collections.unmodifiableMap(result);
  }

  private static void validateConfiguration(Map<Integer, SignalCdnUrl[]> signalCdnUrlMap) {
    if (!signalCdnUrlMap.containsKey(0) || !signalCdnUrlMap.containsKey(2)) {
      throw new AssertionError("Configuration used to create PushServiceSocket must support CDN 0 and CDN 2");
    }
  }

  private static ConnectionHolder[] createConnectionHolders(SignalUrl[] urls, List<Interceptor> interceptors, Optional<Dns> dns) {
    List<ConnectionHolder> connectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      connectionHolders.add(new ConnectionHolder(createConnectionClient(url, interceptors, dns), url.getUrl(), url.getHostHeader()));
    }

    return connectionHolders.toArray(new ConnectionHolder[0]);
  }

  private static OkHttpClient createConnectionClient(SignalUrl url, List<Interceptor> interceptors, Optional<Dns> dns) {
    try {
      TrustManager[] trustManagers = BlacklistingTrustManager.createFor(url.getTrustStore());

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      OkHttpClient.Builder builder = new OkHttpClient.Builder()
                                         .sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
                                         .connectionSpecs(url.getConnectionSpecs().or(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
                                         .dns(dns.or(Dns.SYSTEM));

      builder.sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
          .connectionSpecs(url.getConnectionSpecs().or(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
          .build();

      for (Interceptor interceptor : interceptors) {
        builder.addInterceptor(interceptor);
      }

      return builder.build();
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  private String getAuthorizationHeader(CredentialsProvider credentialsProvider) {
    try {
      String identifier = credentialsProvider.getUuid() != null ? credentialsProvider.getUuid().toString() : credentialsProvider.getE164();
      if (credentialsProvider.getDeviceId() == SignalServiceAddress.DEFAULT_DEVICE_ID) {
        return "Basic " + Base64.encodeBytes((identifier + ":" + credentialsProvider.getPassword()).getBytes("UTF-8"));
      } else {
        return "Basic " + Base64.encodeBytes((identifier + "." + credentialsProvider.getDeviceId() + ":" + credentialsProvider.getPassword()).getBytes("UTF-8"));
      }
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private ConnectionHolder getRandom(ConnectionHolder[] connections, SecureRandom random) { return connections[random.nextInt(connections.length)]; }

  public ProfileKeyCredential parseResponse(UUID uuid, ProfileKey profileKey, ProfileKeyCredentialResponse profileKeyCredentialResponse) throws VerificationFailedException {
    ProfileKeyCredentialRequestContext profileKeyCredentialRequestContext = clientZkProfileOperations.createProfileKeyCredentialRequestContext(random, uuid, profileKey);

    return clientZkProfileOperations.receiveProfileKeyCredential(profileKeyCredentialRequestContext, profileKeyCredentialResponse);
  }

  /**
   * Converts {@link IOException} on body byte reading to {@link
   * PushNetworkException}.
   */
  private static byte[] readBodyBytes(ResponseBody response) throws PushNetworkException {
    try {
      return response.bytes();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  private static String readBodyString(ResponseBody body) throws IOException {
    if (body != null) {
      return body.string();
    } else {
      throw new IOException("No body!");
    }
  }

  private static class GcmRegistrationId {

    @JsonProperty private String gcmRegistrationId;

    @JsonProperty private boolean webSocketChannel;

    public GcmRegistrationId() {}

    public GcmRegistrationId(String gcmRegistrationId, boolean webSocketChannel) {
      this.gcmRegistrationId = gcmRegistrationId;
      this.webSocketChannel = webSocketChannel;
    }
  }

  private static class RegistrationLock {
    @JsonProperty private String pin;

    public RegistrationLock() {}

    public RegistrationLock(String pin) { this.pin = pin; }
  }

  private static class RegistrationLockV2 {
    @JsonProperty private String registrationLock;

    public RegistrationLockV2() {}

    public RegistrationLockV2(String registrationLock) { this.registrationLock = registrationLock; }
  }

  private static class RegistrationLockFailure {
    @JsonProperty private int length;

    @JsonProperty private long timeRemaining;

    @JsonProperty private AuthCredentials backupCredentials;
  }

  private static class ConnectionHolder {

    private final OkHttpClient client;
    private final String url;
    private final Optional<String> hostHeader;

    private ConnectionHolder(OkHttpClient client, String url, Optional<String> hostHeader) {
      this.client = client;
      this.url = url;
      this.hostHeader = hostHeader;
    }

    OkHttpClient getClient() { return client; }

    public String getUrl() { return url; }

    Optional<String> getHostHeader() { return hostHeader; }
  }

  private static class ServiceConnectionHolder extends ConnectionHolder {

    private final OkHttpClient unidentifiedClient;

    private ServiceConnectionHolder(OkHttpClient identifiedClient, OkHttpClient unidentifiedClient, String url, Optional<String> hostHeader) {
      super(identifiedClient, url, hostHeader);
      this.unidentifiedClient = unidentifiedClient;
    }

    OkHttpClient getUnidentifiedClient() { return unidentifiedClient; }
  }

  private interface ResponseCodeHandler { void handle(int responseCode) throws NonSuccessfulResponseCodeException, PushNetworkException; }

  private static class EmptyResponseCodeHandler implements ResponseCodeHandler {
    @Override
    public void handle(int responseCode) {}
  }

  public enum ClientSet { ContactDiscovery, KeyBackup }

  public CredentialResponse retrieveGroupsV2Credentials(int today) throws IOException {
    int todayPlus7 = today + 7;
    String response = makeServiceRequest(String.format(Locale.US, GROUPSV2_CREDENTIAL, today, todayPlus7), "GET", null, NO_HEADERS, Optional.absent());

    return JsonUtil.fromJson(response, CredentialResponse.class);
  }

  private static final ResponseCodeHandler GROUPS_V2_PUT_RESPONSE_HANDLER = NO_HANDLER;
  private static final ResponseCodeHandler GROUPS_V2_GET_LOGS_HANDLER = NO_HANDLER;
  private static final ResponseCodeHandler GROUPS_V2_GET_CURRENT_HANDLER = responseCode -> {
    if (responseCode == 403)
      throw new NotInGroupException();
  };
  private static final ResponseCodeHandler GROUPS_V2_PATCH_RESPONSE_HANDLER = responseCode -> {
    if (responseCode == 400)
      throw new GroupPatchNotAcceptedException();
  };
  private static final ResponseCodeHandler GROUPS_V2_GET_JOIN_INFO_HANDLER = responseCode -> {
    if (responseCode == 403)
      throw new ForbiddenException();
  };

  public void putNewGroupsV2Group(Group group, GroupsV2AuthorizationString authorization) throws NonSuccessfulResponseCodeException, PushNetworkException {
    makeStorageRequest(authorization.toString(), GROUPSV2_GROUP, "PUT", protobufRequestBody(group), GROUPS_V2_PUT_RESPONSE_HANDLER);
  }

  public Group getGroupsV2Group(GroupsV2AuthorizationString authorization) throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException {
    ResponseBody response = makeStorageRequest(authorization.toString(), GROUPSV2_GROUP, "GET", null, GROUPS_V2_GET_CURRENT_HANDLER);

    return Group.parseFrom(readBodyBytes(response));
  }

  public AvatarUploadAttributes getGroupsV2AvatarUploadForm(String authorization) throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException {
    ResponseBody response = makeStorageRequest(authorization, GROUPSV2_AVATAR_REQUEST, "GET", null, NO_HANDLER);

    return AvatarUploadAttributes.parseFrom(readBodyBytes(response));
  }

  public GroupChange patchGroupsV2Group(GroupChange.Actions groupChange, String authorization, Optional<byte[]> groupLinkPassword)
      throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException {
    String path;

    if (groupLinkPassword.isPresent()) {
      path = String.format(GROUPSV2_GROUP_PASSWORD, Base64UrlSafe.encodeBytesWithoutPadding(groupLinkPassword.get()));
    } else {
      path = GROUPSV2_GROUP;
    }

    ResponseBody response = makeStorageRequest(authorization, path, "PATCH", protobufRequestBody(groupChange), GROUPS_V2_PATCH_RESPONSE_HANDLER);

    return GroupChange.parseFrom(readBodyBytes(response));
  }

  public GroupChanges getGroupsV2GroupHistory(int fromVersion, GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException {
    ResponseBody response = makeStorageRequest(authorization.toString(), String.format(Locale.US, GROUPSV2_GROUP_CHANGES, fromVersion), "GET", null, GROUPS_V2_GET_LOGS_HANDLER);

    return GroupChanges.parseFrom(readBodyBytes(response));
  }

  public GroupJoinInfo getGroupJoinInfo(Optional<byte[]> groupLinkPassword, GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException {
    String passwordParam = groupLinkPassword.transform(Base64UrlSafe::encodeBytesWithoutPadding).or("");
    ResponseBody response = makeStorageRequest(authorization.toString(), String.format(GROUPSV2_GROUP_JOIN, passwordParam), "GET", null, GROUPS_V2_GET_JOIN_INFO_HANDLER);

    return GroupJoinInfo.parseFrom(readBodyBytes(response));
  }

  private final class ResumeInfo {
    private final String contentRange;
    private final long contentStart;

    private ResumeInfo(String contentRange, long offset) {
      this.contentRange = contentRange;
      this.contentStart = offset;
    }
  }

  public final class LockedException extends NonSuccessfulResponseCodeException {

    private final int length;
    private final long timeRemaining;
    private final String basicStorageCredentials;

    LockedException(int length, long timeRemaining, String basicStorageCredentials) {
      this.length = length;
      this.timeRemaining = timeRemaining;
      this.basicStorageCredentials = basicStorageCredentials;
    }

    public int getLength() { return length; }

    public long getTimeRemaining() { return timeRemaining; }

    public String getBasicStorageCredentials() { return basicStorageCredentials; }
  }
}
