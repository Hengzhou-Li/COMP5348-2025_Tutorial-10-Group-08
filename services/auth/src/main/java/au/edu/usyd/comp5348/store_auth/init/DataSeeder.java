package au.edu.usyd.comp5348.store_auth.init;

import au.edu.usyd.comp5348.store_auth.user.User;
import au.edu.usyd.comp5348.store_auth.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(UserRepository repo, PasswordEncoder passwordEncoder) {
        return args -> {
            createIfMissing(repo, passwordEncoder, "customer", "COMP5348");
            createIfMissing(repo, passwordEncoder, "demo", "DemoPass123");
        };
    }

    private void createIfMissing(UserRepository repo, PasswordEncoder encoder, String username, String rawPassword) {
        if (repo.findByUsername(username).isPresent()) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setEnabled(true);
        repo.save(user);
        System.out.printf("Seeded demo user: %s / %s%n", username, rawPassword);
    }
}
