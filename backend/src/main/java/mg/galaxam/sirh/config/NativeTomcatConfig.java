package mg.galaxam.sirh.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "galaxam.native.nio2", havingValue = "true")
public class NativeTomcatConfig {

    @Bean
    TomcatServletWebServerFactory nativeTomcatServletWebServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        return factory;
    }
}
