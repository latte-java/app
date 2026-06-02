# PostgreSQL + jOOQ Migration

**Date:** 2026-06-02
**Status:** Approved — ready for implementation planning

## Summary

Replace the Cloudflare D1 (SQLite-over-REST) persistence layer with PostgreSQL accessed through
[jOOQ](https://www.jooq.org/). The domain model (immutable Java records), the service/controller layers, and the
public method surface of the data-access class are preserved; only the persistence implementation and its wiring
change.

## Motivation

1. **Portability — run without Cloudflare.** D1 is a Cloudflare-only managed service reached over a REST API. Anyone
   running this app today needs a Cloudflare account and a personal D1 database. Moving to PostgreSQL lets the app run
   against any standard Postgres (local, self-hosted, or any managed provider).
2. **Local testing without a remote database.** D1 has no local emulator, so the test suite currently issues
   `DELETE`/`INSERT` against a real remote database. PostgreSQL runs locally (Homebrew), so tests hit a local DB.
3. **Optics.** PostgreSQL + a real data-access library reads better than a hand-rolled SQLite-over-HTTP client.

Production continues to deploy on Cloudflare containers (`wrangler deploy`); only the database moves off Cloudflare,
to **PlanetScale (Postgres)** for now. The container reaches PlanetScale over the network.

## Library selection — jOOQ

The choice was made after an exhaustive review of the 2024–2026 Java data-layer landscape (jOOQ, JDBI 3, Hibernate
ORM 6/7, Spring Data JDBC/R2DBC, Micronaut Data, Quarkus Panache, Ebean, MyBatis). Against this project's hard
constraints — Java 25, JPMS-strict (`module-info`), immutable records as the domain model, normalization in compact
constructors, and an existing SQL-first style — the realistic finalists were **jOOQ** and **JDBI 3**.

- **Hibernate/JPA** was rejected: entities cannot be records (needs mutable classes, no-arg constructors, proxies),
  it is the worst JPMS citizen, and Hibernate ORM 7 targets Java 17/21/23, not 25.
- **Micronaut Data** does records + compile-time SQL well but only inside the Micronaut framework; this app uses its
  own `org.lattejava.web`, so it is a non-starter.
- **Reactive (R2DBC, Vert.x SQL)** offers no benefit on Java 25: with virtual threads, plain blocking JDBC is
  competitive and far simpler. Both finalists are blocking-JDBC libraries — the right call here.

**jOOQ** was chosen over JDBI for **compile-time-checked SQL**: queries are composed from typed references generated
from the real schema, so a column rename or type change breaks the build rather than failing at runtime. This matters
most for this codebase's dynamic queries (optional filters, dynamic `IN` lists, ancestor/owning-group resolution).
The cost is a code-generation step and a larger runtime jar (~8 MB vs JDBI's ~1.2 MB) — acceptable for a container
deployment, and jOOQ's largest pieces (codegen) are build-only and never bundled.

## Schema

Three tables move from SQLite to PostgreSQL: `groups`, `members`, `group_verifications` (unchanged in shape).

### Timestamps — `BIGINT` epoch-millis (not `timestamptz`)

All six timestamp columns (`groups.created_at`, `groups.verified_at`, `members.invited_at`, `members.joined_at`,
`group_verifications.started_at`, `group_verifications.last_checked_at`) remain **epoch-millis stored as `BIGINT`**
(Postgres `INTEGER` is 32-bit and would overflow; epoch-millis needs 64-bit). The Java model stays `Instant`.

Rationale (this was deliberately argued, not assumed): `timestamptz` and `BIGINT` are both 8 bytes; both require one
jOOQ forced-type converter to reach `Instant` (jOOQ defaults `timestamptz` → `OffsetDateTime`, so `timestamptz` does
*not* avoid a converter). `timestamptz`'s only real advantage is human-readable values in a bare `SELECT *`, which is
fully recoverable on demand via `to_timestamp(col / 1000.0)` or a convenience view. `BIGINT` epoch-millis wins on the
project's actual priorities: maximal cross-engine portability (a plain integer is identical everywhere), a literal
copy when migrating existing D1 data, zero timezone/DST semantics, and the smallest change from today's schema.

### Enums — `TEXT` + `CHECK` (not native `ENUM`)

`groups.state`, `members.role`, and `members.state` remain `TEXT` with `CHECK (... IN (...))` constraints, mapped to
the existing Java enums (`GroupState`, `Role`, `MembershipState`) via jOOQ forced-type `EnumConverter`s.

Rationale: native Postgres `ENUM`'s only real wins (4-byte storage, intrinsic ordering) are irrelevant at this scale,
while its costs hit project priorities directly — it is non-portable (`CREATE TYPE` is Postgres-specific) and value
removal/rename is painful, whereas a `CHECK` constraint evolves with a one-line `DROP/ADD CONSTRAINT`. On the jOOQ
side, a native `ENUM` makes jOOQ auto-generate a parallel Java enum that duplicates the domain enums, so it is *more*
work, not less. The `CHECK` is kept (not plain `TEXT`) as cheap, standard-SQL defense against bad values from any
non-app writer.

### Schema as source of truth

The schema lives as plain SQL in **`src/main/sql/schema.sql`**; the reserved-group seed (today's migration `0002`,
the `org.lattejava` group) lives in **`src/main/sql/seed.sql`**. **No migration tool** is adopted now (Flyway and
Liquibase were both judged too heavy for a 3-table schema); version tracking is deferred until actually needed. When
migrations are added later, `schema.sql` becomes the first migration and a tool slots in with no other change. The
`database` build plugin already exposes a Liquibase-backed `compare`/`ensureEqual` (build-time only) that can later
verify migrations reproduce `schema.sql`.

## Code-generation (jOOQ)

jOOQ's standalone `GenerationTool` (not an annotation processor) generates typed classes from the schema. Because a
live local Postgres with the schema loaded is always available (provisioned by the `database` plugin) and the
generated classes are **committed**, codegen introspects the **live dev database** via `JDBCDatabase` — reading the
real Postgres catalog rather than relying on jOOQ's SQL parser against the DDL file.

- **Output:** committed into the existing package `src/main/java/org/lattejava/app/db/`.
- **Forced types** (configured in the codegen config): `BIGINT ↔ Instant` (`Instant.ofEpochMilli` / `toEpochMilli`)
  and `TEXT ↔ GroupState/Role/MembershipState`.
- **Invocation:** a build-only `codegen` target runs `GenerationTool`. Its dependencies (`jooq-codegen`, `jooq-meta`)
  live in a separate non-exported dependency group assembled into a classpath for that target only — never compiled
  into the app, never bundled.
- **Workflow:** codegen is an occasional, on-demand step (only when the schema changes): `latte database` (load
  schema) → `latte codegen` (regenerate the committed classes).

## Data-access layer

### `DatabaseService` (renamed from `DatabaseClient`)

`DatabaseClient` is renamed to **`DatabaseService`** and becomes a first-class singleton in the `Services` registry,
initialized **first** in `Services.initialize`. Its **public method signatures are unchanged** — the same methods
`GroupService`, `MembershipService`, `PublishService`, and `VerificationService` already call — so those services and
all controllers are otherwise untouched. Only the internals change from D1-over-REST to jOOQ `DSLContext` calls.

`DatabaseService` owns persistence setup entirely: in its constructor it builds the **HikariCP `DataSource`** and the
jOOQ **`DSLContext`** from `db.*` config, and it closes them on `Services.shutdown()`. **`Main` does no database work**
— it remains persistence-agnostic and only calls `Services.initialize(config)`.

The other services drop their `new DatabaseClient(config)` and instead obtain the shared instance via
`Services.databaseService()` (including their test-only constructors; the test suite boots a real `Main`, so the
registry is initialized).

### Connection pooling — HikariCP (resilience handled by PlanetScale's PgBouncer)

`DatabaseService` uses **HikariCP** (~160 KB). A client-side pool is retained even though PlanetScale provides its own
server-side pooler: the local pool keeps connections warm (avoiding per-request TCP+TLS to PgBouncer) and bounds
concurrency (important on Java 25 with virtual threads, where the pool — not thread count — is the deliberate
throttle).

Resilience to PlanetScale moving, resizing, restarting, or failing over the database is handled **server-side by
PlanetScale's PgBouncer, not by the client pool**, so no topology-aware or failover-aware driver is warranted. The
concrete requirements (per PlanetScale's connection-pooling docs):

1. **Endpoint:** connect through PgBouncer on **port `6432`**, using a **Dedicated Primary PgBouncer** for production
   write traffic (username form `postgres.xxx|write-pool`). PlanetScale states the dedicated primary PgBouncer
   *"persists connections through resizes, upgrades, and most failovers"* — it absorbs the relocation/restart behind
   the pooler while the client's connection to PgBouncer stays up. The local (on-node) PgBouncer gives no such
   guarantee and is not used for production.
2. **Server-side prepared statements disabled.** PgBouncer runs **transaction-pooling mode only**, where prepared
   statements that persist across transactions are unavailable. The pgjdbc setting is **`prepareThreshold=0`** (rely
   on client-side statement caching). This is the definitive resolution of the earlier prepared-statement caveat.
3. **HikariCP liveness tuning:** validation-on-borrow (Hikari's default `isValid()`), a sane `maxLifetime` and
   `keepaliveTime` so connections rotate, and **pool size derived from observed concurrency** (PlanetScale explicitly
   warns against `cores × N` formulas). PgBouncer accepts thousands of client connections, so the client pool stays
   small.
4. **No reliance on session state** across calls (transaction pooling); jOOQ's per-unit-of-work `DataSource` use is
   compatible.

Because PlanetScale persists connections through *most* (not all) failovers, a rare transient error remains possible;
Hikari's validation + `maxLifetime` evict broken connections. A light retry around data-access calls is the
proportionate fix *if* transient blips are observed in practice — not built preemptively. Sharding is not a
client-pool concern: PlanetScale's Postgres docs do not document transparent sharding, and any provider-side routing
sits behind the single PgBouncer endpoint, invisible to the client.

### Removed

The entire D1 transport layer is deleted: `D1Request`, `D1Response`, `D1Result`, `D1Error`, `D1Exception`, `D1Tools`.

## Configuration

`d1.*` keys are replaced with:

```properties
db.url=jdbc:postgresql://127.0.0.1:5432/app      # app_test in the test config
db.username=dev
db.password=dev
# optional pool-size key
```

Dev/prod config lives in `~/.config/latte/app/config.properties`; the test config in
`src/test/resources/config.properties` points at `app_test`. Production points `db.url` at PlanetScale's
Dedicated Primary PgBouncer on port `6432` with `sslmode=require` and `prepareThreshold=0` (see "Connection pooling"
above). The `CLAUDE.md` "Database (D1)" section is rewritten for PostgreSQL/PlanetScale.

## Local & test database

Both dev and tests run against the developer's **local Homebrew PostgreSQL** on `127.0.0.1:5432`. Databases are
provisioned by the `org.lattejava.plugin:database:0.4.0` build plugin (which shells out to `psql`).

### `database` build target

A single `database` target creates/recreates databases and loads schema, controlled by a `--type` value switch
(read via Latte's `switches.values("type")`):

```
latte database --type=main,test     # defaults to both when --type is omitted; comma-split
```

- `main` → `database.createMainDatabase()` (drops + recreates `app`, creates/grants the `dev` role) →
  `execute(file: src/main/sql/schema.sql)` → `execute(file: src/main/sql/seed.sql)`.
- `test` → `database.createTestDatabase()` (drops + recreates `app_test`) →
  `execute(file: src/main/sql/schema.sql)`. (No seed: tests seed their own baseline — see below.)

### Test isolation — `@BeforeMethod`

The `@BeforeSuite` wipe is replaced by a **`@BeforeMethod`** that resets state before every test method:

1. `DELETE FROM members;` then `DELETE FROM group_verifications;` then `DELETE FROM groups;` (child tables first for
   foreign keys).
2. Re-insert the baseline: the `org.lattejava` group and an `OWNER` membership for the FusionAuth test user (the FA
   user UUID is still resolved at runtime, as today).

## Build & dependencies (`project.latte`)

Dependency scope (to be confirmed empirically with a clean build):

- **compile:** `org.jooq:jooq` (code references `DSLContext`/`DSL`/generated classes); `com.zaxxer:HikariCP`
  (`DatabaseService` references `HikariConfig`/`HikariDataSource`); `org.postgresql:postgresql` (compile if
  `module-info` does `requires org.postgresql.jdbc;` — the explicit, clearest option; it *could* be runtime-only via
  JPMS automatic service-binding, but the resolving/bundling behavior will be verified rather than assumed).
- **build-only (separate non-exported group, for the `codegen` target only):** `jooq-codegen`, `jooq-meta`.
- Jackson is removed only if nothing else uses it (the publish API / S3 likely do — verify and retain if so).
- Load `org.lattejava.plugin:database:0.4.0`; add the `database` and `codegen` targets.

### JPMS (`module-info.java`)

Add `requires org.jooq;`, `requires com.zaxxer.hikari;`, and (pending the above) `requires org.postgresql.jdbc;`.
Exact automatic-module names confirmed at implementation time.

## Cloudflare

`wrangler deploy` and the container are retained. The **D1 binding** and `d1 migrations` references are removed from
`wrangler.toml` and the deploy flow; the container reaches PlanetScale over the network.

## Out of scope

- A migration/versioning tool (deferred until needed).
- Any change to the domain model, services' public behavior, controllers, templates, or the publish/verification
  features beyond their data-access calls.
- Cloudflare deployment topology beyond removing the D1 binding.

## Testing strategy

- Tests run against local `app_test`, reset per-method via `@BeforeMethod` (above).
- `DatabaseService` (the renamed `DatabaseClient`) gets a rewritten test exercising CRUD against local Postgres,
  replacing the D1-oriented `DatabaseClientTest`.
- The existing FusionAuth-driven suite (`OIDCTestFixture`, redirect/HTML assertions) is unchanged; it now reads/writes
  through jOOQ instead of D1.
- Requirement parity with today: FusionAuth on `:9013` and MinIO (`latte minio`) are still needed; the D1 network
  requirement is replaced by a running local PostgreSQL with `app`/`app_test` provisioned via `latte database`.
