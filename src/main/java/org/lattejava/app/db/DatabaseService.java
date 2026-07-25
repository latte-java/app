/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.database;

import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.lattejava.app.db.jooq.tables.records.GroupVerificationsRecord;
import org.lattejava.app.db.jooq.tables.records.GroupsRecord;
import org.lattejava.app.db.jooq.tables.records.MembersRecord;
import org.lattejava.app.model.Member;
import org.lattejava.web.Configuration;

import static org.lattejava.app.db.jooq.Tables.GROUPS;
import static org.lattejava.app.db.jooq.Tables.GROUP_VERIFICATIONS;
import static org.lattejava.app.db.jooq.Tables.MEMBERS;

/**
 * PostgreSQL-backed data access, implemented with jOOQ over a HikariCP connection pool. This service owns the
 * persistence setup entirely — it builds the {@link DataSource} and {@link DSLContext} from {@code db.*} config and
 * applies any pending SQL migrations (classpath {@code db/*.sql}) at construction time — so the rest of the
 * application never touches connections or the persistence technology directly.
 *
 * <p>Enum columns ({@code state}, {@code role}) and the epoch-millis BIGINT timestamp columns are mapped to the Java
 * enums and {@link Instant} by jOOQ forced-type converters generated into the {@code org.lattejava.app.db.jooq}
 * package, so the typed fields used below carry the domain types directly.
 */
public class DatabaseService {
  private static final System.Logger LOG = System.getLogger(DatabaseService.class.getName());
  private final HikariDataSource dataSource;
  private final DSLContext dsl;

  public DatabaseService(Configuration config) {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(config.get("db.url"));
    hikariConfig.setUsername(config.get("db.username"));
    hikariConfig.setPassword(config.get("db.password"));
    hikariConfig.setPoolName("latte-app");
    // Pool sizing and liveness tuning (maxLifetime/keepalive) for PlanetScale are intentionally left at
    // HikariCP defaults until observed concurrency justifies specific values — see the migration design doc.
    this.dataSource = new HikariDataSource(hikariConfig);

    // Apply any pending SQL migrations (classpath db/*.sql) before anything else touches the schema
    try (Connection connection = dataSource.getConnection()) {
      var applied = new Migrator(connection, "db").migrate();
      if (!applied.isEmpty()) {
        LOG.log(System.Logger.Level.INFO, "Applied database migrations " + applied);
      }
    } catch (MigrationException | SQLException e) {
      throw new IllegalStateException("Unable to migrate the database", e);
    }

    this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
  }

  private static Group toGroup(GroupsRecord r) {
    return new Group(r.getName(), r.getDescription(), r.getState(), r.getVerificationCode(), r.getCreatedAt(), r.getVerifiedAt());
  }

  private static Member toMember(MembersRecord r) {
    return new Member(
        r.getGroupName(),
        r.getUserId(),
        r.getRole(),
        r.getState(),
        r.getInvitedBy(),
        r.getInvitedAt(),
        r.getJoinedAt()
    );
  }

  private static GroupVerification toVerification(GroupVerificationsRecord r) {
    return new GroupVerification(r.getGroupName(), r.getStartedAt(), r.getLastCheckedAt());
  }

  /**
   * Closes the underlying connection pool. Invoked from {@code Services.shutdown()}.
   */
  public void close() {
    dataSource.close();
  }

  public void deleteGroup(String name) {
    dsl.deleteFrom(GROUPS).where(GROUPS.NAME.eq(name)).execute();
  }

  public void deleteMember(String groupName, UUID userId) {
    dsl.deleteFrom(MEMBERS)
       .where(MEMBERS.GROUP_NAME.eq(groupName))
       .and(MEMBERS.USER_ID.eq(userId))
       .execute();
  }

  public void deleteVerification(String groupName) {
    dsl.deleteFrom(GROUP_VERIFICATIONS).where(GROUP_VERIFICATIONS.GROUP_NAME.eq(groupName)).execute();
  }

  public List<Member> findActiveOwners(String groupName) {
    return dsl.selectFrom(MEMBERS)
              .where(MEMBERS.GROUP_NAME.eq(groupName))
              .and(MEMBERS.ROLE.eq(Role.OWNER))
              .and(MEMBERS.STATE.eq(MembershipState.ACTIVE))
              .fetch(DatabaseService::toMember);
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
    return dsl.selectFrom(GROUPS)
              .where(GROUPS.NAME.in(prefixes))
              .limit(1)
              .fetchOptional(DatabaseService::toGroup);
  }

  public Optional<Group> findGroup(String name) {
    return dsl.selectFrom(GROUPS).where(GROUPS.NAME.eq(name)).fetchOptional(DatabaseService::toGroup);
  }

  public Optional<Member> findMember(String groupName, UUID userId) {
    return dsl.selectFrom(MEMBERS)
              .where(MEMBERS.GROUP_NAME.eq(groupName))
              .and(MEMBERS.USER_ID.eq(userId))
              .fetchOptional(DatabaseService::toMember);
  }

