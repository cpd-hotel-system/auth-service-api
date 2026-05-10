package com.cpd.hotel_system.auth_service_api.repo;

import com.cpd.hotel_system.auth_service_api.entity.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SystemUserRepo extends JpaRepository<SystemUser, String> {
    @Query(value = "select * from system_user WHERE email=?1", nativeQuery = true)
    public Optional<SystemUser> findByEmail(String email);
}