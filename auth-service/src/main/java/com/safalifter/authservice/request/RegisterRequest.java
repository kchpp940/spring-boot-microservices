package com.safalifter.authservice.request;

import lombok.Getter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Getter
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 6, message = "Username must be at least 6 characters")
    private String username;

    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$",
            message = "Password must be at least 8 characters and contain at least one letter and one number")
    @NotNull(message = "Password is required")
    private String password;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;
}
