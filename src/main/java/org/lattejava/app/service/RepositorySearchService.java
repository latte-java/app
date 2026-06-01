/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.model.RepositorySearchResponse;
import org.lattejava.app.s3.S3Client;
import org.lattejava.app.s3.S3HttpClient;
import org.lattejava.app.service.validation.RepositorySearchValidator;
import org.lattejava.app.service.validation.ValidationException;
import org.lattejava.web.Configuration;

/**
 * Searches the artifact repository (the S3-compatible bucket) for the versions of a given artifact. This is the Java
 * port of the former Cloudflare {@code repository-search} Worker: an artifact id ({@code group:project}) is converted to
 * an object-key prefix, the bucket is listed under that prefix, the first path segment of each key is taken as a
 * version, and the unique versions are sorted newest first. The version parsing and ordering rules match the Worker's
 * {@code version.js} exactly (major.minor[.patch][-preRelease]; pre-release sorts below the same release).
 *
 * @author Brian Pontarelli
 */
public class RepositorySearchService {
  private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(.+))?$");
  private final S3Client s3Client;
  private final RepositorySearchValidator validator;

  public RepositorySearchService(Configuration config) {
    this(new RepositorySearchValidator(), new S3HttpClient(config));
  }

  /**
   * Test-only constructor. Production code should use the {@link #RepositorySearchService(Configuration)} constructor.
   */
  public RepositorySearchService(RepositorySearchValidator validator, S3Client s3Client) {
    this.s3Client = s3Client;
    this.validator = validator;
  }

  /**
   * Converts a Latte artifact id (e.g. {@code org.lattejava.plugin:dependency}) to its R2/S3 key prefix (e.g.
   * {@code org/lattejava/plugin/dependency/}). Returns empty when the id is not a valid {@code group:project} pair.
   *
   * @param id The artifact id.
   * @return The key prefix, or empty when the id is invalid.
   */
  public static Optional<String> artifactIdToPrefix(String id) {
    if (id == null) {
      return Optional.empty();
    }

    String[] parts = id.split(":");
    if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(parts[0].replace(".", "/") + "/" + parts[1] + "/");
  }

  /**
   * Extracts the unique version strings (the first path segment after {@code prefix}) from a list of object keys.
   *
   * @param keys   The object keys returned from the bucket.
   * @param prefix The artifact prefix the keys were listed under.
   * @return The unique versions, in first-seen order.
   */
  public static List<String> extractVersions(List<String> keys, String prefix) {
    Set<String> versions = new LinkedHashSet<>();
    for (String key : keys) {
      if (!key.startsWith(prefix)) {
        continue;
      }

      String rest = key.substring(prefix.length());
      int slash = rest.indexOf('/');
      String version = slash < 0 ? rest : rest.substring(0, slash);
      if (!version.isEmpty()) {
        versions.add(version);
      }
    }

    return new ArrayList<>(versions);
  }

  /**
   * Sorts version strings in descending order (highest first), dropping any that cannot be parsed.
   *
   * @param versions The versions to sort.
   * @return The parseable versions, newest first.
   */
  public static List<String> sortVersionsDescending(Collection<String> versions) {
    return versions.stream()
                   .filter(v -> parseVersion(v) != null)
                   .sorted((a, b) -> compareVersions(b, a))
                   .collect(Collectors.toList());
  }

  /**
   * Searches the repository for the versions of {@code id}.
   *
   * @param id     The artifact id ({@code group:project}).
   * @param latest When true, return only the single newest version.
   * @return The response, or empty when the artifact has no versions in the bucket (a 404).
   * @throws ValidationException When {@code id} is missing or not a valid artifact id.
   * @throws org.lattejava.app.s3.S3Exception When the bucket cannot be listed.
   */
  public Optional<RepositorySearchResponse> search(String id, boolean latest) {
    Errors errors = validator.validate(id);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    String prefix = artifactIdToPrefix(id).orElseThrow();
    List<String> extracted = extractVersions(s3Client.listKeys(prefix), prefix);
    if (extracted.isEmpty()) {
      return Optional.empty();
    }

    List<String> sorted = sortVersionsDescending(extracted);
    List<String> versions = latest ? (sorted.isEmpty() ? List.of() : List.of(sorted.getFirst())) : sorted;
    return Optional.of(new RepositorySearchResponse(id, versions));
  }

  static int compareVersions(String a, String b) {
    Version pa = parseVersion(a);
    Version pb = parseVersion(b);
    if (pa == null || pb == null) {
      return 0;
    }

    if (pa.major() != pb.major()) {
      return Integer.compare(pa.major(), pb.major());
    }
    if (pa.minor() != pb.minor()) {
      return Integer.compare(pa.minor(), pb.minor());
    }
    if (pa.patch() != pb.patch()) {
      return Integer.compare(pa.patch(), pb.patch());
    }

    // Both pre-release: compare lexically. Otherwise a pre-release sorts below the same release version.
    if (pa.preRelease() != null && pb.preRelease() != null) {
      return pa.preRelease().compareTo(pb.preRelease());
    }
    if (pa.preRelease() != null) {
      return -1;
    }
    if (pb.preRelease() != null) {
      return 1;
    }

    return 0;
  }

  static Version parseVersion(String version) {
    if (version == null) {
      return null;
    }

    Matcher matcher = VERSION.matcher(version);
    if (!matcher.matches()) {
      return null;
    }

    int major = Integer.parseInt(matcher.group(1));
    int minor = Integer.parseInt(matcher.group(2));
    int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
    return new Version(major, minor, patch, matcher.group(4));
  }

  record Version(int major, int minor, int patch, String preRelease) {
  }
}
