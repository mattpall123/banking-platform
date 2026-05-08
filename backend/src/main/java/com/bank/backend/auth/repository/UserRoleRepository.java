package com.bank.backend.auth.repository;

import com.bank.backend.auth.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRole.PK> {

    List<UserRole> findByUserId(Long userId);

    /**
     * Pulls the role codes for a user in one query. Used at login to
     * populate the JWT's "roles" claim.
     */
    @Query("""
        SELECT r.code FROM UserRole ur
          JOIN Role r ON r.id = ur.roleId
         WHERE ur.userId = :userId
    """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}