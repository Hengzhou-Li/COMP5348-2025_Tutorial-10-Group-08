package au.edu.usyd.comp5348.store_auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 與 DataSeeder 以及登入驗證使用同一顆 encoder
        return new BCryptPasswordEncoder();
    }
}
