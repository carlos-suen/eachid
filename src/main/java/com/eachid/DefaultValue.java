// ==================== DefaultValue.java ====================
package com.eachid;

/**
 * EachId Default Configuration Values
 *
 * <p>Centralized location for all default configuration values used by
 * EachId and related components. Eliminates code duplication and ensures
 * consistency across the codebase.</p>
 */
public final class DefaultValue {

    // ==================== Core Configuration Defaults ====================

    /** Default epoch start date (2025-01-01) */
    public static final String DEFAULT_EPOCH = "2025-01-01";

    /** Default time step in milliseconds (100ms) */
    public static final long DEFAULT_STEP_MS = 100L;

    /** Default number of bits for timestamp (35 bits) */
    public static final long DEFAULT_TIMESTAMP_BITS = 35L;

    /** Default number of bits for datacenter ID (0 bits - disabled by default) */
    public static final long DEFAULT_DATACENTER_ID_BITS = 0L;

    /** Default number of bits for worker ID (8 bits) */
    public static final long DEFAULT_WORKER_ID_BITS = 8L;

    /** Default number of bits for sequence number (20 bits) */
    public static final long DEFAULT_SEQUENCE_BITS = 20L;

    /** Default clock backward tolerance threshold in milliseconds (1000ms) */
    public static final long DEFAULT_CLOCK_BACKWARD_THRESHOLD_MS = 1000L;

    /** Default custom worker ID (0) */
    public static final long DEFAULT_CUSTOM_WORKER_ID = 0L;

    /** Default custom datacenter ID (0) */
    public static final long DEFAULT_DATACENTER_ID = 0L;

    // ==================== Validation Ranges ====================

    /** Minimum allowed timestamp bits */
    public static final long MIN_TIMESTAMP_BITS = 20L;

    /** Maximum allowed timestamp bits */
    public static final long MAX_TIMESTAMP_BITS = 62L;

    /** Minimum allowed sequence bits */
    public static final long MIN_SEQUENCE_BITS = 1L;

    /** Maximum allowed sequence bits */
    public static final long MAX_SEQUENCE_BITS = 35L;



    // ==================== Utility Methods ====================

    /** Prevent instantiation */
    private DefaultValue() {
        throw new AssertionError("Cannot instantiate DefaultValue class");
    }

    /**
     * Returns default configuration summary
     */
    public static String getDefaultConfigSummary() {
        return String.format(
                "Default Config: Epoch=%s, StepMs=%d, Bits=%d(ts)+%d(dc)+%d(wk)+%d(seq)",
                DEFAULT_EPOCH, DEFAULT_STEP_MS,
                DEFAULT_TIMESTAMP_BITS, DEFAULT_DATACENTER_ID_BITS,
                DEFAULT_WORKER_ID_BITS, DEFAULT_SEQUENCE_BITS
        );
    }
}
