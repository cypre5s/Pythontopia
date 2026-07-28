package com.pythontopia.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FlywayMigrationVersionUniquenessTest {

  private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V(\\d+)__.+\\.sql$");

  @Test
  void versionedMigrationsHaveUniqueVersions() throws IOException {
    Map<String, List<String>> filesByVersion;
    try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
      filesByVersion =
          files
              .map(path -> path.getFileName().toString())
              .map(VERSIONED_MIGRATION::matcher)
              .filter(Matcher::matches)
              .collect(
                  Collectors.groupingBy(
                      matcher -> matcher.group(1),
                      TreeMap::new,
                      Collectors.mapping(matcher -> matcher.group(0), Collectors.toList())));
    }

    assertThat(filesByVersion)
        .allSatisfy(
            (version, files) ->
                assertThat(files).as("Flyway version V%s", version).hasSize(1));
  }
}
