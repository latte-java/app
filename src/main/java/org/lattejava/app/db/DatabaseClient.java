/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module com.fasterxml.jackson.databind;
import module java.base;
import module java.net.http;
import module org.lattejava.app;

import org.lattejava.app.model.Member;
import org.lattejava.web.*;

public class DatabaseClient {
  private final String apiToken;
  private final HttpClient httpClient;
  private final ObjectMapper mapper;
  private final String queryURL;

  public DatabaseClient(Configuration config) {
    this.apiToken = config.get("d1.apiToken");
    this.httpClient = HttpClient.newHttpClient();
    this.mapper = new ObjectMapper();
    this.queryURL = config.get("d1.baseUrl") + "/accounts/" + config.get("d1.accountId")
        + "/d1/database/" + config.get("d1.databaseId") + "/query";
  }

  private static String escapeLikePattern(String input) {
    return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Group rowToGroup(Map<String, Object> row) {
    return new Group(
        D1Tools.requiredString(row, "name"),
        D1Tools.requiredString(row, "description"),
        D1Tools.requiredEnum(row, "state", GroupState.class),
        D1Tools.optionalString(row, "verification_code"),
        D1Tools.requiredInstant(row, "created_at"),
        D1Tools.optionalInstant(row, "verified_at")
    );
  }

  private static Member rowToMember(Map<String, Object> row) {
    return new Member(
        D1Tools.requiredString(row, "group_name"),
        D1Tools.requiredUUID(row, "user_id"),
        D1Tools.requiredEnum(row, "role", Role.class),
        D1Tools.requiredEnum(row, "state", MembershipState.class),
        D1Tools.optionalUUID(row, "invited_by"),
        D1Tools.optionalInstant(row, "invited_at"),
        D1Tools.optionalInstant(row, "joined_at")
    );
  }

  private static GroupVerification rowToVerification(Map<String, Object> row) {
    return new GroupVerification(
        D1Tools.requiredString(row, "group_name"),
        D1Tools.requiredInstant(row, "started_at"),
        D1Tools.optionalInstant(row, "last_checked_at")
    );
  }

  private static List<Member> toMembers(D1Response response) {
    List<Map<String, Object>> rows = response.result().getFirst().results();
    List<Member> members = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      members.add(rowToMember(row));
    }
    return members;
  }

  public void deleteGroup(String name) {
    query("DELETE FROM groups WHERE name = ?", name);
  }

  public void deleteMember(String groupName, UUID userId) {
    query("DELETE FROM members WHERE group_name = ? AND user_id = ?", groupName, userId.toString());
  }

  public void deleteVerification(String groupName) {
    query("DELETE FROM group_verifications WHERE group_name = ?", groupName);
  }

  public List<Member> findActiveOwners(String groupName) {
    D1Response response = query(
        "SELECT group_name, user_id, role, state, invited_by, invited_at, joined_at FROM members WHERE group_name = ? AND role = 'OWNER' AND state = 'ACTIVE'",
        groupName
    );
    return toMembers(response);
  }

