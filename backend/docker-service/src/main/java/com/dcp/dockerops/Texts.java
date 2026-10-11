package com.dcp.dockerops;

final class Texts {

    static final int LOG_CAP = 200_000;

    private Texts() {
    }

    static String cap(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= LOG_CAP) {
            return value;
        }
        return value.substring(value.length() - LOG_CAP);
    }

    static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    static void appendCapped(StringBuilder builder, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        builder.append(chunk);
        if (builder.length() > LOG_CAP) {
            builder.delete(0, builder.length() - LOG_CAP);
        }
    }
}
