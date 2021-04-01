package com.rabbitmq.concourse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CloudsmithResource {

  static final Gson GSON = new GsonBuilder().create();

  static final Duration PACKAGE_SYNCHRONIZATION_TIMEOUT = Duration.ofMinutes(5);

  static final String DELETED_VERSION = "<DELETED>";
  static final String JSON_DELETED_VERSION =
      "{\n" + "  \"version\": { \"version\": \"<DELETED>\" },\n" + "  \"metadata\": [ ]\n" + "}";

  public static void main(String[] args) throws IOException, InterruptedException {
    Scanner scanner = new Scanner(System.in);
    StringBuilder builder = new StringBuilder();
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      builder.append(line);
    }

    String command = args[0];

    if ("check".equals(command)) {
      check(null);
    } else if ("in".equals(command)) {
      Input input = GSON.fromJson(builder.toString(), Input.class);
      String outputDirectory = args[1];
      in(input, outputDirectory);
    } else if ("out".equals(command)) {
      Input input = GSON.fromJson(builder.toString(), Input.class);
      String inputDirectory = args[1];
      out(input, inputDirectory);
    } else {
      throw new IllegalArgumentException("command not supported: " + command);
    }
  }

  static void check(Input in) {
    throw new IllegalArgumentException("command not supported: check");
  }

  static void in(Input input, String outputDirectory) throws IOException, InterruptedException {
    if (DELETED_VERSION.equals(input.version().version())) {
      log("Getting special version <DELETED> is a no-op; returning it as is");
      out(JSON_DELETED_VERSION);
    } else {
      CloudsmithPackageAccess access = new CloudsmithPackageAccess(input);
      List<Package> packages = access.find();

      logGreen("Downloading files...");
      for (Package p : packages) {
        try {
          byte[] content = access.download(p.cdnUrl());
          Files.write(Path.of(outputDirectory, p.filename()), content);
          String checksum = CloudsmithPackageAccess.sha256(content);
          String message;
          if (p.filename().toLowerCase().endsWith(".asc")) {
            message = "OK? (checksum not verified for ASC files)";
          } else {
            message = checksum.equals(p.sha256()) ? "OK" : "OK? (checksum verification failed)";
          }
          logIndent(green(p.filename() + ": ") + message);
        } catch (Exception e) {
          logIndent(red(p.filename() + ": " + e.getMessage()));
        }
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("version", input.version());
      out.put("metadata", Collections.emptyList());

      out(GSON.toJson(out));
    }
  }

  static void out(Input input, String inputDirectory) throws IOException, InterruptedException {
    if (input.params().delete()) {
      CloudsmithPackageAccess access = new CloudsmithPackageAccess(input);
      List<Package> packages = access.find();

      List<String> versions =
          packages.stream().map(Package::version).distinct().collect(Collectors.toList());

      List<String> versionsToDelete = filterForDeletion(versions, input.params().keepLastN());
      log(green("Version(s) detected: ") + String.join(", ", versions));
      log(green("Version(s) to delete: ") + String.join(", ", versionsToDelete));

      newLine();

      AtomicInteger deletedCount = new AtomicInteger();
      logGreen("Packages:");
      packages.forEach(
          p -> {
            boolean shouldBeDeleted = versionsToDelete.contains(p.version());
            if (shouldBeDeleted) {
              deletedCount.incrementAndGet();
            }
            if (shouldBeDeleted && input.params().doDelete()) {
              try {
                access.delete(p);
                logIndent(red("deleting " + p.filename()));
              } catch (Exception e) {
                logRed("Error while trying to delete " + p.selfUrl() + ": " + e.getMessage());
              }
            } else {
              logIndent(
                  shouldBeDeleted
                      ? (red("deleting " + p.filename()) + yellow(" (skipped)"))
                      : "keeping " + p.filename());
            }
          });

      newLine();
      logGreen("Deleted " + deletedCount.get() + " file(s)");

      out(JSON_DELETED_VERSION);
    } else {
      inputDirectory =
          (input.params().localPath() == null || input.params().localPath().isBlank())
              ? inputDirectory
              : Paths.get(inputDirectory, input.params().localPath()).toFile().getAbsolutePath();

      String[] globs =
          (input.params().globs() == null || input.params.globs.isBlank())
              ? new String[] {"*"}
              : input.params().globs.split(",");

      Set<String> selectedFiles = selectFilesForUpload(inputDirectory, globs);

      logGreen("Local path:");
      logIndent(input.params().localPath());
      log("");
      logGreen("Files:");
      selectedFiles.forEach(file -> logIndent(Paths.get(file).getFileName().toString()));

      newLine();

      Map<String, Object> creationParameters = new LinkedHashMap<>();
      Collection<String> filenames = filenames(selectedFiles);
      if (input.params() != null && input.params().version() != null) {
        String version = extractVersion(input.params().version(), filenames);
        if (version != null && !version.isBlank()) {
          creationParameters.put("version", version);
          log(green("Extracted version: ") + version);
          newLine();
        }
      }

      String packagesType = determinePackagesType(filenames);

      CloudsmithPackageAccess access = new CloudsmithPackageAccess(input);
      List<String> uploadFilesUrls = new ArrayList<>(selectedFiles.size());
      for (String selectedFile : selectedFiles) {
        Path path = Paths.get(selectedFile);
        log(green("Upload file: ") + path.getFileName());
        try {
          String selfUrl = access.upload(selectedFile, creationParameters, packagesType);
          logIndent(selfUrl);
          uploadFilesUrls.add(selfUrl);
        } catch (Exception e) {
          logIndent(red("Error: " + e.getMessage()));
        }
      }

      log("");

      String version = null;

      logGreen("Checking synchronization of packages...");
      for (String packageUrl : uploadFilesUrls) {
        Package p = null;
        try {
          p = access.findPackage(packageUrl);
          long waitTime = Duration.ofSeconds(10).toMillis();
          int waitedTime = 0;
          long timeoutInMs = PACKAGE_SYNCHRONIZATION_TIMEOUT.toMillis();
          printIndent(green(p.filename() + ": "));
          while (waitedTime <= timeoutInMs) {
            if (p.isSyncCompleted()) {
              log("OK");
              if (version == null) {
                version = p.version();
              }
              break;
            }
            print(".");
            Thread.sleep(waitTime);
            waitedTime += waitTime;
            p = access.findPackage(packageUrl);
          }
        } catch (Exception e) {
          logIndent(red(p == null ? "Error" : p.filename() + ": ") + e.getMessage());
        }
      }

      if (version == null) {
        logRed("No version found");
        System.exit(1);
      } else {
        Map<String, String> outVersion = new LinkedHashMap<>();
        outVersion.put("version", version);
        if (input.source().distribution() != null) {
          outVersion.put("distribution", input.source.distribution());
        }

        outVersion.put("type", packagesType);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", outVersion);
        out.put("metadata", Collections.emptyList());

        out(GSON.toJson(out));
      }
    }
  }

  static Collection<String> filenames(Collection<String> files) {
    return files.stream()
        .map(f -> Paths.get(f).getFileName().toString())
        .collect(Collectors.toSet());
  }

  static String determinePackagesType(Collection<String> filenames) {
    List<String> extensions =
        filenames.stream()
            .map(f -> fileExtension(f.toLowerCase()))
            .distinct()
            .collect(Collectors.toList());
    String type;
    if (extensions.isEmpty() || extensions.size() > 2) {
      type = "raw";
    } else if ("deb".equals(extensions.get(0))) {
      type = "deb";
    } else if ("rpm".equals(extensions.get(0))) {
      type = "rpm";
    } else {
      type = "raw";
    }
    return type;
  }

  static String fileExtension(String filename) {
    return filename.substring(filename.lastIndexOf(".") + 1);
  }

  static List<String> filterForDeletion(List<String> versions, int keepLastN) {
    if (versions.isEmpty()) {
      return Collections.emptyList();
    } else if (keepLastN <= 0) {
      // do not want to keep any, return all
      return versions;
    } else if (keepLastN >= versions.size()) {
      // we want to keep more than we have, so nothing to delete
      return Collections.emptyList();
    } else {
      class VersionWrapper {

        private final String version;
        private final ComparableVersion comparableVersion;

        VersionWrapper(String version) {
          this.version = version;
          this.comparableVersion =
              new ComparableVersion(version.startsWith("1:") ? version.substring(2) : version);
        }
      }
      return versions.stream()
          .map(VersionWrapper::new)
          .sorted(Comparator.comparing(o -> o.comparableVersion))
          .limit(versions.size() - keepLastN)
          .map(w -> w.version)
          .collect(Collectors.toList());
    }
  }

  static String extractVersion(String versionPattern, Collection<String> filenames) {
    Pattern pattern = Pattern.compile(versionPattern);
    return filenames.stream()
        .reduce(
            null,
            (acc, filename) -> {
              String version;
              Matcher matcher = pattern.matcher(filename);
              if (matcher.matches() && matcher.groupCount() == 1) {
                version = matcher.group(1);
              } else {
                version = acc;
              }
              return version;
            });
  }

  static Set<String> selectFilesForUpload(String inputDirectory, String[] globs)
      throws IOException {
    Set<String> selectedFiles = new TreeSet<>();
    for (String glob : globs) {
      glob = glob.trim();
      PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
      Set<String> files =
          Files.list(Paths.get(inputDirectory))
              .filter(p -> matcher.matches(p.getFileName()))
              .map(p -> p.toFile().getAbsolutePath())
              .collect(Collectors.toSet());

      selectedFiles.addAll(files);
    }
    return selectedFiles;
  }

  static void logGreen(String message) {
    log(green(message));
  }

  static void logYellow(String message) {
    log(yellow(message));
  }

  static void logRed(String message) {
    log(red(message));
  }

  static String green(String message) {
    return colored("[32m", message);
  }

  static String yellow(String message) {
    return colored("[33m", message);
  }

  static String red(String message) {
    return colored("[31m", message);
  }

  static String colored(String color, String message) {
    return "\u001B" + color + message + "\u001B[0m";
  }

  static void log(String message) {
    System.err.println(message);
  }

  static void newLine() {
    log("");
  }

  static void logIndent(String message) {
    log("    " + message);
  }

  static void out(String message) {
    System.out.println(message);
  }

  static void printIndent(String message) {
    print("    " + message);
  }

  static void print(String message) {
    System.err.print(message);
  }

  static final class CloudsmithPackageAccess {

    private static final String UPLOAD_URL_TPL = "https://upload.cloudsmith.io/{org}/{repo}/{file}";
    private static final String CREATE_PACKAGE_URL_TPL =
        "https://api-prd.cloudsmith.io/v1/packages/{org}/{repo}/upload/{type}/";
    private static final String SEARCH_URL_TPL = "https://api.cloudsmith.io/packages/{org}/{repo}/";
    private final HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();
    private final Input input;
    private final Source source;
    private final Params params;

    private CloudsmithPackageAccess(Input input) {
      this.input = input;
      this.source = input.source();
      this.params = input.params();
    }

    static String base64(String in) {
      return Base64.getEncoder().encodeToString(in.getBytes(StandardCharsets.UTF_8));
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
      return GSON.toJson(parameters);
    }

    List<Package> find() throws IOException, InterruptedException {
      List queryParameters = new ArrayList();
      Source source = input.source();
      Version version = input.version();

      if (input.source().name() != null) {
        queryParameters.add("filename:" + input.source().name());
      }

      if (input.params() != null && input.params().versionFilter() != null) {
        queryParameters.add("version:" + input.params().versionFilter());
      }

      if (version != null) {
        if (version.version() != null) {
          queryParameters.add("version:" + version.version());
        }
        if (version.distribution() != null) {
          String[] nameCodename = version.distribution().split("/");
          queryParameters.add("distribution:" + nameCodename[0]);
          queryParameters.add("distribution:" + nameCodename[1]);
        }
        if (version.type() != null && !"raw".equals(version.type())) {
          queryParameters.add("filename:" + version.type() + "$");
        }
      }

      if ((version == null || version.distribution() == null) && source.distribution() != null) {
        String[] nameCodename = source.distribution().split("/");
        queryParameters.add("distribution:" + nameCodename[0]);
        queryParameters.add("distribution:" + nameCodename[1]);
      }

      String url =
          SEARCH_URL_TPL
              .replace("{org}", source.organization())
              .replace("{repo}", source.repository());

      if (!queryParameters.isEmpty()) {
        String query = String.join(" AND ", queryParameters);
        newLine();
        log(yellow("Query: ") + query);
        newLine();
        query = Utils.encode(query);
        url = url + "?query=" + query;
      }

      HttpRequest request = requestBuilder().uri(URI.create(url)).GET().build();

      Type type = new TypeToken<List<Package>>() {}.getType();

      List<Package> packages = new ArrayList<>();
      boolean hasMore = true;
      while (hasMore) {
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        packages.addAll(GSON.fromJson(response.body(), type));
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
          UPLOAD_URL_TPL
              .replace("{org}", input.source().organization())
              .replace("{repo}", input.source().repository())
              .replace("{file}", path.getFileName().toString());

      HttpRequest request =
          requestBuilder()
              .setHeader("Content-Sha256", sha256(content))
              .uri(URI.create(uploadUrl))
              .PUT(BodyPublishers.ofByteArray(content))
              .build();
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      String responseBody = response.body();

      String identifier =
          GSON.fromJson(responseBody, JsonObject.class).get("identifier").getAsString();
      String createUrl =
          CREATE_PACKAGE_URL_TPL
              .replace("{org}", input.source().organization())
              .replace("{repo}", input.source().repository())
              .replace("{type}", type);

      creationParameters.put("package_file", identifier);
      if (source.distribution() != null) {
        creationParameters.put("distribution", source.distribution());
      }

      if (params != null && params.tags() != null) {
        creationParameters.put("tags", params.tags());
      }

      String createJson = uploadJsonBody(creationParameters);

      request =
          requestBuilder()
              .setHeader("Content-Type", "application/json")
              .uri(URI.create(createUrl))
              .POST(BodyPublishers.ofString(createJson))
              .build();

      responseBody = client.send(request, BodyHandlers.ofString()).body();

      String selfUrl = GSON.fromJson(responseBody, JsonObject.class).get("self_url").getAsString();
      return selfUrl;
    }

    void delete(Package p) throws IOException, InterruptedException {
      HttpRequest request = requestBuilder().uri(URI.create(p.selfUrl())).DELETE().build();
      HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
      if (response.statusCode() != 204) {
        logRed(
            "Error while trying to delete "
                + p.selfUrl()
                + ". HTTP response code is "
                + response.statusCode());
      }
    }

    byte[] download(String packageUrl) throws IOException, InterruptedException {
      HttpRequest request = requestBuilder().uri(URI.create(packageUrl)).GET().build();
      HttpResponse<byte[]> response = client.send(request, BodyHandlers.ofByteArray());
      return response.body();
    }

    private Package findPackage(String packageUrl) throws IOException, InterruptedException {
      HttpRequest request = requestBuilder().uri(URI.create(packageUrl)).GET().build();
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      String responseBody = response.body();
      return GSON.fromJson(responseBody, Package.class);
    }

    private Builder requestBuilder() {
      return auth(HttpRequest.newBuilder());
    }

    private Builder auth(Builder builder) {
      return builder.setHeader(
          "Authorization",
          "Basic " + base64(input.source.username() + ":" + input.source.apiKey()));
    }
  }

  static class Input {

    private Params params;
    private Source source;
    private Version version;

    Params params() {
      return params;
    }

    Source source() {
      return source;
    }

    Version version() {
      return version;
    }

    @Override
    public String toString() {
      return "Input{" + "params=" + params + ", source=" + source + ", version=" + version + '}';
    }
  }

  static class Version {

    private String version;
    private String distribution;
    private String type;

    public String version() {
      return version;
    }

    public String distribution() {
      return distribution;
    }

    public String type() {
      return type;
    }
  }

  static class Params {

    private boolean delete;
    private boolean do_delete;
    private String globs;
    private String tags;
    private String local_path;
    private String version; // to extract version for "raw" packages
    // for deletion
    private String version_filter;
    private int keep_last_n;

    public String localPath() {
      return local_path;
    }

    public String globs() {
      return globs;
    }

    public String tags() {
      return tags;
    }

    public String versionFilter() {
      return version_filter;
    }

    public boolean delete() {
      return delete;
    }

    public int keepLastN() {
      return keep_last_n;
    }

    public String version() {
      return version;
    }

    public boolean doDelete() {
      return do_delete;
    }

    @Override
    public String toString() {
      return "Params{"
          + "globs='"
          + globs
          + '\''
          + ", tags='"
          + tags
          + '\''
          + ", local_path='"
          + local_path
          + '\''
          + '}';
    }
  }

  static class Source {

    private String username;
    private String organization;
    private String repository;
    private String api_key;
    private String name;
    private String type;
    private String version;
    private String distribution;

    public String username() {
      return username;
    }

    public String organization() {
      return organization;
    }

    public String repository() {
      return repository;
    }

    public String apiKey() {
      return api_key;
    }

    public String name() {
      return name;
    }

    public String type() {
      return type;
    }

    public String version() {
      return version;
    }

    public String distribution() {
      return distribution;
    }

    @Override
    public String toString() {
      return "Source{"
          + "username='"
          + username
          + '\''
          + ", organization='"
          + organization
          + '\''
          + ", repository='"
          + repository
          + '\''
          + ", api_key='"
          + api_key
          + '\''
          + ", name='"
          + name
          + '\''
          + ", type='"
          + type
          + '\''
          + ", version='"
          + version
          + '\''
          + ", distribution='"
          + distribution
          + '\''
          + '}';
    }
  }
}
