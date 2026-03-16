package com.memmcol.ami.auth.service;

public interface PasswordPolicyService {

    boolean isPasswordValid(String rawPassword);

    String getPolicyDescription();
}