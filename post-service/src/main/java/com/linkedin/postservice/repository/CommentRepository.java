package com.linkedin.postservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkedin.postservice.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, String> {

    /** Comments of one post, newest first — the only read this service needs. */
    List<Comment> findByPostIdOrderByCreatedAtDesc(String postId);

    /**
     * Used when a post is deleted: comments are owned by the post and would
     * otherwise be orphaned rows nobody can ever reach. One DELETE instead of
     * loading each comment and removing it individually.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Comment c where c.postId = :postId")
    int deleteAllByPostId(@Param("postId") String postId);
}

