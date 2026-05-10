package com.cpd.hotel_system.auth_service_api.service;

import com.cpd.hotel_system.auth_service_api.dto.request.PasswordRequestDto;
import com.cpd.hotel_system.auth_service_api.dto.request.RequestLoginDto;
import com.cpd.hotel_system.auth_service_api.dto.request.SystemUserRequestDto;
import com.cpd.hotel_system.auth_service_api.dto.request.UserUpdateRequestDto;
import com.cpd.hotel_system.auth_service_api.dto.response.ResponseUserDetailsDto;

import java.io.IOException;
import java.util.List;

public interface SystemUserService {
    public void createUser(SystemUserRequestDto dto) throws IOException;
    public void initializeHosts(List<SystemUserRequestDto> users) throws IOException;
    public void resend(String email, String type);
    public void forgotPasswordSendVerificationCode(String email);
    public boolean verifyReset(String otp, String email);
    public boolean passwordReset(PasswordRequestDto dto);
    public boolean verifyEmail(String otp, String email);
    public Object userLogin(RequestLoginDto dto);
    public ResponseUserDetailsDto getUserDetails(String email);
    public void updateUserDetails(String email, UserUpdateRequestDto data);
}
