package au.edu.usyd.comp5348.store_auth.config;

import org.springframework.beans.factory.annotation.Value;                           // ← 加
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;                              // ← 加
import org.springframework.web.cors.CorsConfigurationSource;                     // ← 加
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;             // ← 加

import java.util.List;                                                           // ← 加

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);

        http.cors(cors -> {}); // 啟用下面的 CorsConfigurationSource

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                        "/api/auth/login",
                        "/api/auth/signup",
                        "/api/auth/logout",
                        "/api/auth/me",
                        "/actuator/health",
                        "/h2-console/**"
                ).permitAll()
                .anyRequest().authenticated());

        // H2 console（開發用）
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    // CORS：允許前端 http://localhost:5173 帶 Cookie
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") List<String> allowed) {
        CorsConfiguration cfg = new CorsConfiguration();
        if (allowed == null || allowed.isEmpty()) {
            allowed = List.of(
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:5174",
                    "http://127.0.0.1:5174"
            );
        }
        cfg.setAllowedOrigins(allowed);
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type","Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    // 提供 AuthenticationManager（如果你的 AuthController 有需要）
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
