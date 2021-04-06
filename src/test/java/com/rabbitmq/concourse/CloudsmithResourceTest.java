package com.rabbitmq.concourse;

import static com.rabbitmq.concourse.CloudsmithResource.CloudsmithPackageAccess.uploadJsonBody;
import static com.rabbitmq.concourse.CloudsmithResource.checkForNewVersions;
import static com.rabbitmq.concourse.CloudsmithResource.extractVersion;
import static com.rabbitmq.concourse.CloudsmithResource.filterForDeletion;
import static com.rabbitmq.concourse.CloudsmithResource.globPredicate;
import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.concourse.CloudsmithResource.PackageVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CloudsmithResourceTest {

  static void newFiles(Path directory, String... names) {
    Arrays.stream(names)
        .forEach(
            name -> {
              try {
                Files.write(directory.resolve(name), "hello".getBytes(StandardCharsets.UTF_8));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  static Consumer<Iterable<? extends String>> filenames(String... names) {
    return filenames ->
        assertThat(
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                            filenames.iterator(), Spliterator.ORDERED),
                        false)
                    .map(f -> Paths.get(f).getFileName().toString())
                    .collect(Collectors.toSet()))
            .containsAll(Arrays.asList(names));
  }

  static Set<String> selectFilesForUpload(Path inputDirectory, String... globs) throws IOException {
    return CloudsmithResource.selectFilesForUpload(
        inputDirectory.toFile().getAbsolutePath(), globs);
  }

  static Package p(String version, boolean isSyncCompleted, boolean hasSyncFailed) {
    Package p = new Package();
    p.setVersion(version);
    p.setIs_sync_completed(isSyncCompleted);
    p.setIs_sync_failed(hasSyncFailed);
    return p;
  }

  static List<Package> packages(Package... packages) {
    List<Package> ps = Arrays.asList(packages);
    Collections.shuffle(ps);
    return ps;
  }

  static Package p(String name) {
    Package p = new Package();
    p.setFilename(name);
    return p;
  }

  static PackageVersion pv(String version, String date) {
    PackageVersion pv = new PackageVersion(version);
    pv.lastPackageDate =
        ZonedDateTime.parse(date + "T12:58:11.418817Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);
    return pv;
  }

  @Test
  void selectedFilesForUploadShouldSelectFilesThatMatchGlob(@TempDir Path inputDirectory)
      throws IOException {
    newFiles(inputDirectory, "test1.txt", "test2.txt", "test.bin");
    assertThat(selectFilesForUpload(inputDirectory, "*.txt"))
        .hasSize(2)
        .satisfies(filenames("test1.txt", "test2.txt"));

    newFiles(
        inputDirectory,
        "erlang-23.2.7-2.el7.x86_64.rpm",
        "erlang-debuginfo-23.2.7-2.el7.x86_64.rpm");
    assertThat(selectFilesForUpload(inputDirectory, "erlang-23*.el7.x86_64.rpm"))
        .hasSize(1)
        .satisfies(filenames("erlang-23.2.7-2.el7.x86_64.rpm"));

    assertThat(selectFilesForUpload(inputDirectory, "erlang*-23*.el7.x86_64.rpm"))
        .hasSize(2)
        .satisfies(
            filenames(
                "erlang-23.2.7-2.el7.x86_64.rpm", "erlang-debuginfo-23.2.7-2.el7.x86_64.rpm"));
  }

  @Test
  void selectedFilesForUploadShouldSelectFilesThatMatchGlobs(@TempDir Path inputDirectory)
      throws IOException {
    newFiles(inputDirectory, "test1.txt", "test2.txt", "test.bin", "data.dat");
    assertThat(selectFilesForUpload(inputDirectory, "*.txt", "*.dat"))
        .hasSize(3)
        .satisfies(filenames("test1.txt", "test2.txt", "data.dat"));
  }

  @Test
  void filterForDeletionShouldReturnVersionsToDelete() {
    List<PackageVersion> versions =
        Arrays.asList(
                "1:22.0-1",
                "1:22.3-1",
                "1:22.3.4-1",
                "1:22.3.4.1-1",
                "1:22.3.4.1-2",
                "1:22.3.4.10-1",
                "1:22.3.4.11-1",
                "1:22.3.4.12-1",
                "1:22.3.4.13-1",
                "1:22.3.4.14-1",
                "1:22.3.4.15-1",
                "1:22.3.4.16-1", // latest
                "1:22.3.4.2-1",
                "1:22.3.4.3-1",
                "1:22.3.4.4-1",
                "1:22.3.4.5-1",
                "1:22.3.4.6-1",
                "1:22.3.4.7-1",
                "1:22.3.4.8-1",
                "1:22.3.4.9-1")
            .stream()
            .map(PackageVersion::new)
            .collect(Collectors.toList());

    Collections.shuffle(versions);
    assertThat(filterForDeletion(versions, 2, true))
        .hasSize(versions.size() - 2)
        .containsExactlyInAnyOrder(
            "1:22.0-1",
            "1:22.3-1",
            "1:22.3.4-1",
            "1:22.3.4.1-1",
            "1:22.3.4.1-2",
            "1:22.3.4.10-1",
            "1:22.3.4.11-1",
            "1:22.3.4.12-1",
            "1:22.3.4.13-1",
            "1:22.3.4.14-1",
            "1:22.3.4.2-1",
            "1:22.3.4.3-1",
            "1:22.3.4.4-1",
            "1:22.3.4.5-1",
            "1:22.3.4.6-1",
            "1:22.3.4.7-1",
            "1:22.3.4.8-1",
            "1:22.3.4.9-1")
        .doesNotContain("1:22.3.4.15-1", "1:22.3.4.16-1");

    assertThat(filterForDeletion(versions, versions.size() - 1, true))
        .hasSize(1)
        .containsExactly("1:22.0-1");

    assertThat(filterForDeletion(versions, 0, true))
        .hasSameSizeAs(versions)
        .hasSameElementsAs(versions.stream().map(v -> v.version).collect(Collectors.toList()));

    assertThat(filterForDeletion(versions, versions.size() + 1, true)).isEmpty();

    versions =
        Arrays.asList(
                "22.2.4-1.el8",
                "22.3-1.el8",
                "22.3.4-1.el8",
                "22.3.4.1-1.el8",
                "22.3.4.10-1.el8",
                "22.3.4.11-1.el8",
                "22.3.4.12-1.el8",
                "22.3.4.16-1.el8", // latest
                "22.3.4.2-1.el8",
                "22.3.4.3-1.el8",
                "22.3.4.4-1.el8",
                "22.3.4.5-1.el8",
                "22.3.4.6-1.el8",
                "22.3.4.7-1.el8")
            .stream()
            .map(PackageVersion::new)
            .collect(Collectors.toList());
    Collections.shuffle(versions);
    assertThat(filterForDeletion(versions, 2, true))
        .hasSize(versions.size() - 2)
        .containsExactlyInAnyOrder(
            "22.2.4-1.el8",
            "22.3-1.el8",
            "22.3.4-1.el8",
            "22.3.4.1-1.el8",
            "22.3.4.10-1.el8",
            "22.3.4.11-1.el8",
            "22.3.4.2-1.el8",
            "22.3.4.3-1.el8",
            "22.3.4.4-1.el8",
            "22.3.4.5-1.el8",
            "22.3.4.6-1.el8",
            "22.3.4.7-1.el8")
        .doesNotContain("22.3.4.12-1.el8", "22.3.4.16-1.el8");

    assertThat(filterForDeletion(versions, versions.size() - 1, true))
        .hasSize(1)
        .containsExactly("22.2.4-1.el8");

    assertThat(filterForDeletion(versions, 0, true))
        .hasSameSizeAs(versions)
        .hasSameElementsAs(versions.stream().map(v -> v.version).collect(Collectors.toList()));

    assertThat(filterForDeletion(versions, versions.size() + 1, true)).isEmpty();
  }

  @Test
  void filterForDeletionShouldReturnVersionsToDeleteWhenUsingUploadingDate() {
    List<PackageVersion> versions =
        Arrays.asList(
            pv("1.1", "2021-04-01"),
            pv("1.2", "2021-04-02"),
            pv("1.3", "2021-04-03"),
            pv("1.4", "2021-04-04"));

    Collections.shuffle(versions);

    assertThat(filterForDeletion(versions, 2, false))
        .hasSize(versions.size() - 2)
        .containsExactly("1.1", "1.2");

    assertThat(filterForDeletion(versions, 1, false))
        .hasSize(versions.size() - 1)
        .containsExactly("1.1", "1.2", "1.3");

    versions =
        Arrays.asList(
            pv("1.1", "2021-04-01"),
            pv("1.3", "2021-04-02"),
            pv("1.2", "2021-04-03"), // uploaded after 1.3
            pv("1.4", "2021-04-04"));

    Collections.shuffle(versions);

    assertThat(filterForDeletion(versions, 2, false))
        .hasSize(versions.size() - 2)
        .containsExactly("1.1", "1.3");

    assertThat(filterForDeletion(versions, 2, true))
        .hasSize(versions.size() - 2)
        .containsExactly("1.1", "1.2");
  }

  @Test
  void createJsonBodyToUpload() {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("package_file", "XeXAmp1eF11e");
    parameters.put("distribution", "ubuntu/focal");
    parameters.put("tags", "erlang,erlang-23.x");
    assertThat(uploadJsonBody(parameters))
        .isEqualTo(
            "{\"package_file\":\"XeXAmp1eF11e\",\"distribution\":\"ubuntu/focal\",\"tags\":\"erlang,erlang-23.x\"}");

    parameters.clear();
    parameters.put("package_file", "XeXAmp1eF11e");
    parameters.put("version", "3.8.14");
    parameters.put("tags", "erlang,erlang-23.x");
    assertThat(uploadJsonBody(parameters))
        .isEqualTo(
            "{\"package_file\":\"XeXAmp1eF11e\",\"version\":\"3.8.14\",\"tags\":\"erlang,erlang-23.x\"}");
  }

  @Test
  void extractVersionWhenUploadRawPackages() {
    Collection<String> filenames =
        Arrays.asList(
            "rabbitmq-server-3.8.14-1.suse.noarch.rpm",
            "rabbitmq-server-3.8.14-1.suse.noarch.rpm.asc",
            "rabbitmq-server-3.8.14-1.suse.src.rpm",
            "rabbitmq-server-3.8.14-1.suse.src.rpm.asc",
            "rabbitmq-server-3.8.14.exe",
            "rabbitmq-server-3.8.14.exe.asc",
            "rabbitmq-server-3.8.14.tar.xz",
            "rabbitmq-server-3.8.14.tar.xz.asc",
            "rabbitmq-server-generic-unix-3.8.14.tar.xz",
            "rabbitmq-server-generic-unix-3.8.14.tar.xz.asc",
            "rabbitmq-server-generic-unix-latest-toolchain-3.8.14.tar.xz",
            "rabbitmq-server-generic-unix-latest-toolchain-3.8.14.tar.xz.asc");

    String versionRegex = "rabbitmq-server-(\\d.*)\\.tar\\.xz";
    assertThat(extractVersion(versionRegex, filenames)).isEqualTo("3.8.14");
    assertThat(extractVersion(versionRegex, Collections.emptyList())).isNull();
    assertThat(extractVersion("foo", filenames)).isNull();

    filenames =
        Arrays.asList(
            "rabbitmq-server-3.9.0~alpha.473-1.sles11.noarch.rpm.asc",
            "rabbitmq-server-3.9.0~alpha.473-1.sles11.src.rpm",
            "rabbitmq-server-3.9.0~alpha.473-1.sles11.src.rpm.asc",
            "rabbitmq-server-3.9.0~alpha.473-1.suse.noarch.rpm",
            "rabbitmq-server-3.9.0~alpha.473-1.suse.noarch.rpm.asc",
            "rabbitmq-server-3.9.0~alpha.473-1.suse.src.rpm",
            "rabbitmq-server-3.9.0-alpha.473.tar.xz",
            "rabbitmq-server-3.9.0~alpha.473-1.suse.src.rpm.asc",
            "rabbitmq-server-generic-unix-3.9.0-alpha.473.tar.xz",
            "rabbitmq-server-generic-unix-3.9.0-alpha.473.tar.xz.asc",
            "rabbitmq-server-generic-unix-latest-toolchain-3.9.0-alpha.473.tar.xz",
            "rabbitmq-server-generic-unix-latest-toolchain-3.9.0-alpha.473.tar.xz.asc",
            "rabbitmq-server-windows-3.9.0-alpha.473.zip");

    assertThat(extractVersion(versionRegex, filenames)).isEqualTo("3.9.0-alpha.473");
    assertThat(extractVersion(versionRegex, Collections.emptyList())).isNull();
    assertThat(extractVersion("foo", filenames)).isNull();
  }

  @Test
  void checkForNewVersionsShouldReturnCurrentAndLaterVersionInChronologicalOrder() {
    List<Package> packages =
        packages(
            p("1", true, false),
            p("1", true, false),
            p("2", true, false),
            p("3", true, false),
            p("4", true, false),
            p("7", true, false));
    String currentVersion = "2";
    assertThat(checkForNewVersions(currentVersion, packages)).containsExactly("2", "3", "4", "7");
    assertThat(checkForNewVersions(null, packages)).containsExactly("1", "2", "3", "4", "7");

    packages =
        packages(
            p("1", true, false),
            p("1", true, false),
            p("2", true, false),
            p("3", true, false),
            p("4", true, true), // sync failed, should not show up
            p("7", true, false));
    assertThat(checkForNewVersions(currentVersion, packages)).containsExactly("2", "3", "7");
    packages =
        packages(
            p("1", true, false),
            p("1", true, false),
            p("2", true, false),
            p("3", true, false),
            p("4", false, false), // sync not completed, should not show up
            p("7", true, false));
    assertThat(checkForNewVersions(currentVersion, packages)).containsExactly("2", "3", "7");

    packages =
        packages(
            p("1:23.1", true, false),
            p("1:23.1", true, false),
            p("1:23.2", true, false),
            p("1:23.3", true, false),
            p("1:23.4", true, false),
            p("1:23.7", true, false));
    currentVersion = "1:23.2";
    assertThat(checkForNewVersions(currentVersion, packages))
        .containsExactly("1:23.2", "1:23.3", "1:23.4", "1:23.7");
    assertThat(checkForNewVersions(null, packages))
        .containsExactly("1:23.1", "1:23.2", "1:23.3", "1:23.4", "1:23.7");
  }

  @Test
  void globsPredicateShouldFilterFiles() {
    List<String> files = Arrays.asList("data.txt", "data.foo", "data.dat", "data.bar");
    assertThat(globPredicate("*.txt,*.dat").test(p("data.txt"))).isTrue();
    assertThat(globPredicate("*.txt,*.dat").test(p("data.foo"))).isFalse();
    assertThat(globPredicate("*.txt,*.dat").test(p("data.dat"))).isTrue();
    assertThat(globPredicate("*.txt,*.dat").test(p("data.bar"))).isFalse();

    assertThat(globPredicate("*.txt").test(p("data.txt"))).isTrue();
    assertThat(globPredicate("*.txt").test(p("data.foo"))).isFalse();
    assertThat(globPredicate("*.txt").test(p("data.dat"))).isFalse();
    assertThat(globPredicate("*.txt").test(p("data.bar"))).isFalse();
  }
}
