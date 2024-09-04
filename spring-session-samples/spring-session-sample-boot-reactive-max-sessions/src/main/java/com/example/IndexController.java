/*
 * Copyright 2014-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import reactor.core.publisher.Mono;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.security.SpringSessionBackedReactiveSessionRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
class IndexController {

	private final ReactiveFindByIndexNameSessionRepository<?> sessionRepository;

	private final SpringSessionBackedReactiveSessionRegistry<?> sessionRegistry;

	IndexController(ReactiveFindByIndexNameSessionRepository<?> sessionRepository, SpringSessionBackedReactiveSessionRegistry<?> sessionRegistry) {
		this.sessionRepository = sessionRepository;
		this.sessionRegistry = sessionRegistry;
	}

	@GetMapping("/")
	Mono<String> index(Model model, Authentication authentication) {
		return this.sessionRepository.findByPrincipalName(authentication.getName())
			.doOnNext((sessions) -> model.addAttribute("sessions", sessions.values()))
			.thenReturn("index");
	}

	@GetMapping("/{sessionId}")
	Mono<String> invalidate(@PathVariable String sessionId) {
		return this.sessionRegistry.getSessionInformation(sessionId)
				.doOnNext((s) -> {
					SecurityContext principal = (SecurityContext) s.getPrincipal();
					System.out.println(principal.getAuthentication().getName());
				})
				.map((s) -> "index");
	}

}
