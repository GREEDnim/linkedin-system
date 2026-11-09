package com.linkedin.postservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkedin.postservice.entity.Like;

public interface LikeRepository extends JpaRepository<Like, String> {

    /** Has this author already liked this post? Drives the idempotent like/unlike. */
    boolean existsByPostIdAndAuthorId(String postId, String authorId);

    /**
     * Removes one like and reports how many rows went away, so the caller knows
     * whether the counter needs decrementing (0 = the author had not liked it).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Like l where l.postId = :postId and l.authorId = :authorId")
    int deleteLike(@Param("postId") String postId, @Param("authorId") String authorId);

    /**
     * Used when a post is deleted: likes belong to the post and would otherwise be
     * orphaned rows, and they would still block the unique (post_id, author_id) key
     * if that id were ever reused.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Like l where l.postId = :postId")
    int deleteAllByPostId(@Param("postId") String postId);
}