  /**
   * Returns the most specific (longest-named) registered group among {@code candidates}, or empty if none of them is
   * registered. Used to find the group that owns an artifact namespace.
   *
   * @param candidates The candidate group names (the namespace itself plus its ancestors).
   * @return The owning group, or empty.
   */
  public Optional<Group> findOwningGroup(List<String> candidates) {
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    return dsl.selectFrom(GROUPS)
              .where(GROUPS.NAME.in(candidates))
              .orderBy(DSL.length(GROUPS.NAME).desc())
              .limit(1)
              .fetchOptional(DatabaseService::toGroup);
  }

  public Optional<GroupVerification> findVerification(String groupName) {
    return dsl.selectFrom(GROUP_VERIFICATIONS)
              .where(GROUP_VERIFICATIONS.GROUP_NAME.eq(groupName))
              .fetchOptional(DatabaseService::toVerification);
  }

  public void insertGroup(Group group) {
    dsl.insertInto(GROUPS)
       .set(GROUPS.NAME, group.name())
       .set(GROUPS.DESCRIPTION, group.description())
       .set(GROUPS.STATE, group.state())
       .set(GROUPS.VERIFICATION_CODE, group.verificationCode())
       .set(GROUPS.CREATED_AT, group.createdAt())
       .set(GROUPS.VERIFIED_AT, group.verifiedAt())
       .execute();
  }

  public void insertMember(Member member) {
    dsl.insertInto(MEMBERS)
       .set(MEMBERS.GROUP_NAME, member.groupName())
       .set(MEMBERS.USER_ID, member.userId())
       .set(MEMBERS.ROLE, member.role())
       .set(MEMBERS.STATE, member.state())
       .set(MEMBERS.INVITED_BY, member.invitedBy())
       .set(MEMBERS.INVITED_AT, member.invitedAt())
       .set(MEMBERS.JOINED_AT, member.joinedAt())
       .execute();
  }

  public void insertVerification(GroupVerification verification) {
    dsl.insertInto(GROUP_VERIFICATIONS)
       .set(GROUP_VERIFICATIONS.GROUP_NAME, verification.groupName())
       .set(GROUP_VERIFICATIONS.STARTED_AT, verification.startedAt())
       .set(GROUP_VERIFICATIONS.LAST_CHECKED_AT, verification.lastCheckedAt())
       .execute();
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
    Condition where = MEMBERS.USER_ID.eq(userId);
    if (filter != null && !filter.isBlank()) {
      where = where.and(GROUPS.NAME.containsIgnoreCase(filter));
    }
    return dsl.select(GROUPS.NAME, GROUPS.DESCRIPTION, GROUPS.STATE, GROUPS.VERIFICATION_CODE, GROUPS.CREATED_AT, GROUPS.VERIFIED_AT)
              .from(GROUPS)
              .join(MEMBERS).on(MEMBERS.GROUP_NAME.eq(GROUPS.NAME))
              .where(where)
              .fetch(Records.mapping(Group::new));
  }

  public List<Member> listMembers(String groupName) {
    return dsl.selectFrom(MEMBERS)
              .where(MEMBERS.GROUP_NAME.eq(groupName))
              .fetch(DatabaseService::toMember);
  }

  public List<GroupVerification> listVerificationsDueForCheck(Instant lastCheckedAtBefore) {
    return dsl.selectFrom(GROUP_VERIFICATIONS)
              .where(GROUP_VERIFICATIONS.LAST_CHECKED_AT.isNull().or(GROUP_VERIFICATIONS.LAST_CHECKED_AT.le(lastCheckedAtBefore)))
              .fetch(DatabaseService::toVerification);
  }

  public void updateGroupDescription(String name, String description) {
    dsl.update(GROUPS).set(GROUPS.DESCRIPTION, description).where(GROUPS.NAME.eq(name)).execute();
  }

  public void updateGroupState(String name, GroupState state, Instant verifiedAt) {
    dsl.update(GROUPS)
       .set(GROUPS.STATE, state)
       .set(GROUPS.VERIFIED_AT, verifiedAt)
       .where(GROUPS.NAME.eq(name))
       .execute();
  }

  public void updateMemberRole(String groupName, UUID userId, Role role) {
    dsl.update(MEMBERS)
       .set(MEMBERS.ROLE, role)
       .where(MEMBERS.GROUP_NAME.eq(groupName))
       .and(MEMBERS.USER_ID.eq(userId))
       .execute();
  }

  public void updateMemberState(String groupName, UUID userId, MembershipState state, Instant joinedAt) {
    dsl.update(MEMBERS)
       .set(MEMBERS.STATE, state)
       .set(MEMBERS.JOINED_AT, joinedAt)
       .where(MEMBERS.GROUP_NAME.eq(groupName))
       .and(MEMBERS.USER_ID.eq(userId))
       .execute();
  }

  public void updateVerificationLastChecked(String groupName, Instant lastCheckedAt) {
    dsl.update(GROUP_VERIFICATIONS)
       .set(GROUP_VERIFICATIONS.LAST_CHECKED_AT, lastCheckedAt)
       .where(GROUP_VERIFICATIONS.GROUP_NAME.eq(groupName))
       .execute();
  }
}
