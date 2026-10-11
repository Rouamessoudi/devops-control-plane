package com.dcp.auth.web;

import jakarta.servlet.http.HttpServletRequest;

final class ClientIp {

    private ClientIp() {
    }

    static String of(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        if (address != null && address.length() > 64) {
            return address.substring(0, 64);
        }
        return address;
    }
}
