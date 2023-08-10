package org.springframework.session.jdbc.config.annotation.web.server;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({ java.lang.annotation.ElementType.TYPE })
@Documented
@Import(R2dbcWebSessionConfiguration.class)
public @interface EnableR2dbcWebSession {

}
