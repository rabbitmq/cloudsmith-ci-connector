/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

import static com.rabbitmq.ci.RetryUtils.retry;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class CloudsmithLogic {

  static final String CONCOURSE_DELETED_VERSION = "<DELETED>";
  static final String CONCOURSE_JSON_DELETED_VERSION =
      "{\n" + "  \"version\": { \"version\": \"<DELETED>\" },\n" + "  \"metadata\": [ ]\n" + "}";
  static final Duration PACKAGE_SYNCHRONIZATION_TIMEOUT = Duration.ofMinutes(5);

  private final Log log;

  private final Input input;

  CloudsmithLogic(Input input, Log log) {
    this.input = input;
    this.log = log;
  }

  static Predicate<Package> globPredicate(String globs) {
    globs = globs == null || globs.isBlank() ? "*" : globs;
    return Arrays.stream(globs.split(","))
        .map(String::trim)
        .map(g -> "glob:" + g)
        .map(g -> FileSystems.getDefault().getPathMatcher(g))
        .map(
            pathMatcher ->
                (Predicate<Package>) p -> pathMatcher.matches(Path.of(p.filename()).getFileName()))
        .reduce(
            aPackage -> false,
            (packagePredicate, packagePredicate2) -> packagePredicate.or(packagePredicate2));
  }

  static List<String> filterForDeletion(
      Collection<PackageVersion> versions, int keepLastN, boolean orderByVersion) {
    if (versions.isEmpty()) {
      return Collections.emptyList();
    } else if (keepLastN <= 0) {
      // do not want to keep any, return all
      return versions.stream().map(v -> v.version).collect(toList());
    } else if (keepLastN >= versions.size()) {
      // we want to keep more than we have, so nothing to delete
      return Collections.emptyList();
    } else {
      class VersionWrapper {

        private final PackageVersion version;
        private final ComparableVersion comparableVersion;

        VersionWrapper(PackageVersion version) {
          this.version = version;
          this.comparableVersion =
              new ComparableVersion(
                  version.version.startsWith("1:")
                      ? version.version.substring(2)
                      : version.version);
        }
      }
      Comparator<VersionWrapper> comparator =
          orderByVersion
              ? Comparator.comparing(packageVersion -> packageVersion.comparableVersion)
              : Comparator.comparing(packageVersion -> packageVersion.version.lastPackageDate);
      return versions.stream()
          .map(VersionWrapper::new)
          .sorted(comparator)
          .limit(versions.size() - keepLastN)
          .map(w -> w.version.version)
          .collect(toList());
    }
  }

  static String latestMinor(List<String> versions) {
    if (versions == null || versions.isEmpty()) {
      return null;
    } else {
      return versions.stream()
          .map(CloudsmithLogic::extractMinor)
          .distinct()
          .map(ComparableVersion::new)
          .max(Comparator.naturalOrder())
          .get()
          .toString();
    }
  }

  static List<String> lastMinorPatches(String minorToIgnore, List<String> versions) {
    if (versions == null || versions.isEmpty()) {
      return Collections.emptyList();
    }
    class VersionWrapper {

      private final String version;
      private final ComparableVersion comparableVersion;
      private final String minor;

      VersionWrapper(String version) {
        this.version = version;
        // e.g. 1:22.3.4.3-1, removing 1:
        String curatedVersion = version.startsWith("1:") ? version.substring(2) : version;
        this.comparableVersion = new ComparableVersion(curatedVersion);
        this.minor = extractMinor(version);
      }
    }

    Map<String, List<VersionWrapper>> minors =
        versions.stream()
            .map(VersionWrapper::new)
            .filter(v -> !v.minor.equals(minorToIgnore))
            .collect(groupingBy(versionWrapper -> versionWrapper.minor));

    Comparator<VersionWrapper> comparator =
        Comparator.comparing(wrapper -> wrapper.comparableVersion);
    return minors.values().stream()
        .map(
            patches -> {
              if (patches.size() == 1) {
                return patches.get(0).version;
              } else {
                return Collections.max(patches, comparator).version;
              }
            })
        .collect(toList());
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

  static Collection<String> filenames(Collection<String> files) {
    return files.stream()
        .map(f -> Paths.get(f).getFileName().toString())
        .collect(Collectors.toSet());
  }

  static String extractMinor(String version) {
    // e.g. 1:22.3.4.3-1, removing 1:
    String curatedVersion = version.startsWith("1:") ? version.substring(2) : version;
    // e.g. 22.3-1, removing -1
    curatedVersion =
        curatedVersion.contains("-")
            ? curatedVersion.substring(0, curatedVersion.lastIndexOf("-"))
            : curatedVersion;
    String[] digits = curatedVersion.split("\\.");
    if (digits == null || digits.length <= 1) {
      return curatedVersion;
    } else {
      return digits[0] + "." + digits[1];
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

  static String determinePackagesType(Collection<String> filenames) {
    List<String> extensions =
        filenames.stream().map(f -> fileExtension(f.toLowerCase())).distinct().collect(toList());
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

  static String base64(String in) {
    return Base64.getEncoder().encodeToString(in.getBytes(StandardCharsets.UTF_8));
  }

  void check() throws InterruptedException {
    String currentVersion = input.version() == null ? null : input.version().version();
    input.version(null); // should not be a search criteria
    CloudsmithPackageAccess access = new CloudsmithPackageAccess(this.input, this.log);
    List<Package> packages = access.find();
    List<String> versions = checkForNewVersions(currentVersion, packages);
    String output =
        Utils.GSON.toJson(
            versions.stream().map(v -> Collections.singletonMap("version", v)).collect(toList()));
    log.out(output);
  }

  void in(String directory) throws InterruptedException {
    if (input.version() != null && CONCOURSE_DELETED_VERSION.equals(input.version().version())) {
      log.log("Getting special version <DELETED> is a no-op; returning it as is");
      log.out(CONCOURSE_JSON_DELETED_VERSION);
    } else {
      CloudsmithPackageAccess access = new CloudsmithPackageAccess(this.input, this.log);
      List<Package> packages = retry(access::find);
      String outputDirectory = directory == null ? System.getProperty("user.dir") : directory;

      Predicate<Package> globPredicate =
          globPredicate(
              input.params() == null || input.params().globs() == null
                  ? null
                  : input.params().globs());

      if (packages.stream().anyMatch(globPredicate)) {
        log.logGreen("Downloading files...");
      }
      packages.stream()
          .filter(globPredicate)
          .forEach(
              p -> {
                try {
                  byte[] content = access.download(p.cdnUrl());
                  Files.write(Path.of(outputDirectory, p.filename()), content);
                  String checksum = CloudsmithPackageAccess.sha256(content);
                  String message =
                      checksum.equals(p.sha256()) ? "OK" : "OK? (checksum verification failed)";
                  log.logIndent(log.green(p.filename() + ": ") + message);
                } catch (Exception e) {
                  log.logIndent(log.red(p.filename() + ": " + e.getMessage()));
                }
              });

      if (packages.stream().anyMatch(Predicate.not(globPredicate))) {
        log.newLine();
        log.logGreen("Ignored:");
      }

      packages.stream()
          .filter(Predicate.not(globPredicate))
          .forEach(p -> log.logIndent(p.filename()));

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("version", input.version());
      out.put("metadata", Collections.emptyList());

      log.out(Utils.GSON.toJson(out));
    }
  }

  void out(String inputDirectory) throws InterruptedException, IOException {
    if (input.params().delete()) {
      delete();
    } else {
      upload(inputDirectory);
    }
  }

  void delete() throws InterruptedException {
    CloudsmithPackageAccess access = new CloudsmithPackageAccess(this.input, this.log);
    List<Package> packages = retry(access::find);

    Map<String, PackageVersion> versions =
        packages.stream()
            .reduce(
                new HashMap<>(),
                (packageVersions, aPackage) -> {
                  PackageVersion packageVersion =
                      packageVersions.computeIfAbsent(
                          aPackage.version(),
                          version -> {
                            PackageVersion pv = new PackageVersion(version);
                            pv.consider(aPackage);
                            return pv;
                          });
                  packageVersion.consider(aPackage);
                  return packageVersions;
                },
                (stringPackageVersionHashMap, stringPackageVersionHashMap2) -> {
                  stringPackageVersionHashMap.putAll(stringPackageVersionHashMap2);
                  return stringPackageVersionHashMap;
                });

    List<String> versionsToDelete =
        filterForDeletion(
            versions.values(), input.params().keepLastN(), input.source().orderByVersion());

    Collection<String> deletionExceptions = Collections.emptySet();
    if (input.params().keepLastMinorPatches()) {
      String latestMinor =
          latestMinor(versions.values().stream().map(v -> v.version).collect(toList()));
      deletionExceptions = lastMinorPatches(latestMinor, versionsToDelete);
      if (!input.source().orderByVersion()) {
        log.logYellow("Warning: keep_last_minor_patches should only be used with order_by:version");
      }
    }

    DateTimeFormatter dateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmO", Locale.ENGLISH);
    Function<String, String> formatVersion =
        version -> {
          PackageVersion packageVersion = versions.get(version);
          return String.format(
              "%s [%s]",
              packageVersion.version, dateTimeFormatter.format(packageVersion.lastPackageDate));
        };

    log.log(
        log.green("Version(s) detected: ")
            + String.join(
                ", ",
                versions.values().stream()
                    .map(pv -> formatVersion.apply(pv.version))
                    .collect(toList())));
    log.log(
        log.green("Version(s) to delete: ")
            + String.join(", ", versionsToDelete.stream().map(formatVersion).collect(toList())));

    if (input.params().keepLastMinorPatches() && !deletionExceptions.isEmpty()) {
      log.log(
          log.green("Deletion exception(s) (last minor patches): ")
              + String.join(
                  ", ", deletionExceptions.stream().map(formatVersion).collect(toList())));
    }

    Collection<String> exceptionsToDeletion = new HashSet<>(deletionExceptions);

    log.newLine();

    List<String> versionsToKeep =
        versions.values().stream()
            .filter(
                pv ->
                    !versionsToDelete.contains(pv.version)
                        || exceptionsToDeletion.contains(pv.version))
            .map(pv -> formatVersion.apply(pv.version))
            .collect(toList());

    if (!versionsToKeep.isEmpty()) {
      log.log(
          log.green("Version(s) to keep: ")
              + String.join(", ", versionsToKeep.stream().collect(toList())));
    }

    log.newLine();

    AtomicInteger deletedCount = new AtomicInteger();
    log.logGreen("Packages:");
    packages.forEach(
        p -> {
          boolean shouldBeDeleted =
              versionsToDelete.contains(p.version()) && !exceptionsToDeletion.contains(p.version());
          if (shouldBeDeleted) {
            deletedCount.incrementAndGet();
          }
          if (shouldBeDeleted && input.params().doDelete()) {
            try {
              access.delete(p);
              log.logIndent(log.red("deleting " + p.filename()));
            } catch (Exception e) {
              log.logRed("Error while trying to delete " + p.selfUrl() + ": " + e.getMessage());
            }
          } else {
            boolean isDeletionException = exceptionsToDeletion.contains(p.version());
            log.logIndent(
                shouldBeDeleted
                    ? (log.red("deleting " + p.filename()) + log.yellow(" (skipped)"))
                    : "keeping "
                        + p.filename()
                        + (isDeletionException ? " (latest minor patch)" : ""));
          }
        });

    log.newLine();
    log.logGreen("Deleted " + deletedCount.get() + " file(s)");

    log.out(CONCOURSE_JSON_DELETED_VERSION);
  }

  void upload(String inputDirectory) throws IOException {
    inputDirectory =
        (input.params().localPath() == null || input.params().localPath().isBlank())
            ? inputDirectory
            : Paths.get(inputDirectory, input.params().localPath()).toFile().getAbsolutePath();

    String[] globs =
        (input.params().globs() == null || input.params().globs().isBlank())
            ? new String[] {"*"}
            : input.params().globs().split(",");

    Set<String> selectedFiles = selectFilesForUpload(inputDirectory, globs);

    log.logGreen("Local path:");
    log.logIndent(input.params().localPath());
    log.log("");
    log.logGreen("Files:");
    selectedFiles.forEach(file -> log.logIndent(Paths.get(file).getFileName().toString()));

    log.newLine();

    Map<String, Object> creationParameters = new LinkedHashMap<>();
    Collection<String> filenames = filenames(selectedFiles);
    String extractedVersion = null;
    if (input.params() != null && input.params().version() != null) {
      extractedVersion = extractVersion(input.params().version(), filenames);
      if (extractedVersion != null && !extractedVersion.isBlank()) {
        creationParameters.put("version", extractedVersion);
        log.log(log.green("Extracted version: ") + extractedVersion);
        log.newLine();
      }
    }

    String packagesType = determinePackagesType(filenames);

    CloudsmithPackageAccess access = new CloudsmithPackageAccess(this.input, this.log);
    List<String> uploadFilesUrls = new ArrayList<>(selectedFiles.size());
    for (String selectedFile : selectedFiles) {
      Path path = Paths.get(selectedFile);
      log.log(log.green("Upload file: ") + path.getFileName());
      try {
        String selfUrl = retry(() -> access.upload(selectedFile, creationParameters, packagesType));
        if (selfUrl == null) {
          log.logIndent("Upload failed, duplicated raw package?");
        } else {
          log.logIndent(selfUrl);
          uploadFilesUrls.add(selfUrl);
        }
      } catch (Exception e) {
        log.logIndent(log.red("Error: " + e.getMessage()));
      }
    }

    log.log("");

    String version = null;

    if (!uploadFilesUrls.isEmpty()) {
      log.logGreen("Checking synchronization of packages...");
      for (String packageUrl : uploadFilesUrls) {
        Package p = null;
        try {
          p = retry(() -> access.findPackage(packageUrl));
          long waitTime = Duration.ofSeconds(10).toMillis();
          int waitedTime = 0;
          long timeoutInMs = PACKAGE_SYNCHRONIZATION_TIMEOUT.toMillis();
          log.printIndent(log.green(p.filename() + ": "));
          boolean timedOut = true;
          while (waitedTime <= timeoutInMs) {
            if (p.isSyncCompleted()) {
              log.log("OK");
              if (version == null) {
                version = p.version();
              }
              timedOut = false;
              break;
            }
            if (p.isSyncFailed()) {
              log.logRed("Error " + log.italic("(" + p.statusReason() + ")"));
              if (version == null) {
                version = p.version();
              }
              timedOut = false;
              log.printIndent(log.indent("Deleting... "));
              try {
                access.delete(p);
                log.logGreen("OK");
              } catch (Exception e) {
                log.logRed("Error: " + e.getMessage());
              }
              break;
            }
            log.print(".");
            Thread.sleep(waitTime);
            waitedTime += waitTime;
            p = retry(() -> access.findPackage(packageUrl));
          }
          if (timedOut) {
            log.print(
                log.red(
                    " timed out after "
                        + PACKAGE_SYNCHRONIZATION_TIMEOUT.toSeconds()
                        + " seconds"));
          }
        } catch (Exception e) {
          log.logIndent(log.red(p == null ? "Error" : p.filename() + ": ") + e.getMessage());
        }
      }
    }

    if (version == null && uploadFilesUrls.isEmpty() && extractedVersion != null) {
      // it may be a whole set a re-submitted raw packages. If re-publish is disabled,
      // the uploads fails immediately, so there is no way to get the version from the
      // packages. We just set the version we extracted as the output version.
      version = extractedVersion;
    }

    if (version == null) {
      log.logRed("No version found");
      System.exit(1);
    } else {
      Map<String, String> outVersion = new LinkedHashMap<>();
      outVersion.put("version", version);
      if (input.source().distribution() != null) {
        outVersion.put("distribution", input.source().distribution());
      }

      outVersion.put("type", packagesType);

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("version", outVersion);
      out.put("metadata", Collections.emptyList());

      log.out(Utils.GSON.toJson(out));
    }
  }

  static List<String> checkForNewVersions(String currentVersion, List<Package> packages) {
    class VersionWrapper {

      final String original;
      final ComparableVersion comparableVersion;
      boolean hasNonCompletedPackage = false;

      VersionWrapper(String version) {
        this.original = version;
        this.comparableVersion =
            new ComparableVersion(version.startsWith("1:") ? version.substring(2) : version);
      }

      void considerPackage(Package p) {
        if (!hasNonCompletedPackage) {
          hasNonCompletedPackage = !(p.isSyncCompleted() && !p.isSyncFailed());
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }
        VersionWrapper that = (VersionWrapper) o;
        return original.equals(that.original);
      }

      @Override
      public int hashCode() {
        return Objects.hash(original);
      }

      @Override
      public String toString() {
        return "VersionWrapper{" + "original='" + original + '\'' + '}';
      }
    }

    VersionWrapper comparableCurrentVersion =
        new VersionWrapper(currentVersion == null ? "0.0.0" : currentVersion);
    Predicate<VersionWrapper> afterCurrentVersion =
        currentVersion == null
            ? v -> true
            : v -> v.comparableVersion.compareTo(comparableCurrentVersion.comparableVersion) >= 0;

    HashMap<String, VersionWrapper> allVersions =
        packages.stream()
            .reduce(
                new HashMap<>(),
                (versions, p) -> {
                  VersionWrapper versionWrapper =
                      versions.computeIfAbsent(p.version(), VersionWrapper::new);
                  versionWrapper.considerPackage(p);
                  return versions;
                },
                (stringVersionWrapperMap, stringVersionWrapperMap2) -> {
                  stringVersionWrapperMap.putAll(stringVersionWrapperMap2);
                  return stringVersionWrapperMap;
                });

    if (currentVersion != null) {
      allVersions.put(currentVersion, new VersionWrapper(currentVersion));
    }

    return allVersions.values().stream()
        .filter(v -> !v.hasNonCompletedPackage)
        .filter(afterCurrentVersion)
        .sorted(Comparator.comparing(v -> v.comparableVersion))
        .distinct()
        .map(versionWrapper -> versionWrapper.original)
        .collect(toList());
  }

  // for out
  static class PackageVersion {

    final String version;
    ZonedDateTime lastPackageDate;

    PackageVersion(String version) {
      this.version = version;
    }

    void consider(Package p) {
      if (lastPackageDate == null) {
        lastPackageDate = p.uploadedAt();
      } else {
        lastPackageDate =
            lastPackageDate.isBefore(p.uploadedAt()) ? p.uploadedAt() : lastPackageDate;
      }
    }
  }
}
