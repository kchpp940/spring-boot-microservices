package com.safalifter.authservice.service;


import com.safalifter.authservice.client.adapter.UserServiceClientAdapter;
import com.safalifter.authservice.dto.UserDto;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserServiceClientAdapter userServiceClientAdapter;

    public CustomUserDetailsService(UserServiceClientAdapter userServiceClientAdapter) {
        this.userServiceClientAdapter = userServiceClientAdapter;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDto user = userServiceClientAdapter.getUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return new CustomUserDetails(user);
    }
}