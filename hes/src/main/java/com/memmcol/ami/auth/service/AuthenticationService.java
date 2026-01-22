package com.memmcol.ami.auth.service;

import com.memmcol.ami.auth.dto.AuthenticationResult;
import com.memmcol.ami.auth.dto.LoginRequest;

public interface AuthenticationService {

    AuthenticationResult authenticate(LoginRequest request);

}