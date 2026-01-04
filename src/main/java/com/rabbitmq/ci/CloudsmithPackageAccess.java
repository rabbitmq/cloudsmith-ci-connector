/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

import static com.rabbitmq.ci.RetryUtils.retry;
import static com.rabbitmq.ci.Utils.encodeHttpParameter;
import static com.rabbitmq.ci.Utils.encodePath;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;

final class CloudsmithPackageAccess {

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();
  private final Input input;
  private final Input.Source source;
  private final Input.Params params;

  private final String baseUploadUrlTpl;
  private final String baseCreatePackageUrlTpl;
  private final String baseSearchUrlTpl;
  private final Log log;

  CloudsmithPackageAccess(
      Input input, String baseUpload, String baseCreate, String baseSearch, Log log) {
    this.input = input;
    this.source = input.source();
    this.params = input.params();
    this.baseUploadUrlTpl = baseUpload + "/{org}/{repo}/{file}";
    this.baseCreatePackageUrlTpl = baseCreate + "/v1/packages/{org}/{repo}/upload/{type}/";
    this.baseSearchUrlTpl = baseSearch + "/packages/{org}/{repo}/";
    this.log = log;
  }

  CloudsmithPackageAccess(Input input, Log log) {
    this(
        input,
        "https://upload.cloudsmith.io",
        "https://api-prd.cloudsmith.io",
        "https://api.cloudsmith.io",
        log);
  }

