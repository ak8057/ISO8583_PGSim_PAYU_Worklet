package com.payu.pgsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PgsimApplication {

	public static void main(String[] args) {
		SpringApplication.run(PgsimApplication.class, args);
	}

}
