package com.example;

import reactor.core.publisher.Mono;

import org.springframework.security.core.Authentication;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class IndexController {

	private final ReactiveFindByIndexNameSessionRepository<?> sessionRepository;

	IndexController(ReactiveFindByIndexNameSessionRepository<?> sessionRepository) {
		this.sessionRepository = sessionRepository;
	}

	@GetMapping("/")
	Mono<String> index(Model model, Authentication authentication) {
		return this.sessionRepository.findByPrincipalName(authentication.getName())
			.doOnNext((sessions) -> model.addAttribute("sessions", sessions.values()))
			.thenReturn("index");
	}

}
