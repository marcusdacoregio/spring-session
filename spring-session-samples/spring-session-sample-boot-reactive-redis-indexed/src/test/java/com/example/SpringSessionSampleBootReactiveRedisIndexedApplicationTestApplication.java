package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
public class SpringSessionSampleBootReactiveRedisIndexedApplicationTestApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpringSessionSampleBootReactiveRedisIndexedApplication::main)
			.with(TestcontainersConfig.class)
			.run(args);
	}

}
