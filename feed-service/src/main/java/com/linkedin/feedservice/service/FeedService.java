package com.linkedin.feedservice.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.linkedin.feedservice.config.FeedProperties;
import com.linkedin.feedservice.dto.FeedResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns feed-service's two Redis structures, both maintained by the consumers:
 * <pre>
 *   connections:{userId} - set of userIds this user is connected with (the graph)
 *   feed:{userId}        - zset of postId members scored by the post's createdAt
 *                          (epoch millis) — the user's timeline
 * </pre>
 * The zset score is what makes ordering ("newest first" = highest score first)
 * and pagination ("ranks N..M") cheap, and frees both from depending on the
 * order Kafka happens to deliver events in.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FeedService {

    private static final String FEED_KEY_PREFIX = "feed:";
    private static final String CONNECTIONS_KEY_PREFIX = "connections:";
    private static final String POST_KEY_PREFIX = "post:";

    /**
     * How long the post -> recipients index is kept. It only has to outlive the post; a post that
     * is still being deleted after this long falls back to the graph (see removePostFromFeeds).
     */
    private static final Duration POST_INDEX_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final FeedProperties feedProperties;

    /**
     * One page of a user's feed, newest posts first.
     * An absent or still-ungenerated feed is simply an empty page, not an error:
     * capacity is what this endpoint answers, not existence.
     */
    public FeedResponse getFeed(String userId, int page, int size) {

        String key = feedKey(userId);

        // Rank window for this page; exclusive lower bound handled by hasNext.
        long start = (long) page * size;
        long endInclusive = start + size - 1;

        Set<String> postIds = redisTemplate.opsForZSet().reverseRange(key, start, endInclusive);
        Long total = redisTemplate.opsForZSet().zCard(key);

        List<String> ids = postIds == null ? List.of() : List.copyOf(postIds);
        long totalElements = total == null ? 0 : total;

        log.info("Feed page {} (size {}) for {}: {} of {} ids",
                page, size, userId, ids.size(), totalElements);

        return new FeedResponse(
                userId,
                ids,
                page,
                size,
                totalElements,
                totalPages(totalElements, size),
                start + size < totalElements);
    }

    /**
     * Removes the user's feed from Redis entirely (DEL on the key).
     *
     * <p>A testing convenience while there is no ingestion: lets a client reset a
     * feed between test runs without shell access to Redis. Idempotent — clearing
     * a feed that is already empty is still a success.
     */
    public void clearFeed(String userId) {

        Boolean existed = redisTemplate.delete(feedKey(userId));
        log.info("Feed for {}: {}", userId,
                Boolean.TRUE.equals(existed) ? "cleared" : "was already empty");
    }

    /**
     * A connection became mutual: record both directions in the graph.
     *
     * <p>Two SADDs rather than one atomic multi-key write is deliberate: SADD is
     * idempotent, so if a crash lands between the two, Kafka's redelivery simply
     * performs both again and the graph heals itself.
     */
    public void addConnection(String userA, String userB) {

        redisTemplate.opsForSet().add(connectionsKey(userA), userB);
        redisTemplate.opsForSet().add(connectionsKey(userB), userA);

        log.info("Graph: {} <-> {} now mutual", userA, userB);
    }

    /**
     * A connection ended: drop both directions from the graph so future posts stop
     * fanning out to an ex-connection. SREM is idempotent, so redelivery is free.
     *
     * <p>Re-connecting later just runs {@link #addConnection} again — the sets hold
     * no memory of the removal, which is exactly why re-connect needs no special case.
     */
    public void removeConnection(String userA, String userB) {

        redisTemplate.opsForSet().remove(connectionsKey(userA), userB);
        redisTemplate.opsForSet().remove(connectionsKey(userB), userA);

        log.info("Graph: {} >-< {} no longer connected", userA, userB);
    }

    /**
     * The fan-out: one post into the author's own feed plus every connection's,
     * so that reading any of those feeds later is a single Redis slice.
     *
     * <p>Snapshot semantics, by design: the targets are the connections that
     * exist at publish time. A connection formed afterwards does not receive
     * this post — matching how real feeds behave.
     */
    public void fanOutPost(String authorId, String postId, LocalDateTime createdAt) {

        double score = createdAt.toInstant(ZoneOffset.UTC).toEpochMilli();

        Set<String> targets = fanOutTargets(authorId);

        writeToFeeds(targets, postId, score);

        log.info("Fan-out: post {} -> {} feeds (author + {} connections)",
                postId, targets.size(), targets.size() - 1);
    }

    /**
     * Undoes a fan-out: removes a deleted post from every feed that would receive
     * it today (the author's plus their connections'). ZREM is idempotent, so a
     * redelivered post.deleted costs nothing.
     *
     * <p>Known approximation, on record: this recomputes the author's <em>current</em>
     * connections rather than the ones the post was published to. Since disconnect
     * exists, a pair that disconnected in between keeps the post in the
     * ex-connection's feed (their feed is no longer a target). Making this exact
     * needs the reverse index (post:{postId} -> userIds recorded during fan-out).
     */
    public void removePostFromFeeds(String authorId, String postId) {

        Set<String> indexed = redisTemplate.opsForSet().members(postKey(postId));

        // The reverse index is authoritative: it names the feeds that actually received the post.
        // Recomputing from the graph was wrong whenever a connection changed between publish and
        // delete - most visibly on account deletion, where the graph entry is removed before the
        // posts are, so the rebuilt target set was missing exactly the people holding the post.
        final Set<String> targets = (indexed == null || indexed.isEmpty())
                ? fanOutTargets(authorId)   // posts published before the index existed
                : indexed;

        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                ZSetOperations<String, String> feeds =
                        ((RedisOperations<String, String>) operations).opsForZSet();

                for (String userId : targets) {
                    feeds.remove(feedKey(userId), postId);  // ZREM, idempotent
                }
                return null;
            }
        });

        redisTemplate.delete(postKey(postId));

        log.info("Un-fanout: post {} removed from {} feeds", postId, targets.size());
    }

    /** The feeds that receive (or lose) a post from this author: author + connections. */
    private Set<String> fanOutTargets(String authorId) {

        Set<String> targets = new HashSet<>(getConnections(authorId));
        targets.add(authorId);       // your own posts appear in your own feed
        return targets;
    }

    /** The users this user is connected with, per the consumer-maintained graph. */
    public Set<String> getConnections(String userId) {

        Set<String> members = redisTemplate.opsForSet().members(connectionsKey(userId));
        return members == null ? Set.of() : members;
    }

    /**
     * All the writes for one fan-out leave in a single pipelined round trip:
     * a ZADD per target plus the per-feed trim. Without the pipeline, an author
     * with 500 connections would pay 500 sequential network round trips.
     *
     * <p>ZADD is idempotent, so Kafka's at-least-once redelivery cannot
     * duplicate a post in any feed.
     */
    private void writeToFeeds(Collection<String> userIds, String postId, double score) {

        long keepFromOldest = -(feedProperties.maxFeedSize() + 1L);

        // SessionCallback + template ops: still one pipelined round trip, but on the
        // serializer-aware API. The raw RedisConnection command methods are all
        // deprecated in Spring Data Redis 4 (DefaultedRedisConnection).
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                ZSetOperations<String, String> feeds =
                        ((RedisOperations<String, String>) operations).opsForZSet();

                SetOperations<String, String> sets =
                        ((RedisOperations<String, String>) operations).opsForSet();

                for (String userId : userIds) {
                    String key = feedKey(userId);

                    feeds.add(key, postId, score);

                    // Reverse index: exactly who received this post. Un-fanout reads it instead of
                    // recomputing the author's connections, which by then may have changed.
                    sets.add(postKey(postId), userId);

                    // Keep only the newest maxFeedSize entries: drop from the oldest
                    // side (ZREMRANGEBYRANK). No-op while the feed is under the cap,
                    // because rank -(N+1) is beyond the end.
                    feeds.removeRange(key, 0, keepFromOldest);
                }

                ((RedisOperations<String, String>) operations).expire(postKey(postId), POST_INDEX_TTL);
                return null;
            }
        });
    }

    /** Key of the sorted set holding {@code userId}'s feed. */
    static String feedKey(String userId) {
        return FEED_KEY_PREFIX + userId;
    }

    /** Key of the set holding {@code userId}'s connections. */
    /**
     * Removes a deleted account from this service entirely.
     *
     * <p>Their own keys go first, then their id is removed from every connection set. That last
     * part needs a scan of the keyspace: the graph stores edges in the direction they are read
     * ("who does this user follow"), so there is no index from a user to the sets containing
     * them. SCAN rather than KEYS so the server is not blocked while it runs, and this is
     * acceptable only because account deletion is rare - a frequent operation would need a
     * reverse index instead.
     */
    public void removeUser(String userId) {

        redisTemplate.delete(List.of(feedKey(userId), connectionsKey(userId)));

        long cleaned = 0;
        ScanOptions options = ScanOptions.scanOptions().match(CONNECTIONS_KEY_PREFIX + "*").count(200).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Long removed = redisTemplate.opsForSet().remove(key, userId);
                cleaned += removed == null ? 0 : removed;
            }
        }

        log.info("Removed feed and graph entries for deleted account {} ({} references cleared)",
                userId, cleaned);
    }

    static String postKey(String postId) {
        return POST_KEY_PREFIX + postId;
    }

    static String connectionsKey(String userId) {
        return CONNECTIONS_KEY_PREFIX + userId;
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
