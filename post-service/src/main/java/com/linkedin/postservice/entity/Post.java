package com.linkedin.postservice.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A post written by a user.
 *
 * <p>authorId is a plain string, not a @ManyToOne: users live in user-service's
 * database, so the two services keep their own aggregates and refer to each other
 * by id.
 *
 * <p>The index covers the only query we run per profile: "posts of an author,
 * newest first". Without it that request is a full table scan.
 */
@Entity
@Table(name = "posts",
        indexes = @Index(name = "idx_posts_author_created", columnList = "authorId, createdAt"))
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String authorId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String imageUrl;

    /**
     * The object key behind imageUrl, kept so the image can be deleted with the post. It cannot
     * be reconstructed from the URL afterwards: keys are namespaced by author, not by post id.
     */
    @Column(length = 500)
    private String imageKey;

    /** Denormalised counters, kept in sync by the like/comment endpoints. */
    private int likeCount;

    private int commentCount;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

