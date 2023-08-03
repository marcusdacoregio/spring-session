package com.example;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisIndexedWebSession;

@Configuration(proxyBeanMethods = false)
@EnableRedisIndexedWebSession
public class SessionConfig {

}
