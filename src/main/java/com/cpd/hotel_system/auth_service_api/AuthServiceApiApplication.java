package com.cpd.hotel_system.auth_service_api;

import com.cpd.hotel_system.auth_service_api.dto.request.SystemUserRequestDto;
import com.cpd.hotel_system.auth_service_api.service.SystemUserService;
import com.cpd.hotel_system.auth_service_api.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.util.Arrays;

@SpringBootApplication
@EnableDiscoveryClient
@RequiredArgsConstructor
public class AuthServiceApiApplication implements CommandLineRunner {

	private final SystemUserService service;
	private final PasswordGenerator generator;

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApiApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		SystemUserRequestDto user1= new SystemUserRequestDto(
				"ABC",
				"XYZ",
				"abc@gmail.com",
				generator.generatePassword(),
				"0714911257"
		);
		SystemUserRequestDto user2= new SystemUserRequestDto(
				"STY",
				"WTY",
				"sty@gmail.com",
				generator.generatePassword(),
				"0714875865"
		);
		service.initializeHosts(Arrays.asList(user1, user2));
	}
//	https://github.com/cpd-3/auth-service-api
//	1.22
}
