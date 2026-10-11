package com.dcp.dockerops;

import com.dcp.common.error.ApiException;

import java.util.Arrays;

final class FixedCommands {

    static final String[] MAVEN_TEST = {"mvn", "-B", "-f", "/workspace/pom.xml", "test"};
    static final String[] NPM_TEST = {"npm", "test", "--if-present"};
    static final String MAVEN_IMAGE = "maven:3.9.11-eclipse-temurin-21";
    static final String NPM_IMAGE = "node:22-bookworm-slim";

    private FixedCommands() {
    }

    static void check(String image, String[] command) {
        boolean maven = MAVEN_IMAGE.equals(image) && Arrays.equals(command, MAVEN_TEST);
        boolean npm = NPM_IMAGE.equals(image) && Arrays.equals(command, NPM_TEST);
        if (!maven && !npm) {
            throw ApiException.badRequest("Container command is not allowed");
        }
    }
}