  public Optional<Group> findAncestorGroup(String name) {
    String[] segments = name.split("\\.");
    if (segments.length < 2) {
      return Optional.empty();
    }
    List<String> prefixes = new ArrayList<>();
    for (int i = 1; i < segments.length; i++) {
      prefixes.add(String.join(".", Arrays.copyOfRange(segments, 0, i)));
    }
    String placeholders = String.join(",", Collections.nCopies(prefixes.size(), "?"));
    D1Response response = query(
        "SELECT name, description, state, verification_code, created_at, verified_at FROM groups WHERE name IN (" + placeholders + ") LIMIT 1",
        prefixes.toArray()
    );
    List<Map<String, Object>> rows = response.result().getFirst().results();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rowToGroup(rows.getFirst()));
  }

  public Optional<Group> findGroup(String name) {
    D1Response response = query("SELECT name, description, state, verification_code, created_at, verified_at FROM groups WHERE name = ?", name);
    List<Map<String, Object>> rows = response.result().getFirst().results();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rowToGroup(rows.getFirst()));
  }

  public Optional<Member> findMember(String groupName, UUID userId) {
    D1Response response = query(
        "SELECT group_name, user_id, role, state, invited_by, invited_at, joined_at FROM members WHERE group_name = ? AND user_id = ?",
        groupName,
        userId.toString()
    );
    List<Map<String, Object>> rows = response.result().getFirst().results();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rowToMember(rows.getFirst()));
  }

  public Optional<GroupVerification> findVerification(String groupName) {
    D1Response response = query(
        "SELECT group_name, started_at, last_checked_at FROM group_verifications WHERE group_name = ?",
        groupName
    );
    List<Map<String, Object>> rows = response.result().getFirst().results();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rowToVerification(rows.getFirst()));
  }

  public void insertGroup(Group group) {
    query(
        "INSERT INTO groups (name, description, state, verification_code, created_at, verified_at) VALUES (?, ?, ?, ?, ?, ?)",
        group.name(),
        group.description(),
        group.state().name(),
        group.verificationCode(),
        group.createdAt().toEpochMilli(),
        group.verifiedAt() == null ? null : group.verifiedAt().toEpochMilli()
    );
  }

  public void insertMember(Member member) {
    query(
        "INSERT INTO members (group_name, user_id, role, state, invited_by, invited_at, joined_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        member.groupName(),
        member.userId().toString(),
        member.role().name(),
        member.state().name(),
        member.invitedBy() == null ? null : member.invitedBy().toString(),
        member.invitedAt() == null ? null : member.invitedAt().toEpochMilli(),
        member.joinedAt() == null ? null : member.joinedAt().toEpochMilli()
    );
  }

  public void insertVerification(GroupVerification verification) {
    query(
        "INSERT INTO group_verifications (group_name, started_at, last_checked_at) VALUES (?, ?, ?)",
        verification.groupName(),
        verification.startedAt().toEpochMilli(),
        verification.lastCheckedAt() == null ? null : verification.lastCheckedAt().toEpochMilli()
    );
  }

  public List<Group> listGroupsForUser(UUID userId) {
    return listGroupsForUser(userId, null);
  }

  /**
   * Lists the groups for {@code userId}, optionally narrowing by a case-insensitive substring match against
   * {@code groups.name}. {@code %} and {@code _} in {@code filter} are escaped so they match literally.
   *
   * @param userId The user whose memberships drive the result.
   * @param filter A substring to match against group names. May be {@code null} or blank, in which case all
   *               memberships are returned.
   * @return The matching groups in no defined order.
   */
  public List<Group> listGroupsForUser(UUID userId, String filter) {
    String baseSQL = "SELECT g.name, g.description, g.state, g.verification_code, g.created_at, g.verified_at "
        + "FROM groups g JOIN members m ON m.group_name = g.name WHERE m.user_id = ?";
    D1Response response;
    if (filter == null || filter.isBlank()) {
      response = query(baseSQL, userId.toString());
    } else {
      String pattern = "%" + escapeLikePattern(filter) + "%";
      response = query(baseSQL + " AND g.name LIKE ? ESCAPE '\\'", userId.toString(), pattern);
    }
    List<Map<String, Object>> rows = response.result().getFirst().results();
    List<Group> groups = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      groups.add(rowToGroup(row));
    }
    return groups;
  }

  public List<Member> listMembers(String groupName) {
    D1Response response = query(
        "SELECT group_name, user_id, role, state, invited_by, invited_at, joined_at FROM members WHERE group_name = ?",
        groupName
    );
    return toMembers(response);
  }

  public List<GroupVerification> listVerificationsDueForCheck(Instant lastCheckedAtBefore) {
    D1Response response = query(
        "SELECT group_name, started_at, last_checked_at FROM group_verifications WHERE last_checked_at IS NULL OR last_checked_at <= ?",
        lastCheckedAtBefore.toEpochMilli()
    );
    List<Map<String, Object>> rows = response.result().getFirst().results();
    List<GroupVerification> verifications = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      verifications.add(rowToVerification(row));
    }
    return verifications;
  }

  public D1Response query(String sql, Object... params) {
    String requestBody;
    try {
      requestBody = mapper.writeValueAsString(new D1Request(sql, Arrays.asList(params)));
    } catch (JsonProcessingException e) {
      throw new D1Exception("Failed to serialize D1 request for SQL [" + sql + "]", e);
    }

    HttpRequest request = HttpRequest.newBuilder(URI.create(queryURL))
                                     .timeout(Duration.ofSeconds(30))
                                     .header("Authorization", "Bearer " + apiToken)
                                     .header("Content-Type", "application/json")
                                     .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                     .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new D1Exception("D1 request failed for SQL [" + sql + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new D1Exception("D1 request interrupted for SQL [" + sql + "]", e);
    }

    D1Response payload;
    try {
      payload = mapper.readValue(response.body(), D1Response.class);
    } catch (IOException e) {
      throw new D1Exception("Failed to parse D1 response for SQL [" + sql + "] (status [" + response.statusCode() + "])", e);
    }

    if (!payload.success()) {
      String detail = payload.errors() == null || payload.errors().isEmpty()
          ? "no error detail; HTTP status [" + response.statusCode() + "]"
          : payload.errors().stream().map(D1Error::message).collect(Collectors.joining("; "));
      throw new D1Exception("D1 query failed [" + sql + "]: [" + detail + "]");
    }
    return payload;
  }

  public void updateGroupDescription(String name, String description) {
    query(
        "UPDATE groups SET description = ? WHERE name = ?",
        description,
        name
    );
  }

  public void updateGroupState(String name, GroupState state, Instant verifiedAt) {
    query(
        "UPDATE groups SET state = ?, verified_at = ? WHERE name = ?",
        state.name(),
        verifiedAt == null ? null : verifiedAt.toEpochMilli(),
        name
    );
  }

  public void updateMemberRole(String groupName, UUID userId, Role role) {
    query(
        "UPDATE members SET role = ? WHERE group_name = ? AND user_id = ?",
        role.name(),
        groupName,
        userId.toString()
    );
  }

  public void updateMemberState(String groupName, UUID userId, MembershipState state, Instant joinedAt) {
    query(
        "UPDATE members SET state = ?, joined_at = ? WHERE group_name = ? AND user_id = ?",
        state.name(),
        joinedAt == null ? null : joinedAt.toEpochMilli(),
        groupName,
        userId.toString()
    );
  }

  public void updateVerificationLastChecked(String groupName, Instant lastCheckedAt) {
    query(
        "UPDATE group_verifications SET last_checked_at = ? WHERE group_name = ?",
        lastCheckedAt.toEpochMilli(),
        groupName
    );
  }
}
