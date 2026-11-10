package com.linkedin.userservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkedin.userservice.entity.Connection;
import com.linkedin.userservice.entity.ConnectionStatus;

public interface ConnectionRepository extends JpaRepository<Connection, String> {

    /**
     * The single row between two users, if any — regardless of direction or status.
     * Returns an Optional rather than a boolean because the caller needs the row
     * itself: a REJECTED row has to be REUSED for a new attempt, since
     * uk_connections_pair forbids inserting a second A -> B row.
     */
    @Query("""
            select c from Connection c
            where (c.requesterId = :userA and c.receiverId = :userB)
               or (c.requesterId = :userB and c.receiverId = :userA)
            """)
    Optional<Connection> findBetween(@Param("userA") String userA, @Param("userB") String userB);

    /**
     * All connections involving a user in EITHER role, restricted to a status.
     * The naive findByRequesterIdAndStatus misses everything where the user is the receiver.
     */
    @Query("""
            select c from Connection c
            where c.status = :status
              and (c.requesterId = :userId or c.receiverId = :userId)
            """)
    List<Connection> findByUserIdAndStatus(
            @Param("userId") String userId,
            @Param("status") ConnectionStatus status);

    /** Incoming requests the user has not answered yet, newest first. */
    List<Connection> findByReceiverIdAndStatusOrderByCreatedAtDesc(
            String receiverId, ConnectionStatus status);

    /**
     * Removes every connection touching a user, in both directions.
     *
     * <p>Used when an account is deleted. A connection is mutual and stored as one row, so
     * leaving it behind would point the surviving user at somebody who no longer exists.
     */
    @Modifying
    @Query("delete from Connection c where c.requesterId = :userId or c.receiverId = :userId")
    int deleteAllForUser(@Param("userId") String userId);
}


