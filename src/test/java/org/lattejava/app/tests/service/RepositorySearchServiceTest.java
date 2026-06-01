/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module java.base;
import module org.lattejava.app;
import module org.testng;

import static org.testng.Assert.*;

/**
 * Unit coverage for {@link RepositorySearchService}'s pure helpers — the Java port of the repository-search Worker's
 * {@code repository.js} and {@code version.js}. Mirrors the original Vitest cases (prefix conversion, version
 * extraction, descending sort with pre-release ordering).
 */
@Test
public class RepositorySearchServiceTest {
  @Test
  public void artifactIdToPrefix_emptyString_empty() {
    assertTrue(RepositorySearchService.artifactIdToPrefix("").isEmpty());
  }

  @Test
  public void artifactIdToPrefix_noColon_empty() {
    assertTrue(RepositorySearchService.artifactIdToPrefix("invalid").isEmpty());
  }

  @Test
  public void artifactIdToPrefix_null_empty() {
    assertTrue(RepositorySearchService.artifactIdToPrefix(null).isEmpty());
  }

  @Test
  public void artifactIdToPrefix_simpleGroup_convertsToPrefix() {
    assertEquals(RepositorySearchService.artifactIdToPrefix("com.example:mylib").orElseThrow(), "com/example/mylib/");
  }

  @Test
  public void artifactIdToPrefix_validId_convertsToPrefix() {
    assertEquals(RepositorySearchService.artifactIdToPrefix("org.lattejava.plugin:dependency").orElseThrow(),
        "org/lattejava/plugin/dependency/");
  }

  @Test
  public void extractVersions_noMatches_empty() {
    assertEquals(RepositorySearchService.extractVersions(List.of(), "org/example/lib/"), List.of());
  }

  @Test
  public void extractVersions_uniqueVersionsFromKeys() {
    String prefix = "org/lattejava/plugin/dependency/";
    List<String> keys = List.of(
        "org/lattejava/plugin/dependency/0.1.0/dependency-0.1.0.jar",
        "org/lattejava/plugin/dependency/0.1.0/dependency-0.1.0.jar.amd",
        "org/lattejava/plugin/dependency/0.1.1/dependency-0.1.1.jar",
        "org/lattejava/plugin/dependency/0.1.2/dependency-0.1.2.jar"
    );
    assertEquals(RepositorySearchService.extractVersions(keys, prefix), List.of("0.1.0", "0.1.1", "0.1.2"));
  }

  @Test
  public void sortVersionsDescending_filtersUnparseable() {
    assertEquals(RepositorySearchService.sortVersionsDescending(List.of("1.0.0", "garbage", "0.1.0")),
        List.of("1.0.0", "0.1.0"));
  }

  @Test
  public void sortVersionsDescending_highestFirst() {
    assertEquals(RepositorySearchService.sortVersionsDescending(List.of("0.1.0", "1.0.0", "0.2.0", "0.1.1")),
        List.of("1.0.0", "0.2.0", "0.1.1", "0.1.0"));
  }

  @Test
  public void sortVersionsDescending_preReleaseSortsBelowRelease() {
    assertEquals(RepositorySearchService.sortVersionsDescending(List.of("1.0.0", "1.0.0-beta")),
        List.of("1.0.0", "1.0.0-beta"));
  }

  @Test
  public void sortVersionsDescending_twoSegmentTreatedAsPatchZero() {
    // "1.2" parses as 1.2.0, so it sorts above 1.1.9 and below 1.2.1.
    assertEquals(RepositorySearchService.sortVersionsDescending(List.of("1.1.9", "1.2", "1.2.1")),
        List.of("1.2.1", "1.2", "1.1.9"));
  }
}
