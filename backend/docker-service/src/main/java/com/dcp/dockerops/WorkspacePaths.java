package com.dcp.dockerops;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class WorkspacePaths {

    private WorkspacePaths() {
    }

    static Path resolveInside(Path root, String relative) {
        String safe = ResourcePolicies.relativePath(relative, ".");
        Path base = root.toAbsolutePath().normalize();
        Path resolved = base.resolve(safe).normalize();
        if (!resolved.startsWith(base)) {
            throw ApiException.badRequest("Path must stay inside the workspace");
        }
        try {
            if (Files.exists(base) && Files.exists(resolved)) {
                Path realBase = base.toRealPath();
                Path realResolved = resolved.toRealPath();
                if (!realResolved.startsWith(realBase) || Files.isSymbolicLink(resolved)) {
                    throw ApiException.badRequest("Path must stay inside the workspace");
                }
                return realResolved;
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("Path could not be resolved");
        }
        return resolved;
    }
}
