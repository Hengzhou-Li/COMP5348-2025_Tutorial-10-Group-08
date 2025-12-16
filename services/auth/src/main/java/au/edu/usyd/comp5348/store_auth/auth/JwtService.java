package au.edu.usyd.comp5348.store_auth.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final String issuer;
    private final Algorithm alg;
    private final long expiresMinutes;

    public JwtService(
            @Value("${auth.jwt.issuer}") String issuer,
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.expires-minutes}") long expiresMinutes
    ) {
        this.issuer = issuer;
        this.alg = Algorithm.HMAC256(secret);
        this.expiresMinutes = expiresMinutes;
    }

    public String issue(String username) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(expiresMinutes * 60)))
                .withSubject(username)
                .withClaim("role", "CUSTOMER")
                .sign(alg);
    }
}
