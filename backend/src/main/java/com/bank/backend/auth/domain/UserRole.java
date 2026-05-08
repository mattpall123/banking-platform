package com.bank.backend.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Join row between users and roles. Composite PK (userId, roleId) means
 * a user can hold each role at most once.
 *
 * NOT modelled as @ManyToMany on UserAccount because we want to query
 * the join independently (e.g. "list everyone who is ADMIN"), and
 * because we capture grant metadata (when/who) on the row itself.
 */
@Entity
@Table(name = "user_roles")
@IdClass(UserRole.PK.class)
@Getter
@Setter
@NoArgsConstructor
public class UserRole {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "granted_by_user_id")
    private Long grantedByUserId;

    /** Composite primary key class required by JPA's @IdClass. */
    public static class PK implements Serializable {
        private Long userId;
        private Long roleId;

        public PK() {}
        public PK(Long userId, Long roleId) {
            this.userId = userId;
            this.roleId = roleId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(userId, pk.userId) && Objects.equals(roleId, pk.roleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roleId);
        }
    }
}