  static String sha256(byte[] content) throws NoSuchAlgorithmException {
    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
    byte[] digest = messageDigest.digest(content);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < digest.length; ++i) {
      sb.append(Integer.toHexString((digest[i] & 0xFF) | 0x100), 1, 3);
    }
    return sb.toString();
  }

  static String nextLink(String linkHeader) {
    String nextLink = null;
    for (String link : linkHeader.split(",")) {
      // e.g.
      // <https://api.cloudsmith.io/packages/rabbitmq/rabbitmq-erlang/?page=2&version%3A1%3A23.2.7-1>; rel="next"
      String[] urlRel = link.split(";");
      if ("rel=\"next\"".equals(urlRel[1].trim())) {
        String url = urlRel[0].trim();
        // removing the < and >
        nextLink = url.substring(1, url.length() - 1);
      }
    }
    return nextLink;
  }

  static String uploadJsonBody(Map<String, Object> parameters) {
    return Utils.GSON.toJson(parameters);
  }

  List<Package> find() throws InterruptedException {
    List<String> queryParameters = new ArrayList<>();
    Input.Source source = input.source();
    Input.Version version = input.version();

    if (input.source().name() != null) {
      queryParameters.add("filename:" + input.source().name());
    }

    if (input.params() != null && input.params().versionFilter() != null) {
      queryParameters.add("version:" + input.params().versionFilter());
    }

    String versionCriteria = null;
    if (version != null && version.version() != null) {
      versionCriteria = version.version();
    } else {
      if (input.params() != null) {
        versionCriteria = input.params().version();
      }
    }
    if (versionCriteria != null) {
      queryParameters.add("version:" + versionCriteria);
    }

    String distribution;
    if (version != null && version.distribution() != null) {
      distribution = version.distribution();
    } else {
      distribution = input.source().distribution();
    }
    if (distribution != null) {
      String[] nameCodename = distribution.split("/");
      if (nameCodename.length != 2) {
        log.logRed(
            "Distribution invalid: "
                + source.distribution()
                + ". "
                + "Format should be {distribution}/{codename}, e.g. ubuntu/focal.");
      }
      queryParameters.add("distribution:" + nameCodename[0]);
      queryParameters.add("distribution:" + nameCodename[1]);
    }

    String typeCriteria;
    if (version != null && version.type() != null) {
      typeCriteria = version.type();
    } else {
      typeCriteria = source.type();
    }
    if (typeCriteria != null && !"raw".equals(typeCriteria)) {
      queryParameters.add("filename:" + typeCriteria + "$");
    }

    String url =
        this.baseSearchUrlTpl
            .replace("{org}", encodePath(source.organization()))
            .replace("{repo}", encodePath(source.repository()));

    if (!queryParameters.isEmpty()) {
      String query = String.join(" AND ", queryParameters);
      log.newLine();
      log.log(log.yellow("Query: ") + query);
      log.newLine();
      query = encodeHttpParameter(query);
      url = url + "?query=" + query;
    }

    HttpRequest request = requestBuilder().uri(URI.create(url)).GET().build();

    Type type = new TypeToken<List<Package>>() {}.getType();

    List<Package> packages = new ArrayList<>();
    boolean hasMore = true;
    while (hasMore) {
      HttpRequest rq = request;
      HttpResponse<String> response =
          retry(() -> client.send(rq, HttpResponse.BodyHandlers.ofString()));
      packages.addAll(Utils.GSON.fromJson(response.body(), type));
      Optional<String> link = response.headers().firstValue("link");
      String nextLink;
      if (link.isPresent() && (nextLink = nextLink(link.get())) != null) {
        request = requestBuilder().uri(URI.create(nextLink)).GET().build();
      } else {
        hasMore = false;
      }
    }
    return packages;
  }

  String upload(String file, Map<String, Object> creationParameters, String type)
      throws IOException, NoSuchAlgorithmException, InterruptedException {
    creationParameters = new LinkedHashMap<>(creationParameters);
    Path path = Paths.get(file);
    byte[] content = Files.readAllBytes(path);
    String uploadUrl =
        this.baseUploadUrlTpl
            .replace("{org}", encodePath(input.source().organization()))
            .replace("{repo}", encodePath(input.source().repository()))
            .replace("{file}", encodePath(path.getFileName().toString()));

    HttpRequest request =
        requestBuilder()
            .setHeader("Content-Sha256", sha256(content))
            .uri(URI.create(uploadUrl))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    String responseBody = response.body();

    String identifier =
        Utils.GSON.fromJson(responseBody, JsonObject.class).get("identifier").getAsString();
    String createUrl =
        this.baseCreatePackageUrlTpl
            .replace("{org}", encodePath(input.source().organization()))
            .replace("{repo}", encodePath(input.source().repository()))
            .replace("{type}", encodePath(type));

    creationParameters.put("package_file", identifier);
    if (source.distribution() != null) {
      creationParameters.put("distribution", source.distribution());
    }

    if (params != null && params.tags() != null) {
      creationParameters.put("tags", params.tags());
    }

    if (params.republish()) {
      creationParameters.put("republish", true);
    }

    String createJson = uploadJsonBody(creationParameters);

    request =
        requestBuilder()
            .setHeader("Content-Type", "application/json")
            .uri(URI.create(createUrl))
            .POST(HttpRequest.BodyPublishers.ofString(createJson))
            .build();

    response = client.send(request, HttpResponse.BodyHandlers.ofString());
    responseBody = response.body();

    String selfUrl;
    if (response.statusCode() == 400
        && "raw".equals(type)
        && !responseBody.contains("\"self_url\"")
        && !params.republish()) {
      // duplicated package, it's detected immediately (no sync process)
      selfUrl = null;
    } else {
      try {
        selfUrl = Utils.GSON.fromJson(responseBody, JsonObject.class).get("self_url").getAsString();
      } catch (RuntimeException e) {
        log.logIndent(
            log.red("Error: response status " + response.statusCode() + ", body " + responseBody));
        log.logIndent(log.red("Creation parameters: " + createJson));
        throw e;
      }
    }
    return selfUrl;
  }

  void delete(Package p) throws IOException, InterruptedException {
    HttpRequest request = requestBuilder().uri(URI.create(p.selfUrl())).DELETE().build();
    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
    if (response.statusCode() != 204) {
      log.logRed(
          "Error while trying to delete "
              + p.selfUrl()
              + ". HTTP response code is "
              + response.statusCode());
    }
  }

  byte[] download(String packageUrl) throws IOException, InterruptedException {
    HttpRequest request = requestBuilder().uri(URI.create(packageUrl)).GET().build();
    HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    return response.body();
  }

  Package findPackage(String packageUrl) throws IOException, InterruptedException {
    HttpRequest request = requestBuilder().uri(URI.create(packageUrl)).GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    String responseBody = response.body();
    return Utils.GSON.fromJson(responseBody, Package.class);
  }

  private HttpRequest.Builder requestBuilder() {
    return auth(HttpRequest.newBuilder());
  }

  private HttpRequest.Builder auth(HttpRequest.Builder builder) {
    return builder.setHeader("X-Api-Key", input.source().apiKey());
  }
}
