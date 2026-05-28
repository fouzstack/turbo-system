package com.nexus;

/**
 * Log levels for Nexus.
 */
public enum NexusLogLevel {
    /** Errors only */
    ERROR,
    /** Warnings and errors */
    WARN,
    /** General information (default) */
    INFO,
    /** Detailed debugging */
    DEBUG,
    /** Everything including serialized data */
    VERBOSE
}