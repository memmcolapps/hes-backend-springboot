package com.memmcol.ami.auth.service.impl;

import com.memmcol.ami.auth.service.PasswordPolicyService;
import org.springframework.stereotype.Service;

@Service
public class DefaultPasswordPolicyService implements PasswordPolicyService {

    @Override
    public boolean isPasswordValid(String rawPassword) {
        return rawPassword != null
                && rawPassword.length() >= 8
                && rawPassword.chars().anyMatch(Character::isUpperCase)
                && rawPassword.chars().anyMatch(Character::isLowerCase)
                && rawPassword.chars().anyMatch(Character::isDigit);
    }

    @Override
    public String getPolicyDescription() {
        return "Password must be at least 8 characters and contain upper, lower, and numeric characters.";
    }
}