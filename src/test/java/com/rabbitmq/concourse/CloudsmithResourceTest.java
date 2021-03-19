package com.rabbitmq.concourse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
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
  }

  @Test
  void selectedFilesForUploadShouldSelectFilesThatMatchGlobs(@TempDir Path inputDirectory)
      throws IOException {
    newFiles(inputDirectory, "test1.txt", "test2.txt", "test.bin", "data.dat");
    assertThat(selectFilesForUpload(inputDirectory, "*.txt", "*.dat"))
        .hasSize(3)
        .satisfies(filenames("test1.txt", "test2.txt", "data.dat"));
  }

  static Set<String> selectFilesForUpload(Path inputDirectory, String... globs) throws IOException {
    return CloudsmithResource.selectFilesForUpload(
        inputDirectory.toFile().getAbsolutePath(), globs);
  }
}
