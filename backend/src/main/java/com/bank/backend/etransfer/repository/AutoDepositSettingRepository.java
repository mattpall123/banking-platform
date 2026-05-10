package com.bank.backend.etransfer.repository;

import com.bank.backend.etransfer.domain.AutoDepositSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AutoDepositSettingRepository extends JpaRepository<AutoDepositSetting, Long> {

    /** Case-insensitive lookup. Returns enabled OR disabled records. */
    @Query("SELECT a FROM AutoDepositSetting a WHERE LOWER(a.email) = LOWER(:email)")
    Optional<AutoDepositSetting> findByEmail(@Param("email") String email);

    List<AutoDepositSetting> findByUserId(Long userId);
}