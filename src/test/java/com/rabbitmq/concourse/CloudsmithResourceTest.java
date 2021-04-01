package com.rabbitmq.concourse;

import static com.rabbitmq.concourse.CloudsmithResource.CloudsmithPackageAccess.uploadJsonBody;
import static com.rabbitmq.concourse.CloudsmithResource.extractVersion;
import static com.rabbitmq.concourse.CloudsmithResource.filterForDeletion;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    List<String> versions =
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
            "1:22.3.4.9-1");
    Collections.shuffle(versions);
    assertThat(filterForDeletion(versions, 2))
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

    assertThat(filterForDeletion(versions, versions.size() - 1))
        .hasSize(1)
        .containsExactly("1:22.0-1");

    assertThat(filterForDeletion(versions, 0)).hasSameSizeAs(versions).hasSameElementsAs(versions);

    assertThat(filterForDeletion(versions, versions.size() + 1)).isEmpty();

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
            "22.3.4.7-1.el8");
    Collections.shuffle(versions);
    assertThat(filterForDeletion(versions, 2))
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

    assertThat(filterForDeletion(versions, versions.size() - 1))
        .hasSize(1)
        .containsExactly("22.2.4-1.el8");

    assertThat(filterForDeletion(versions, 0)).hasSameSizeAs(versions).hasSameElementsAs(versions);

    assertThat(filterForDeletion(versions, versions.size() + 1)).isEmpty();
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
}
