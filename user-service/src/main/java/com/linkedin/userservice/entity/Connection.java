package com.linkedin.userservice.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity 
@Table (name = "connections", uniqueConstraints = @UniqueConstraint(
        name = "uk_connections_pair", columnNames = {"requesterId", "receiverId"}))
@Getter
@Setter
@ToString
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
public class Connection {

    @Id 
    @GeneratedValue (strategy = GenerationType.UUID)
    private String id;

    @Column (nullable = false)
    private String requesterId;

    @Column (nullable = false)
    private String receiverId;

    @Column(nullable = false, columnDefinition = "varchar(20)")
    @Enumerated(EnumType.STRING)
    private ConnectionStatus status;

    /**
     * When this pair last became CONNECTED. Null until the first acceptance, and
     * stamped again on every re-connect — unlike createdAt, which stays the date of
     * the very first request and would otherwise make "connected since" a lie.
     */
    private LocalDateTime connectedAt;

    @CreationTimestamp 
    private LocalDateTime createdAt;

    @UpdateTimestamp 
    private LocalDateTime updatedAt;
    
}
