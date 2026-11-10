> **Historical document.**
>
> This describes the state of `user-service` at the end of its first round of work.
> Five services have been added since (post, feed, search, notification, gateway), and
> their changes are described in the commit messages rather than here. Treat the
> details below as a record of how user-service got to its current shape, not as a
> map of the repository.

# `user-service` — Change Log & Notes

> Every change made to `user-service` since the initial scaffold, with the reasoning
> behind it. Written as a learning reference, not a release note.

---
## Contents

1. [Build & startup blockers](#1-build--startup-blockers-fixed-first)
2. [application.yaml](#2-applicationyaml)
3. [Entity layer](#3-entity-layer-entity)
4. [Repository layer](#4-repository-layer-repository)
5. [Service layer](#5-service-layer-service)
6. [Controller layer](#6-controller-layer-controller)
7. [DTO layer](#7-dto-layer-dto--all-records-now)
8. [Events](#8-events-event--typed-records-over-mapstringobject)
9. [Exception handling](#9-exception-handling-exception)
10. [Configuration](#10-configuration-config)
11. [pom.xml](#11-pomxml)
12. [Connection lifecycle rules](#12-connection-lifecycle-final-rules)
13. [API reference](#13-api-reference-final)
14. [Verification performed](#14-verification-performed)
15. [Spring Boot concepts touched](#15-spring-boot-concepts-touched-learning-notes)
16. [Known gaps / next steps](#16-known-gaps--next-steps)
17. [File inventory](#17-file-inventory)

---


## TL;DR

| | Before | After |
|---|---|---|
| Compiles? | ❌ 2 errors | ✅ |
| Starts? | ❌ 4 startup failures | ✅ (verified against Docker MySQL + Kafka) |
| Endpoints | 2 (register, login) | 10 |
| Logic bugs | 8 | 0 known |
| Security holes | 3 | 0 known |
| AWS SDK | v1 (EOL Dec 2025) | v2 (`software.amazon.awssdk:2.57.11`) |
| `@Value` field injection | 6 | 0 |
| `@Data` on entities | 2 | 0 |
| DTOs | 4 mutable classes | 6 records |

---

## 1. Build & startup blockers (fixed first)

### Compile errors

| File | Problem | Fix |
|---|---|---|
| `UserController` | `RequestParam("file")` missing `@` → `<identifier> expected` | `@RequestParam("file")` + imports for `RequestParam` and `MultipartFile` |
| `S3Service` | `uploadFile()` declared `String` but had no `return` | implemented the upload |
| `AuthService` | `Map` not imported; `generateRefreshToken` called but never defined | added import + implemented the method |
| `AuthService` | `signWith(Key, SIG.HS256)` — jjwt 0.13 needs `SecretKey` | `getSignInKey()` returns `javax.crypto.SecretKey` |

### Startup failures

| File | Problem | Fix |
|---|---|---|
| `application.yaml` | `jdbc://mysql://...` — malformed | `jdbc:mysql://...` + `allowPublicKeyRetrieval=true&useSSL=false` |
| `application.yaml` | `org.apache.kafka.serialization.StringSerializer` — wrong package | `org.apache.kafka.common.serialization.StringSerializer` |
| `application.yaml` | `jwt.secret/expiration/refresh-expiration` empty → `NumberFormatException` | real Base64 secret + millisecond values |
| `BCryptConfig` | empty class, no `@Bean` → `BCryptPasswordEncoder` not injectable | `@Configuration` + `@Bean` |
| `AWSConfig` | empty class | `@Bean S3Client` |

---

## 2. `application.yaml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:user_db}?...&allowPublicKeyRetrieval=true&useSSL=false
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
  jpa:
    hibernate.ddl-auto: update
    show-sql: true
    open-in-view: false          # was default (true)
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false   # JSON body is the contract, not the class name
  servlet.multipart:
    max-file-size: 5MB
    max-request-size: 6MB
server:
  port: 8081
jwt:
  secret: ${JWT_SECRET:...}
  expiration: ${JWT_EXPIRATION:900000}            # 15 min, MILLISECONDS
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}   # 7 days
aws:
  access-key: ${AWS_ACCESS_KEY:dev-access-key}
  secret-key: ${AWS_SECRET_KEY:dev-secret-key}
  region: ${AWS_REGION:us-east-1}
  s3.bucket-name: ${AWS_S3_BUCKET:linkedin-profile-media}
```

**Why each change**

| Change | Reason |
|---|---|
| `${ENV:default}` everywhere | lets real environments inject secrets while local dev still boots |
| `open-in-view: false` | stops lazy DB reads during JSON serialisation; forces DB work into `@Transactional` service methods (Spring Boot's recommendation) |
| `spring.json.add.type.headers: false` | otherwise `JsonSerializer` stamps `__TypeId__` with this app's FQCN and consumers crash with `ClassNotFoundException` |
| `multipart.max-file-size` | bounds uploads at the framework level → clean `413` instead of an exception |
| `hibernate.dialect` removed | Hibernate 6/7 auto-detects; setting it is deprecated noise |

---

## 3. Entity layer (`entity/`)

### `User`

| Change | Reason |
|---|---|
| `@Data` → `@Getter @Setter @ToString @NoArgsConstructor @AllArgsConstructor @Builder` | `@Data` generates `equals`/`hashCode` on mutable fields (dangerous for JPA) and a `toString()` that leaked the BCrypt hash and touched a lazy collection |
| `@ToString.Exclude` on `password` | a hash must never reach a log line (verified: 0 references in bytecode `toString`) |
| `@ToString.Exclude` on `skills` | lazy `PersistentBag`: reading it in `toString()` outside a transaction hits the DB |
| `@Builder.Default` on `skills` | keeps `new ArrayList<>()` when built via `User.builder()` |
| `@CollectionTable(joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))` | makes the FK target explicit |

`skills` is an `@ElementCollection`, so it is a **separate table** (`user_skills`) but **not an entity**:

```sql
CREATE TABLE user_skills (
  user_id varchar(255) NOT NULL,
  skill varchar(255),
  PRIMARY KEY (user_id, skill),
  FOREIGN KEY (user_id) REFERENCES users (id)
);
```

Hibernate infers the FK target from: the owning entity (`User`) → its `@Id` (`id`) → its `@Table` (`users`).

### `Connection`

| Change | Reason |
|---|---|
| `@Data` → `@Getter @Setter @ToString @NoArgsConstructor @AllArgsConstructor @Builder` | same reasons as `User` |
| `@UniqueConstraint(name="uk_connections_pair", columnNames={"requesterId","receiverId"})` | DB-level backstop against duplicate requests |

⚠️ The constraint only blocks **exact** duplicates. `(A,B)` and `(B,A)` are different tuples, so the reversed pair still needs the application-level check:

```
step1: A->B inserted OK
step2: B->A ALSO inserted OK     <-- not blocked by the unique key
step3: exact duplicate A->B      --> ERROR 1062 Duplicate entry 'A-B'
```

### Enums moved: `UserRole`, `ConnectionStatus` → `entity/`

Rule applied: **nothing but `@SpringBootApplication` lives in the root package.**

---

## 4. Repository layer (`repository/`)

### `ConnectionRepository`

| Before | After |
|---|---|
| `existsByRequesterIdAndReceiverId(...)` | `findBetween(userA, userB)` → `Optional<Connection>` |
| `findByRequesterIdAndStatus(...)` | `findByUserIdAndStatus(...)` → `@Query`, both directions |
| — | `findByReceiverIdAndStatusOrderByCreatedAtDesc(...)` (derived) |

```java
@Query("""
        select c from Connection c
        where (c.requesterId = :userA and c.receiverId = :userB)
           or (c.requesterId = :userB and c.receiverId = :userA)
        """)
Optional<Connection> findBetween(...);
```

**Why `Optional` and not `boolean`:** the caller must *reuse* an existing `REJECTED` row,
because `uk_connections_pair` forbids inserting a second row for the same pair.

**Why `@Query` here but a derived name for the pending list:** derived queries can't express
`(a=A and b=B) or (a=B and b=A)` readably. Rule of thumb:

```java
findByEmail(...)                                       // derived ✅
findByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc()   // derived ✅
existsByRequesterIdAndReceiverIdOrRequesterIdAndReceiverId()  // @Query ✅
```

**Parentheses are load-bearing** in JPQL — `AND` binds tighter than `OR`:

```sql
where status = :status and (requester = :id or receiver = :id)   -- correct
where status = :status and requester = :id or receiver = :id     -- BUG: returns any status
```

### `UserRepository`

Unchanged: `existsByEmail`, `findByEmail` (derived), plus inherited `JpaRepository<User, String>`
methods used: `save`, `findById`, `existsById`, `findAllById`.

---

## 5. Service layer (`service/`)

### `AuthService`

| Change | Reason |
|---|---|
| `@Value` fields → `JwtProperties` record | type-safe config, constructor injection, no field injection |
| `RuntimeException` → `ApiException.conflict/unauthorized` | 409/401 instead of 500 |
| login: one message for unknown email **and** wrong password | prevents user enumeration |
| `register` is `@Transactional`, `login` is `@Transactional(readOnly = true)` | atomic write; read-only hint for reads |
| `Map<String,Object>` event → `UserCreatedEvent.from(user)` | typed message contract |
| record accessors (`request.email()`) | DTOs are records now |

### `UserService`

| Method | Change |
|---|---|
| `sendConnectionRequest(requesterId, receiverId)` | parameter order was reversed; now validates self-request (400) and target existence (404), reuses a `REJECTED` row, returns the `connectionId` |
| `acceptConnectionRequest(id, caller)` | added **authorization** (receiver only) + status guard; publishes a typed event |
| `rejectConnectionRequest(id, caller)` | **new** — `PENDING → REJECTED`, no Kafka event (rejection is silent) |
| `loadPendingForReceiver(...)` | **new private guard** shared by accept/reject: exists → 404, not receiver → 403, not PENDING → 409 |
| `getConnections(userId)` | both directions; 2 queries instead of 1+N |
| `getPendingConnectionRequests(userId)` | **new**; 2 queries, newest first |
| `updateProfile(...)` | takes `UpdateProfileRequest` (not the response DTO); partial update; **`location` was silently dropped, now applied**; returns the saved entity |
| `uploadProfilePhoto(...)` | unchanged shape, S3 SDK v2 underneath |
| `mapToUserResponse` | **deleted** — replaced by `UserResponse.from(user)` |

**The N+1 fix** — before, every connection triggered its own user lookup:

```java
// before: 1 + N queries
connections.stream().map(c -> getUserProfile(c.getReceiverId()))

// after: 2 queries, join in memory
Set<String> otherUserIds = connections.stream()
    .map(c -> c.getRequesterId().equals(userId) ? c.getReceiverId() : c.getRequesterId())
    .collect(Collectors.toCollection(LinkedHashSet::new));
Map<String, User> usersById = userRepository.findAllById(otherUserIds).stream()
    .collect(Collectors.toMap(User::getId, Function.identity()));
```

`LinkedHashSet` de-duplicates **and** preserves query order.

**The lazy-collection bug** (surfaced by `open-in-view: false`):

```
Could not write JSON: Cannot lazily initialize collection of role
'User.skills' ... (no session)
```

`user.getSkills()` is a Hibernate `PersistentBag`, not an `ArrayList`. Handing it to a DTO only
works while the session stays open. Fixed by copying **inside** the transaction, in
`UserResponse.from(...)`:

```java
user.getSkills() == null ? List.of() : new ArrayList<>(user.getSkills())
```

### `S3Service` (AWS SDK v2)

| Change | Reason |
|---|---|
| `AmazonS3` (v1) → `S3Client` (v2) | SDK v1 reached end of support 2025-12-31 (it logged a warning at every startup) |
| `@Value` field → `AwsProperties` | constructor injection |
| `catch (RuntimeException)` → `catch (SdkException)` | v2 root exception covers `S3Exception` + client errors |
| key gets a `UUID` + original extension | no key collisions; correct `Content-Type` when served |
| returns the object URL | stored on the user's `profilePhotoUrl` |

```java
// v1 (mutable setters, metadata object)
amazonS3.putObject(new PutObjectRequest(bucket, key, stream, metadata));

// v2 (immutable builders, required content length)
s3Client.putObject(
    PutObjectRequest.builder().bucket(b).key(k).contentLength(size).contentType(ct).build(),
    RequestBody.fromInputStream(stream, size));
```

---

## 6. Controller layer (`controller/`)

| Change | Reason |
|---|---|
| `@RequestMapping("/api/vi/users")` → `/api/v1/users` | typo: `vi`, not `v1` — every documented URL 404'd |
| `getConnection` → `getConnections` | matches the service method and returns a list |
| `requireSelf(...)` helper | replaced 4 copies of `return ResponseEntity.status(FORBIDDEN).build()`; also gives 403s the same JSON body as every other error |
| `/connect` returns **201 Created** | it creates a resource, consistent with `/auth/register` |
| `/connection/{id}/accept` → `/connections/{id}/accept` | plural, consistent with `/connections/pending` |
| `POST /connections/{id}/reject` | new |
| `@Valid @RequestBody UpdateProfileRequest` | `@Size` rules now actually run |
| class javadoc | documents that `X-User-Id` comes from the gateway and is **not** trustworthy on its own |

---

## 7. DTO layer (`dto/`) — all records now

Rule applied: **immutable data carriers are `record`s; only JPA entities are classes.**

| DTO | Direction | Notes |
|---|---|---|
| `RegisterRequest` | in | `@NotBlank`, `@Email`, `@Size(min=6)` on components |
| `LoginRequest` | in | `@NotBlank`, `@Email` |
| `UpdateProfileRequest` | in | **new** — replaces using `UserResponse` as a request body (which exposed `id`, `role`, `createdAt`, `email` to clients) |
| `AuthResponse` | out | `AuthResponse.of(user, access, refresh)`; `tokenType` is a constant |
| `UserResponse` | out | `UserResponse.from(user)`; copies `skills` |
| `ConnectionResponse` | out | **new** — returns the generated `connectionId` |
| `PendingConnectionRequest` | out | **new** — connectionId + requester name/headline/photo, deliberately **no email** |

```java
// records give you a canonical constructor, accessors, equals/hashCode and toString
public record PendingConnectionRequest(
        String connectionId, String requesterId,
        String requesterFirstName, String requesterLastName,
        String requesterHeadline, String requesterProfilePhotoUrl,
        LocalDateTime requestedAt) { ... }
```

---

## 8. Events (`event/`) — typed records over `Map<String,Object>`

| Event | Topic | Consumed by (planned) |
|---|---|---|
| `UserCreatedEvent` | `user.created` | search-service (index) |
| `UserUpdatedEvent` | `user.updated` | search-service (re-index) |
| `ConnectionRequestedEvent` | `connection.requested` | notification-service |
| `ConnectionAcceptedEvent` | `connection.accepted` | notification-service, feed-service |

| Change | Reason |
|---|---|
| `Map<String,Object>` → records with `from(entity)` | untyped maps are invisible to consumers' schemas; also fixed a real bug where the payload was built and then **never sent** — the bare `receiverId` string was published instead |
| **no** `connection.rejected` event | LinkedIn never tells the sender they were ignored; publishing it would leak that downstream |

---

## 9. Exception handling (`exception/`)

Before: every failure was `throw new RuntimeException(...)` → **HTTP 500**.

```
exception/
├── ApiException.java          one RuntimeException carrying an HttpStatus + static factories
├── ErrorResponse.java         record: timestamp, status, error, message, details
└── GlobalExceptionHandler.java @RestControllerAdvice
```

| Handled | Status |
|---|---|
| `ApiException` | whatever it carries (400/401/403/404/409/502) |
| `MethodArgumentNotValidException` | 400 + per-field `details` |
| `HandlerMethodValidationException` | 400 |
| `MissingRequestHeaderException` | 400 `Required header is missing: X-User-Id` |
| `MissingServletRequestParameterException` | 400 |
| `MethodArgumentTypeMismatchException` | 400 |
| `HttpMessageNotReadableException` | 400 malformed JSON |
| `HttpRequestMethodNotSupportedException` | 405 |
| `NoResourceFoundException` | 404 unknown URL (was 500) |
| `MultipartException` | 400 not a multipart request (was 500) |
| `MaxUploadSizeExceededException` | 413 |
| `Exception` | 500, logged, no stack trace leaked |

Every error now has one shape:

```json
{"timestamp":"...","status":409,"error":"Conflict","message":"You are already connected with this user","details":[]}
```

---

## 10. Configuration (`config/`)

| Class | Purpose |
|---|---|
| `JwtProperties` | `@ConfigurationProperties(prefix = "jwt")` record: `secret`, `expiration`, `refreshExpiration` |
| `AwsProperties` | `@ConfigurationProperties(prefix = "aws")` record + nested `S3(bucketName)` |
| `AWSConfig` | `@Bean S3Client` (v2, region + static credentials) |
| `BCryptConfig` | `@Bean BCryptPasswordEncoder` |

`UserServiceApplication` gained `@ConfigurationPropertiesScan`, which finds the two record
binders. Result: **zero `@Value` annotations** in the service.

```java
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expiration, long refreshExpiration) { }
```

Relaxed binding maps `refresh-expiration` in YAML → `refreshExpiration` in Java automatically.

---

## 11. `pom.xml`

```xml
<properties>
  <java.version>17</java.version>
  <aws-sdk.version>2.57.11</aws-sdk.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>bom</artifactId>
      <version>${aws-sdk.version}</version>
      <type>pom</type><scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>s3</artifactId>   <!-- no version: the BOM owns it -->
</dependency>
```

Removed: `com.amazonaws:aws-java-sdk-s3:1.12.797`.
Verified with `dependency:tree` → 20+ `software.amazon.awssdk:*:2.57.11` entries, **0** `com.amazonaws`.

---

## 12. Connection lifecycle (final rules)

```
        ┌───────────── connect ─────────────┐
        │                                   ▼
     (none)                            PENDING ──── accept ────► CONNECTED
        ▲                                   │                        │
        │                                   │                        │ connect → 409
        └────── connect ◄── REJECTED ◄── reject                      ▼
                   (retry allowed)                                (terminal)
```

| Current state | `connect` again |
|---|---|
| none | 201 → `PENDING` |
| `PENDING` | **409** `A connection request already exists between these users` |
| `CONNECTED` | **409** `You are already connected with this user` |
| `REJECTED` | **201** → `PENDING` (same row reused) |

Rules encoded:

1. **One row per pair.** The relationship is mutual; `requesterId`/`receiverId` record only *who initiated*.
2. **Only the receiver** may accept or reject (403 otherwise).
3. **Accept/reject require `PENDING`** (409 otherwise).
4. **Rejection is silent** — no event, no notification. Mirrors LinkedIn's *Ignore*: *"The sender won't be notified… so they may try to connect with you again."*
5. **Rejection is never permanent**, unlike LinkedIn's *"I don't know this person"* (not implemented — would need a `BLOCKED` state that beats every other check).

---

## 13. API reference (final)

| Method | Path | Caller check | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | — | 201 `AuthResponse` | 400, 409 |
| POST | `/api/v1/auth/login` | — | 200 `AuthResponse` | 400, 401 |
| GET | `/api/v1/users/{userId}` | any authenticated | 200 `UserResponse` | 400, 404 |
| PUT | `/api/v1/users/{userId}/profile` | self | 200 `UserResponse` | 400, 403, 404 |
| POST | `/api/v1/users/{userId}/upload/profile-photo` | self | 200 `UserResponse` | 400, 403, 413, 502 |
| POST | `/api/v1/users/{targetUserId}/connect` | caller = requester | 201 `ConnectionResponse` | 400, 404, 409 |
| POST | `/api/v1/users/connections/{id}/accept` | receiver | 200 `ConnectionResponse` | 403, 404, 409 |
| POST | `/api/v1/users/connections/{id}/reject` | receiver | 200 `ConnectionResponse` | 403, 404, 409 |
| GET | `/api/v1/users/{userId}/connections` | self | 200 `List<UserResponse>` | 403 |
| GET | `/api/v1/users/{userId}/connections/pending` | self | 200 `List<PendingConnectionRequest>` | 403 |
| GET | `/actuator/health` | — | 200 | — |

All authenticated calls need `X-User-Id: <uuid>`.

---

## 14. Verification performed

Everything below was executed against the real Docker MySQL + Kafka, not just compiled.

| Check | Result |
|---|---|
| `./mvnw clean package` | BUILD SUCCESS |
| `./mvnw test` | 1 test, 0 failures |
| Boot (jar) | `Started UserServiceApplication in ~3s` |
| AWS SDK v1 end-of-support warning | gone |
| `com.amazonaws` in dependency tree | 0 |
| register → JWT with `type: access`/`type: refresh` | ✅ |
| duplicate email → 409 | ✅ |
| wrong password **and** unknown email → identical 401 | ✅ |
| record validation → 400 + `details[]` | ✅ |
| profile update incl. `location` + `skills` | ✅ |
| cross-user profile update → 403 | ✅ |
| self connect → 400 | ✅ |
| connect → 201 `PENDING` + id | ✅ |
| reverse request while pending → 409 | ✅ |
| requester tries to accept/reject → 403 | ✅ |
| receiver rejects → 200 `REJECTED`; pending list empty | ✅ |
| re-request after rejection → 201, **1 row** reused | ✅ |
| reject an already-CONNECTED pair → 409 | ✅ |
| connections listed from **both** sides | ✅ |
| pending list shows requester name/headline, no email | ✅ |
| missing header → 400, unknown URL → 404, bad JSON → 400, wrong method → 405 | ✅ |
| non-multipart upload → 400; S3 failure → 502 (not 500) | ✅ |
| Kafka payloads | real JSON records, no `__TypeId__` | ✅ |
| MySQL `connections` | `UNIQUE KEY uk_connections_pair (requester_id, receiver_id)` | ✅ |
| `password` inside `User.toString()` (bytecode) | 0 occurrences | ✅ |

---

## 15. Spring Boot concepts touched (learning notes)

| Concept | Where | Takeaway |
|---|---|---|
| `@Entity` / `@Table` / `@Id` / `@Column` | `entity/` | Annotations **are** the schema; Hibernate generates the DDL |
| `@ElementCollection` + `@CollectionTable` | `User.skills` | Creates a real table but **no entity**: no `@Id`, owned by the parent |
| `@Enumerated(STRING)` | `role`, `status` | Store readable names, not ordinals |
| Spring Data derived queries | `findByReceiverIdAndStatusOrderByCreatedAtDesc` | Method name = query; fall back to `@Query` when unreadable |
| JPQL vs SQL | `ConnectionRepository` | JPQL uses entity/field names; `AND` binds tighter than `OR` |
| N+1 queries | `getConnections` | Collect ids → `findAllById` → join in memory |
| `@Transactional` | all writes | Wraps `@ElementCollection` delete+insert; proxied, so self-calls bypass it |
| `@Transactional(readOnly = true)` | all reads | No dirty checking; session stays open while mapping to DTOs |
| `open-in-view: false` | `application.yaml` | DB access belongs in the service, not during JSON serialisation |
| `PersistentBag` | `User.skills` | Never put a managed collection in a DTO — copy it |
| Bean validation on records | `dto/*Request` | Annotations go on record components; `@Valid` still works |
| `@RestControllerAdvice` | `GlobalExceptionHandler` | Map exceptions to status codes once, centrally |
| `@ConfigurationProperties` + records | `JwtProperties`, `AwsProperties` | Constructor binding, type-safe, beats scattered `@Value` |
| Constructor injection | every service | `final` fields + `@RequiredArgsConstructor`; no field injection |
| Records vs classes | DTOs vs entities | Immutable carriers are records; JPA needs mutable classes |
| `@Data` on entities | `entity/` | Generates risky `equals`/`hashCode`/`toString` — prefer explicit Lombok |
| Kafka `JsonSerializer` | `application.yaml` | `add.type.headers: false` or consumers depend on your classpath |
| Multipart uploads | `S3Service` | `contentLength` is required for streaming to S3 |
| Unique constraints | `Connection` | DB backstop ≠ business rule: reversed pairs still need app logic |

---

## 16. Known gaps / next steps

| # | Item | Why it matters |
|---|---|---|
| 1 | **`api-gateway` has no JWT filter** | `X-User-Id` is currently attacker-controlled; the gateway must validate the JWT, strip any inbound `X-User-Id`, and inject a verified one |
| 2 | No `refresh` endpoint | `refreshToken` is issued but never exchanged |
| 3 | No token revocation / `jti` | logout and refresh-token rotation need Redis (already in `docker-compose.yml`) |
| 4 | Invitations never expire | LinkedIn expires them after 6 months; `PENDING` lives forever here |
| 5 | No block list | LinkedIn's *"I don't know this person"* — needs a `BLOCKED` state that beats every other check |
| 6 | No per-field `@Valid` on connect | `targetUserId` is validated as a UUID nowhere |
| 7 | Only a `contextLoads` test | Add `@WebMvcTest` for controllers and `@DataJpaTest` for the JPQL queries — these rules are currently unprotected |
| 8 | `createdAt` on a re-request | the reused row keeps its first-attempt timestamp, so the pending inbox sorts by the original attempt |
| 9 | Hardcoded dev secrets in `application.yaml` | fine for local, must come from env/vault in any real deployment |

---

## 17. File inventory

### Created

```
config/JwtProperties.java
config/AwsProperties.java
dto/UpdateProfileRequest.java
dto/ConnectionResponse.java
dto/PendingConnectionRequest.java
event/UserCreatedEvent.java
event/UserUpdatedEvent.java
event/ConnectionRequestedEvent.java
event/ConnectionAcceptedEvent.java
exception/ApiException.java
exception/ErrorResponse.java
exception/GlobalExceptionHandler.java
```

### Rewritten / heavily modified

```
pom.xml
application.yaml
UserServiceApplication.java          @ConfigurationPropertiesScan
entity/User.java                     @Data removed, ToString.Exclude
entity/Connection.java               unique constraint, @Builder
repository/ConnectionRepository.java findBetween / findByUserIdAndStatus / pending query
service/AuthService.java             JwtProperties, typed event, proper errors
service/UserService.java             reject, shared guard, N+1 fix, no mapToUserResponse
service/S3Service.java               AWS SDK v2, AwsProperties
controller/AuthController.java       record accessors, tidy imports
controller/UserController.java       v1 typo, reject, 201, requireSelf
config/AWSConfig.java                S3Client bean
config/BCryptConfig.java             @Configuration + @Bean
dto/AuthResponse.java                record + of(...)
dto/UserResponse.java                record + from(...)
dto/LoginRequest.java                record
dto/RegisterRequest.java             record
dto/UpdateProfileRequest.java        record
```

### Moved

```
UserRole.java         -> entity/UserRole.java
ConnectionStatus.java -> entity/ConnectionStatus.java
```

### Deleted / absorbed

```
UserService.mapToUserResponse()      -> UserResponse.from(user)
ConnectionRepository.existsBetween  -> findBetween (returns Optional)
```

---

## 18. How to run locally

```bash
# 1. infrastructure
docker compose up -d mysql kafka redis elasticsearch

# 2. build + test
cd user-service && ./mvnw clean package

# 3. run (your 8081 may be taken — override if needed)
java -jar target/user-service-0.0.1-SNAPSHOT.jar --server.port=8082

# 4. smoke test
curl -X POST localhost:8082/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@test.com","password":"secret123"}'
```

The schema is created automatically because `spring.jpa.hibernate.ddl-auto: update`.
