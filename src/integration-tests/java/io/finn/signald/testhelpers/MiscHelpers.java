package io.finn.signald.testhelpers;

import okhttp3.OkHttpClient;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import io.finn.signald.BuildConfig;

public class MiscHelpers {

  public static String getVerificationCode(String username) throws IOException {
    OkHttpClient client = new OkHttpClient();

    RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("number", username).build();

    Request request = new Request.Builder().url(BuildConfig.SIGNAL_URL + "/helper/verification-code").post(body).build();

    Response response = client.newCall(request).execute();

    return response.body().string().trim();
  }
}
