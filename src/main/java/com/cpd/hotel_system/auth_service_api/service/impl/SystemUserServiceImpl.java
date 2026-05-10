package com.cpd.hotel_system.auth_service_api.service.impl;

import com.cpd.hotel_system.auth_service_api.dto.request.UserUpdateRequestDto;
import com.cpd.hotel_system.auth_service_api.dto.response.ResponseUserDetailsDto;
import com.cpd.hotel_system.auth_service_api.entity.Otp;
import com.cpd.hotel_system.auth_service_api.entity.SystemAvatar;
import com.cpd.hotel_system.auth_service_api.exception.BadRequestException;
import com.cpd.hotel_system.auth_service_api.config.KeycloakSecurityUtil;
import com.cpd.hotel_system.auth_service_api.dto.request.PasswordRequestDto;
import com.cpd.hotel_system.auth_service_api.dto.request.RequestLoginDto;
import com.cpd.hotel_system.auth_service_api.dto.request.SystemUserRequestDto;
import com.cpd.hotel_system.auth_service_api.entity.SystemUser;
import com.cpd.hotel_system.auth_service_api.exception.DuplicateEntryException;
import com.cpd.hotel_system.auth_service_api.exception.EntryNotFoundException;
import com.cpd.hotel_system.auth_service_api.exception.UnAuthorizedException;
import com.cpd.hotel_system.auth_service_api.repo.OtpRepo;
import com.cpd.hotel_system.auth_service_api.repo.SystemUserRepo;
import com.cpd.hotel_system.auth_service_api.service.EmailService;
import com.cpd.hotel_system.auth_service_api.service.SystemUserService;
import com.cpd.hotel_system.auth_service_api.util.FileDataExtractor;
import com.cpd.hotel_system.auth_service_api.util.OtpGenerator;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemUserServiceImpl implements SystemUserService {

    private final OtpRepo otpRepo;
    private final FileDataExtractor fileDataExtractor;
    @Value("${keycloak.config.realm}")
    private String realm;


    @Value("${spring.security.oauth2.resourceserver.jwt.token-uri}")
    private String KeyCloakApiUrl;

    @Value("${keycloak.config.client-id}")
    private String clientId;

    @Value("${keycloak.config.secret}")
    private String secret;


    private final SystemUserRepo systemUserRepo;
    private final OtpRepo repo;
    private final KeycloakSecurityUtil keycloakUtil;
    private final OtpGenerator otpGenerator;
    private final EmailService emailService;

    @Override
    public void createUser(SystemUserRequestDto dto) throws IOException {
        if (dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) {
            throw new BadRequestException("First name is required");
        }

        if (dto.getLastName() == null || dto.getLastName().trim().isEmpty()) {
            throw new BadRequestException("Last name is required");
        }

        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new BadRequestException("Email is required");
        }

        String userId = "";
        String otp = "";
        Keycloak keycloak = null;

        UserRepresentation existingUser = null;
        keycloak = keycloakUtil.getKeycloakInstance();

        existingUser = keycloak.realm(realm).users().search(dto.getEmail()).stream()
                .findFirst().orElse(null);

        if (existingUser != null) {
            Optional<SystemUser> selectedSystemUserFromAuthService =
                    systemUserRepo.findByEmail(dto.getEmail());
            if (selectedSystemUserFromAuthService.isEmpty()) {
                keycloak.realm(realm).users().delete(existingUser.getId());
            } else {
                throw new DuplicateEntryException("Email already exists");
            }
        } else {
            Optional<SystemUser> selectedSystemUserFromAuthService =
                    systemUserRepo.findByEmail(dto.getEmail());

            if (selectedSystemUserFromAuthService.isPresent()) {
                Optional<Otp> selectedOtp =
                        otpRepo.findBySystemUserId(selectedSystemUserFromAuthService.get().getUserId());
                if (selectedOtp.isPresent()) {
                    otpRepo.deleteById(selectedOtp.get().getPropertyId());
                }
                systemUserRepo.deleteById(selectedSystemUserFromAuthService.get().getUserId());
            }
        }

        UserRepresentation userRepresentation = mapUserRepo(dto, false, false);
        Response response = keycloak.realm(realm).users().create(userRepresentation);
        if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
            RoleRepresentation userRole = keycloak.realm(realm).roles().get("user").toRepresentation();
            userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            keycloak.realm(realm).users().get(userId).roles().realmLevel().add(Arrays.asList(userRole));
            UserRepresentation createdUser = keycloak.realm(realm).users().get(userId).toRepresentation();

            SystemUser sUser = SystemUser.builder()
                    .userId(userId)
                    .keycloakId(createdUser.getId())
                    .firstName(dto.getFirstName())
                    .lastName(dto.getLastName())
                    .email(dto.getEmail())
                    .contact(dto.getContact())
                    .isActive(false)
                    .isAccountNonExpired(true)
                    .isAccountNonLocked(true)
                    .isCredentialsNonExpired(true)
                    .isEnabled(false)
                    .isEmailVerified(false)
                    .createdAt(new Date().toInstant())
                    .updatedAt(new Date().toInstant())
                    .build();

            SystemUser savedUser = systemUserRepo.save(sUser);
            Otp createdOtp = Otp.builder()
                    .propertyId(UUID.randomUUID().toString())
                    .code(otpGenerator.generateOtp(5))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .isVerified(false)
                    .systemUser(savedUser)
                    .attempts(0)
                    .build();
            otpRepo.save(createdOtp);
            emailService.sendUserSignupVerificationCode(dto.getEmail(), "Verify your email", createdOtp.getCode(), dto.getFirstName());
        }

    }

    private UserRepresentation mapUserRepo(SystemUserRequestDto dto, boolean isEmailVerified, boolean isEnabled) {
        UserRepresentation user = new UserRepresentation();
        user.setEmail(dto.getEmail());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setUsername(dto.getEmail());
        user.setEnabled(isEnabled);
        user.setEmailVerified(isEmailVerified);
        List<CredentialRepresentation> credList = new ArrayList<>();
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setTemporary(false);
        cred.setValue(dto.getPassword());
        credList.add(cred);
        user.setCredentials(credList);
        return user;
    }

    @Override
    public void initializeHosts(List<SystemUserRequestDto> users) throws IOException {
        for(SystemUserRequestDto dto: users){
            Optional<SystemUser> selectedUser = systemUserRepo.findByEmail(dto.getEmail());

            if(selectedUser.isPresent()){
                continue;
            }


            String userId = "";
            String otp = "";
            Keycloak keycloak = null;

            UserRepresentation existingUser = null;
            keycloak = keycloakUtil.getKeycloakInstance();

            existingUser = keycloak.realm(realm).users().search(dto.getEmail()).stream()
                    .findFirst().orElse(null);

            if (existingUser != null) {
                Optional<SystemUser> selectedSystemUserFromAuthService =
                        systemUserRepo.findByEmail(dto.getEmail());
                if (selectedSystemUserFromAuthService.isEmpty()) {
                    keycloak.realm(realm).users().delete(existingUser.getId());
                } else {
                    throw new DuplicateEntryException("Email already exists");
                }
            } else {
                Optional<SystemUser> selectedSystemUserFromAuthService =
                        systemUserRepo.findByEmail(dto.getEmail());
                if (selectedSystemUserFromAuthService.isPresent()) {
                    Optional<Otp> selectedOtp =
                            otpRepo.findBySystemUserId(selectedSystemUserFromAuthService.get().getUserId());
                    if (selectedOtp.isPresent()) {
                        otpRepo.deleteById(selectedOtp.get().getPropertyId());
                    }
                    systemUserRepo.deleteById(selectedSystemUserFromAuthService.get().getUserId());
                }
            }

            UserRepresentation userRepresentation = mapUserRepo(dto, true, true);
            Response response = keycloak.realm(realm).users().create(userRepresentation);
            if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                RoleRepresentation userRole = keycloak.realm(realm).roles().get("host").toRepresentation();
                userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
                keycloak.realm(realm).users().get(userId).roles().realmLevel().add(Arrays.asList(userRole));
                UserRepresentation createdUser = keycloak.realm(realm).users().get(userId).toRepresentation();

                SystemUser sUser = SystemUser.builder()
                        .userId(userId)
                        .keycloakId(createdUser.getId())
                        .firstName(dto.getFirstName())
                        .lastName(dto.getLastName())
                        .email(dto.getEmail())
                        .contact(dto.getContact())
                        .isActive(true)
                        .isAccountNonExpired(true)
                        .isAccountNonLocked(true)
                        .isCredentialsNonExpired(true)
                        .isEnabled(true)
                        .isEmailVerified(true)
                        .createdAt(new Date().toInstant())
                        .updatedAt(new Date().toInstant())
                        .build();

                SystemUser savedUser = systemUserRepo.save(sUser);
                emailService.sendHostPassword(dto.getEmail(), "access system by using the above password", dto.getPassword(), dto.getFirstName());
            }
        }
    }

    @Override
    public void resend(String email, String type) {
        try{
            Optional<SystemUser> selectedUser = systemUserRepo.findByEmail(email);
            if(selectedUser.isEmpty()){
                throw new EntryNotFoundException("unable to find any users associated with the provided email address");
            }

            SystemUser systemUser = selectedUser.get();

            if(type.equalsIgnoreCase("SIGNUP")){
                if(systemUser.isEmailVerified()){
                    throw new DuplicateEntryException("The email is already activated");
                }
            }

            Otp selectedOtpObj = systemUser.getOtp();
            String code = otpGenerator.generateOtp(5);

            emailService.sendUserSignupVerificationCode(systemUser.getEmail(), "verify your email", code, systemUser.getFirstName());

            selectedOtpObj.setAttempts(0);
            selectedOtpObj.setCode(code);
            selectedOtpObj.setIsVerified(false);
            selectedOtpObj.setUpdatedAt(new Date().toInstant());
            otpRepo.save(selectedOtpObj);


        }catch(Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void forgotPasswordSendVerificationCode(String email) {
        try{
            Optional<SystemUser> selectedUser = systemUserRepo.findByEmail(email);
            if(selectedUser.isEmpty()){
                throw new EntryNotFoundException("unable to find any users associated with the provided email address");
            }

            SystemUser systemUser = selectedUser.get();

            Keycloak keycloak =null;
            keycloak = keycloakUtil.getKeycloakInstance();
            UserRepresentation existingUser =
                    keycloak.realm(realm).users().search(email).stream().findFirst().orElse(null);

            if(existingUser==null){
                throw new EntryNotFoundException("unable to find any users associated with the provided email address");
            }


            Otp selectedOtpObj = systemUser.getOtp();
            String code = otpGenerator.generateOtp(5);


            selectedOtpObj.setAttempts(0);
            selectedOtpObj.setCode(code);
            selectedOtpObj.setIsVerified(false);
            selectedOtpObj.setUpdatedAt(new Date().toInstant());
            otpRepo.save(selectedOtpObj);

            emailService.sendUserSignupVerificationCode(systemUser.getEmail(), "verify your email to reset the password", code, systemUser.getFirstName());
        }catch(Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public boolean verifyReset(String otp, String email) {
        try{
            Optional<SystemUser> selectedUser = systemUserRepo.findByEmail(email);
            if(selectedUser.isEmpty()){
                throw new EntryNotFoundException("unable to find any users associated with the provided email address");
            }

            SystemUser systemUserOb = selectedUser.get();
            Otp otpOb = systemUserOb.getOtp();

            if(otpOb.getCode().equals(otp)){
                //otpRepo.deleteById(otpOb.getPropertyId());
                otpOb.setAttempts(otpOb.getAttempts()+1);
                otpOb.setUpdatedAt(new Date().toInstant());
                otpOb.setIsVerified(true);
                otpRepo.save(otpOb);
                return true;
            }else{

                if (otpOb.getAttempts()>=5) {
                    resend(email, "PASSWORD");
                    throw new BadRequestException("you have a new verification code");

                }

                otpOb.setAttempts(otpOb.getAttempts()+1);
                otpOb.setUpdatedAt(new Date().toInstant());
                otpRepo.save(otpOb);
                return false;
            }

        }catch(Exception e){
            return false;
        }
    }

    @Override
    public boolean passwordReset(PasswordRequestDto dto) {

        Optional<SystemUser> selectedUserObj = systemUserRepo.findByEmail(dto.getEmail());
        if(selectedUserObj.isPresent()){

            SystemUser systemUser = selectedUserObj.get();
            Otp otpObj = systemUser.getOtp();
            Keycloak keycloak = keycloakUtil.getKeycloakInstance();
            List<UserRepresentation> keyCloakUsers = keycloak.realm(realm).users().search(systemUser.getEmail());
            if(!keyCloakUsers.isEmpty() && otpObj.getCode().equals(dto.getCode())){
                UserRepresentation keyCloakUser = keyCloakUsers.get(0);
                UserResource userResource = keycloak.realm(realm).users().get(keyCloakUser.getId());
                CredentialRepresentation newPass = new CredentialRepresentation();
                newPass.setType(CredentialRepresentation.PASSWORD);
                newPass.setValue(dto.getPassword());
                newPass.setTemporary(false);
                userResource.resetPassword(newPass);

                systemUser.setUpdatedAt(new Date().toInstant());
                systemUserRepo.save(systemUser);

                return true;
            }
            throw new BadRequestException("try again");
        }
        throw new EntryNotFoundException("unable to find!");

    }

    @Override
    public boolean verifyEmail(String otp, String email) {
        Optional<SystemUser> selectedUserObj = systemUserRepo.findByEmail(email);
        if(selectedUserObj.isEmpty()){
            throw new EntryNotFoundException("cant find the associated user");
        }
        SystemUser systemUser = selectedUserObj.get();
        Otp otpObj = systemUser.getOtp();

        if(otpObj.getIsVerified()){
            throw new BadRequestException("this otp has been used");
        }

        if(otpObj.getAttempts()>=5){
            resend(email, "SIGNUP");
            return false;
        }

        if(otpObj.getCode().equals(otp)){
            UserRepresentation keycloakUser = keycloakUtil.getKeycloakInstance().realm(realm)
                    .users()
                    .search(email)
                    .stream()
                    .findFirst()
                    .orElseThrow(()->new EntryNotFoundException("user not found"));

            keycloakUser.setEmailVerified(true);
            keycloakUser.setEnabled(true);

            keycloakUtil.getKeycloakInstance().realm(realm)
                    .users().get(keycloakUser.getId()).update(keycloakUser);

            systemUser.setEmailVerified(true);
            systemUser.setEnabled(true);
            systemUser.setActive(true);

            systemUserRepo.save(systemUser);

            otpObj.setIsVerified(true);
            otpObj.setAttempts(otpObj.getAttempts()+1);

            otpRepo.save(otpObj);

            return true;
        }else{
            if (otpObj.getAttempts()>=5) {
                resend(email, "SIGNUP");
                return false;
            }

            otpObj.setAttempts(otpObj.getAttempts()+1);
            otpRepo.save(otpObj);
        }
        return false;

    }

    @Override
    public Object userLogin(RequestLoginDto dto) {
        Optional<SystemUser> selectedUserObj = systemUserRepo.findByEmail(dto.getEmail());

        if(selectedUserObj.isEmpty()){
            throw new EntryNotFoundException("cant find the associated user");
        }

        SystemUser systemUser = selectedUserObj.get();
        if(!systemUser.isEmailVerified()){
            resend(dto.getEmail(), "SIGNUP");
            throw new UnAuthorizedException("please verify email");
        }

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("client_id", clientId);
        requestBody.add("grant_type", OAuth2Constants.PASSWORD);
        requestBody.add("username", dto.getEmail());
        requestBody.add("client_secret", secret);
        requestBody.add("password", dto.getPassword());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Object> response = restTemplate.postForEntity(KeyCloakApiUrl, requestBody, Object.class);
        return response.getBody();
    }

    @Override
    public ResponseUserDetailsDto getUserDetails(String email) {
        Optional<SystemUser> byEmail = systemUserRepo.findByEmail(email);
        if (byEmail.isEmpty()) {
            throw new EntryNotFoundException("User was not found");
        }

        SystemAvatar systemUserAvatar = byEmail.get().getSystemAvatar();

        return ResponseUserDetailsDto.builder()
                .email(byEmail.get().getEmail())
                .firstName(byEmail.get().getFirstName())
                .lastName(byEmail.get().getLastName())
                .resourceUrl(systemUserAvatar != null ? fileDataExtractor.byteArrayToString(systemUserAvatar.getResourceUrl()) : null)
                .build();
    }

    @Override
    public void updateUserDetails(String email, UserUpdateRequestDto data) {
        Optional<SystemUser> byEmail = systemUserRepo.findByEmail(email);
        if (byEmail.isEmpty()) {
            throw new EntryNotFoundException("User was not found");
        }

        byEmail.get().setFirstName(data.getFirstname());
        byEmail.get().setLastName(data.getLastName());
        systemUserRepo.save(byEmail.get());
    }
}