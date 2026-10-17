package com.k8bas.skyblockutility.update;

/**
 * An update staged for install when the game exits (REQ-UPD-19's pending install, written by T4.3): kept in the state file
 * so the next launch can finish, re-arm or clear it.
 *
 * @param version   the staged version, e.g. "1.4.0"
 * @param fileName  the jar's file name
 * @param sha256    its checked SHA-256, lowercase hex
 * @param stagedAtMs when it was staged, epoch milliseconds
 */
public record PendingInstall(String version, String fileName, String sha256, long stagedAtMs) {
}
