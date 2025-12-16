package au.edu.usyd.comp5348.store_auth.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username can contain letters, numbers, dot, hyphen or underscore")
        String username,

        @NotBlank
        @Size(min = 8, max = 100)
        String password
) {
}
