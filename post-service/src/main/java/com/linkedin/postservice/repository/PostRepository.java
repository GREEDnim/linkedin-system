package com.linkedin.postservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkedin.postservice.entity.Post;

public interface PostRepository extends JpaRepository<Post, String> {

    /**
     * Every post written by one author, newest first — a profile's post list.
     * A derived query name is enough here; no @Query needed.
     */
    List<Post> findByAuthorIdOrderByCreatedAtDesc(String authorId);

    /**
     * Comment counter updates are done in SQL, not by loading the post and setting
     * a new value: read-modify-write loses updates when two people comment at once.
     * flushAutomatically + clearAutomatically keep the persistence context honest.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :postId")
    int incrementCommentCount(@Param("postId") String postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Post p set p.commentCount = p.commentCount - 1
            where p.id = :postId and p.commentCount > 0
            """)
    int decrementCommentCount(@Param("postId") String postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :postId")
    int incrementLikeCount(@Param("postId") String postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Post p set p.likeCount = p.likeCount - 1
            where p.id = :postId and p.likeCount > 0
            """)
    int decrementLikeCount(@Param("postId") String postId);
}

