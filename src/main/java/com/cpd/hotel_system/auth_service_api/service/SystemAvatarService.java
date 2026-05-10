package com.cpd.hotel_system.auth_service_api.service;

import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;

public interface SystemAvatarService {
    void createSystemUserAvatar(MultipartFile avatar, String email) throws SQLException;
}