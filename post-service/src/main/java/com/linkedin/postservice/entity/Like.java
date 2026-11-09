package com.linkedin.postservice.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One like of one post by one user — a join row with a timestamp.
 *
 * <p>unique (postId, authorId) is what makes "like" safe: two concurrent requests or
 * a double click cannot create two rows, so Post.likeCount cannot drift. It also
 * doubles as the lookup index because postId is its leftmost column.
 *
 * <p>Field names are camelCase and the column names are derived by Hibernate's
 * naming strategy, exactly like Post and Comment. The old explicit
 * @Column(name = "post_id") produced the same DDL, so no schema change.
 */
@Entity
@Table(name = "likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_likes_post_author",
                columnNames = { "postId", "authorId" }))
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String postId;

    @Column(nullable = false)
    private String authorId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

