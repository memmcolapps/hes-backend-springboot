package com.memmcol.hes.nettyUtils;

import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
class DlmsConfig {
    private int clientId;
    private int serverId;
    private Authentication auth;
    private String password;
    private InterfaceType interfaceType;
}

