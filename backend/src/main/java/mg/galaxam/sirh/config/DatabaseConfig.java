package mg.galaxam.sirh.config;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

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

        String finalUsername = username;
        String finalPassword = password;
        String jdbcUrl = url;

        if (StringUtils.hasText(url)) {
            String uriString = url.trim();
            if (uriString.startsWith("jdbc:")) {
                uriString = uriString.substring(5);
            }

            if (uriString.contains("@")) {
                try {
                    URI uri = URI.create(uriString);
                    if (uri.getUserInfo() != null) {
                        String[] userInfo = uri.getUserInfo().split(":", 2);
                        if (!StringUtils.hasText(finalUsername)) {
                            finalUsername = userInfo[0];
                        }
                        if (userInfo.length > 1 && !StringUtils.hasText(finalPassword)) {
                            finalPassword = userInfo[1];
                        }
                    }
                    String host = uri.getHost();
                    int port = uri.getPort();
                    String path = uri.getPath();

                    StringBuilder sb = new StringBuilder("jdbc:postgresql://");
                    sb.append(host);
                    if (port > 0) {
                        sb.append(":").append(port);
                    }
                    if (StringUtils.hasText(path)) {
                        sb.append(path);
                    }
                    jdbcUrl = sb.toString();
                } catch (Exception e) {
                    jdbcUrl = normalizeJdbcUrl(url);
                }
            } else {
                jdbcUrl = normalizeJdbcUrl(url);
            }
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setDriverClassName(driverClassName);
        if (StringUtils.hasText(finalUsername)) {
            dataSource.setUsername(finalUsername);
        }
        if (StringUtils.hasText(finalPassword)) {
            dataSource.setPassword(finalPassword);
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
