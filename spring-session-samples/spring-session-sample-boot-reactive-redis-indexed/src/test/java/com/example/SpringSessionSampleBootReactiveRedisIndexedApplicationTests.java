package com.example;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class SpringSessionSampleBootReactiveRedisIndexedApplicationTests {

	WebDriver driver;

	@LocalServerPort
	int serverPort;

	@BeforeEach
	void setup() {
		this.driver = new HtmlUnitDriver();
	}

	@AfterEach
	void tearDown() {
		this.driver.quit();
	}

	@Test
	void indexWhenLoginThenShowSessionIds() {
		LoginPage login = HomePage.go(this.driver, this.serverPort);
		login.assertAt();
		HomePage home = login.form().login(HomePage.class);
		assertThat(home.attributes()).hasSize(1);
	}

}
