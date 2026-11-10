package com.linkedin.userservice.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity 
@Table (name = "users")
@Getter
@Setter
@ToString
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 
public class User {
    @Id 
    @GeneratedValue (strategy =  GenerationType.UUID)
    private String id;

    @Column (unique = true, nullable = false)
    private String email;

    @Column (nullable = false)
    @ToString.Exclude // never let a password hash reach a log line
    private String password;

    @Column (nullable = false)
    private String firstName;

    @Column (nullable = false)
    private String lastName;

    private String headline;
    private String about;
    private String location;

    private String profilePhotoUrl;
    private String coverPhotoUrl;

    /** The object key behind profilePhotoUrl, so a replaced or deleted avatar can be removed. */
    @Column(length = 500)
    private String profilePhotoKey;

    @Column(columnDefinition = "varchar(20)")
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @ElementCollection 
    @CollectionTable  (name = "user_skills", joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"))
    @Column(name = "skill")
    @Builder.Default
    @ToString.Exclude // lazy collection: touching it in toString() can hit the DB
    private List<String> skills = new ArrayList<>();

    @CreationTimestamp 
    private LocalDateTime createdAt;

    @UpdateTimestamp 
    private LocalDateTime updatedAt;


}
