package mg.galaxam.sirh.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url:}")
    private String rawUrl;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        String url = rawUrl;
        if (!StringUtils.hasText(url)) {
            url = System.getenv("SPRING_DATASOURCE_URL");
        }
        if (!StringUtils.hasText(url)) {
            url = System.getenv("DATABASE_URL");
        }

        url = normalizeJdbcUrl(url);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setDriverClassName(driverClassName);
        if (StringUtils.hasText(username)) {
            dataSource.setUsername(username);
        }
        if (StringUtils.hasText(password)) {
            dataSource.setPassword(password);
        }
        return dataSource;
    }

    private String normalizeJdbcUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String trimmedUrl = url.trim();
        if (trimmedUrl.startsWith("jdbc:")) {
            return trimmedUrl;
        }
        if (trimmedUrl.startsWith("postgres://")) {
            return "jdbc:postgresql://" + trimmedUrl.substring("postgres://".length());
        }
        if (trimmedUrl.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + trimmedUrl.substring("postgresql://".length());
        }
        return "jdbc:" + trimmedUrl;
    }
}
