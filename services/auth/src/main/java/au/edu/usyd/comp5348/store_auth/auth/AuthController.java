package au.edu.usyd.comp5348.store_auth.auth;

import au.edu.usyd.comp5348.store_auth.auth.dto.LoginRequest;
import au.edu.usyd.comp5348.store_auth.auth.dto.SignupRequest;
import au.edu.usyd.comp5348.store_auth.user.User;
import au.edu.usyd.comp5348.store_auth.user.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final Duration cookieLifetime;

    public AuthController(
            AuthenticationManager authManager,
            JwtService jwtService,
            UserService userService,
            @Value("${auth.jwt.expires-minutes}") long cookieMinutes
    ) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userService = userService;
        this.cookieLifetime = Duration.ofMinutes(cookieMinutes);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        var authToken = new UsernamePasswordAuthenticationToken(req.username(), req.password());
        try {
            var result = authManager.authenticate(authToken);
            return buildAuthSuccess(result.getName(), HttpStatus.OK, "login ok");
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "bad credentials"));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest req) {
        User user = userService.register(req.username(), req.password());
        return buildAuthSuccess(user.getUsername(), HttpStatus.CREATED, "signup ok");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        var cookie = buildCookie("", Duration.ZERO);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("message", "logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@CookieValue(value = ACCESS_TOKEN_COOKIE, required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            var jwt = com.auth0.jwt.JWT.decode(token);
            var username = jwt.getSubject();
            return ResponseEntity.ok(Map.of("username", username));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid token"));
        }
    }

    private ResponseEntity<Map<String, Object>> buildAuthSuccess(String username, HttpStatus status, String message) {
        String token = jwtService.issue(username);
        var cookie = buildCookie(token, cookieLifetime);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("message", message, "username", username));
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
