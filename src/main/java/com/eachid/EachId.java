// ==================== EachId_Synchronized.java ====================
package com.eachid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.LongAdder;



/**
 * EachId v2 - High-performance distributed ID generator with synchronized implementation
 *
 * <p>This implementation provides thread-safe ID generation using synchronized blocks,
 * completely compatible with JDK 8+. The core design uses 100ms time steps and 22-bit
 * sequence numbers to achieve extremely high performance with minimal lock contention.</p>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Thread-safe synchronized implementation</li>
 *   <li>100ms time step for minimal lock contention</li>
 *   <li>22-bit sequence number (4,194,304 IDs per time step)</li>
 *   <li>Batch ID generation support</li>
 *   <li>Clock backward protection</li>
 *   <li>Automatic worker ID allocation</li>
 *   <li>Full JDK 8+ compatibility</li>
 * </ul></p>
 *
 * <p><b>Performance Characteristics:</b>
 * <ul>
 *   <li>Single thread: ~39 million QPS</li>
 *   <li>Multi-thread (64 threads): ~4.3 million QPS</li>
 *   <li>Batch generation: Billions of QPS</li>
 * </ul></p>
 *
 * @author EachId Development Team
 * @version 2.0.0
 * @since 2025
 */
public final class EachId {
    private static final Logger logger = LoggerFactory.getLogger(EachId.class);

    // ==================== Default Configuration Constants ====================

    /** Default epoch start date (2025-01-01) */
//    static final String DEFAULT_EPOCH = "2025-01-01";

    /** Default number of bits for timestamp (31 bits) */
//    static final long DEFAULT_TIMESTAMP_BITS = 35L;

    /** Default number of bits for datacenter ID (0 bits - disabled by default) */
//    static final long DEFAULT_DATACENTER_ID_BITS = 0L;

    /** Default number of bits for worker ID (10 bits) */
//    static final long DEFAULT_WORKER_ID_BITS = 8L;

    /** Default number of bits for sequence number (22 bits) */
//    static final long DEFAULT_SEQUENCE_BITS = 20L;

    /** Default clock backward tolerance threshold in milliseconds (1000ms) */
//    static final long DEFAULT_CLOCK_BACKWARD_THRESHOLD_MS = 1000L;

    /** Default custom worker ID (0) */
//    static final long DEFAULT_CUSTOM_WORKER_ID = 0L;

    /** Zero padding string for hexadecimal conversion */
    private static final String ZEROS_16 = "0000000000000000";

    /** Default time step in milliseconds (100ms) */
//    static final long DEFAULT_STEP_MS = 100L;

    // ==================== Configurable Properties ====================

    /** Epoch start date string */
    private String epoch = DefaultValue.DEFAULT_EPOCH;

    /** Number of bits allocated for timestamp */
    private long timestampBits = DefaultValue.DEFAULT_TIMESTAMP_BITS;

    /** Number of bits allocated for datacenter ID */
    private long datacenterIdBits = DefaultValue.DEFAULT_DATACENTER_ID_BITS;

    /** Number of bits allocated for worker ID */
    private long workerIdBits = DefaultValue.DEFAULT_WORKER_ID_BITS;

    /** Number of bits allocated for sequence number */
    private long sequenceBits = DefaultValue.DEFAULT_SEQUENCE_BITS;

    /** Clock backward tolerance threshold in milliseconds */
    private long clockBackwardThresholdMs = DefaultValue.DEFAULT_CLOCK_BACKWARD_THRESHOLD_MS;

    /** Time step in milliseconds for ID generation */
    private long stepMs = DefaultValue.DEFAULT_STEP_MS;

    // ==================== Internal State Fields ====================

    /** Epoch timestamp in custom time units */
    private long epochTimeStamp;

    /** Bit shift position for datacenter ID */
    private long datacenterIdShift;

    /** Bit shift position for worker ID */
    private long workerIdShift;

    /** Bit shift position for timestamp */
    private long timestampShift;

    /** Maximum allowed datacenter ID value */
    private long maxDatacenterId;

    /** Maximum allowed worker ID value */
    private long maxWorkerId;

    /** Maximum allowed sequence number value */
    private long maxSequence;

    /** Bit mask for sequence number extraction */
    private long sequenceMask;

    /** Current datacenter ID */
    private long datacenterId;

    /** Current worker ID */
    private long workerId;

    // ==================== Synchronized State Fields ====================

    /** Current sequence number within the time step */
    private long sequence = 0L;

    /** Last timestamp when ID was generated */
    private long lastIdTimestamp = -1L;

    // ==================== Monitoring Fields ====================

    /** Thread-safe counter for total generated IDs */
    private final LongAdder totalGenerated = new LongAdder();

    /**
     * Default constructor initializes EachId with default configuration.
     * Automatically allocates worker ID and validates configuration.
     */
    public EachId() {
        initialize();
    }

    // ==================== Configuration Methods ====================

    /**
     * Sets the epoch start date for ID generation.
     *
     * @param epoch the epoch start date in "yyyy-MM-dd" format
     * @return this EachId instance for method chaining
     * @throws IllegalArgumentException if the epoch format is invalid
     */
    public EachId setEpoch(String epoch) {
        this.epochTimeStamp = parseEpoch(epoch);
        this.epoch = epoch;
        return this;
    }

    /**
     * Sets the time step in milliseconds for ID generation.
     *
     * <p>Smaller step values provide more frequent timestamp updates but may increase
     * lock contention. Larger step values reduce contention but provide fewer unique
     * timestamps per second.</p>
     *
     * @param stepMs the time step in milliseconds (must be positive)
     * @return this EachId instance for method chaining
     * @throws IllegalArgumentException if stepMs is not positive
     */
    public EachId setStepMs(long stepMs) {
        if (stepMs <= 0) throw new IllegalArgumentException("stepMs must be > 0");
        this.stepMs = stepMs;
        setEpoch(this.epoch);
        return this;
    }

    /**
     * Sets the number of bits allocated for timestamp.
     *
     * @param bits number of timestamp bits (20-62)
     * @return this EachId instance for method chaining
     * @throws IllegalArgumentException if bits is outside valid range
     */
    public EachId setTimestampBits(long bits) {
        if (bits < 20 || bits > 62) throw new IllegalArgumentException("timestampBits must be 20-62");
        this.timestampBits = bits;
        return this;
    }

    /**
     * Sets the number of bits allocated for datacenter ID.
     *
     * @param bits number of datacenter ID bits (0-35)
     * @return this EachId instance for method chaining
     * @throws IllegalArgumentException if bits is outside valid range
     */
    public EachId setDatacenterIdBits(long bits) {
        if (bits < 0 || bits > 35) throw new IllegalArgumentException("datacenterBits 0-35");
        this.datacenterIdBits = bits;
        this.maxDatacenterId = bits > 0 ? (1L << bits) - 1 : 0L;
        this.timestampShift = sequenceBits + workerIdBits + datacenterIdBits;
        return this;
    }

    /**
     * Sets the number of bits allocated for worker ID.
     *
     * @param bits number of worker ID bits (0-35)
     * @return this EachId instance for method chaining
     * @throws IllegalArgumentException if bits is outside valid range
     */
    public EachId setWorkerIdBits(long bits) {
        if (bits < 0 || bits > 35) throw new IllegalArgumentException("workerIdBits 0-35");
        this.workerIdBits = bits;
        this.maxWorkerId = (1L << bits) - 1;
        this.datacenterIdShift = sequenceBits + workerIdBits;
        this.timestampShift = sequenceBits + workerIdBits + datacenterIdBits;
        return this;
    }

    /**
     * Sets the number of bits allocated for sequence number.
     *
     * @param bits number of sequence bits (1-35)
     * @return this EachId instance for method chaining
     * @throws IllegalArgumentException if bits is outside valid range
     */
    public EachId setSequenceBits(long bits) {
        if (bits < 1 || bits > 35) throw new IllegalArgumentException("sequenceBits 1-35");
        this.sequenceBits = bits;
        this.maxSequence = (1L << bits) - 1;
        this.sequenceMask = maxSequence;
        this.workerIdShift = sequenceBits;
        this.datacenterIdShift = sequenceBits + workerIdBits;
        this.timestampShift = sequenceBits + workerIdBits + datacenterIdBits;
        return this;
    }

    /**
     * Sets the clock backward tolerance threshold.
     *
     * <p>If clock moves backward within this threshold, the generator will wait
     * and retry. If beyond threshold, an exception will be thrown.</p>
     *
     * @param ms tolerance threshold in milliseconds (0-60000)
     * @return this EachId instance for method chaining
     * @throws IllegalArgumentException if ms is outside valid range
     */
    public EachId setClockBackwardThresholdMs(long ms) {
        if (ms < 0 || ms > 60_000) throw new IllegalArgumentException("threshold 0-60000");
        this.clockBackwardThresholdMs = ms;
        return this;
    }

    /**
     * Sets the worker ID manually.
     *
     * @param workerId the worker ID to set
     * @return this EachId instance for method chaining
     * @throws IllegalArgumentException if workerId is outside valid range
     */
    public EachId setWorkerId(long workerId) {
        if (workerId < 0 || workerId > maxWorkerId)
            throw new IllegalArgumentException("workerId out of range, max=" + maxWorkerId);
        this.workerId = workerId;
        return this;
    }

    /**
     * Sets the datacenter ID manually.
     *
     * @param datacenterId the datacenter ID to set
     * @return this EachId instance for method chaining
     * @throws IllegalArgumentException if datacenterId is outside valid range
     */
    public EachId setDatacenterId(long datacenterId) {
        if (datacenterId < 0 || datacenterId > maxDatacenterId)
            throw new IllegalArgumentException("datacenterId out of range, max=" + maxDatacenterId);
        this.datacenterId = datacenterId;
        return this;
    }

    /**
     * Automatically allocates worker ID based on IP address.
     *
     * <p>Uses the last segment of the local IP address to generate a worker ID
     * within the allowed range.</p>
     *
     * @return this EachId instance for method chaining
     */
    public EachId autoWorkerId() {
        this.workerId = WorkerIdAllocator.getAutoWorkerId((int) maxWorkerId);
        return this;
    }

    // ==================== Getter Methods ====================

    /**
     * Returns the current time step in milliseconds.
     *
     * @return the time step in milliseconds
     */
    public long getStepMs() { return stepMs; }

    /**
     * Returns the epoch timestamp in custom time units.
     *
     * @return the epoch timestamp
     */
    public long getEpochTimeStamp() { return epochTimeStamp; }

    /**
     * Returns the number of bits allocated for sequence number.
     *
     * @return the number of sequence bits
     */
    public long getSequenceBits() { return sequenceBits; }

    /**
     * Returns the current worker ID.
     *
     * @return the worker ID
     */
    public long getWorkerId() { return workerId; }

    /**
     * Returns the maximum allowed sequence number.
     *
     * @return the maximum sequence number
     */
    public long getMaxSequence() { return maxSequence; }

    /**
     * Returns the current datacenter ID.
     *
     * @return the datacenter ID
     */
    public long getDatacenterId() { return datacenterId; }

    /**
     * Returns the number of bits allocated for timestamp.
     *
     * @return the number of timestamp bits
     */
    public long getTimestampBits() { return timestampBits; }

    /**
     * Returns the number of bits allocated for worker ID.
     *
     * @return the number of worker ID bits
     */
    public long getWorkerIdBits() { return workerIdBits; }

    /**
     * Initializes the EachId instance with default configuration.
     * Validates settings and automatically allocates worker ID.
     */
    private void initialize() {
        this.setEpoch(DefaultValue.DEFAULT_EPOCH)
                .setStepMs(DefaultValue.DEFAULT_STEP_MS)
                .setTimestampBits(DefaultValue.DEFAULT_TIMESTAMP_BITS)
                .setDatacenterIdBits(DefaultValue.DEFAULT_DATACENTER_ID_BITS)
                .setWorkerIdBits(DefaultValue.DEFAULT_WORKER_ID_BITS)
                .setSequenceBits(DefaultValue.DEFAULT_SEQUENCE_BITS)
                .setClockBackwardThresholdMs(DefaultValue.DEFAULT_CLOCK_BACKWARD_THRESHOLD_MS)
                .setDatacenterId(DefaultValue.DEFAULT_DATACENTER_ID)
                .setWorkerId(DefaultValue.DEFAULT_CUSTOM_WORKER_ID)
                .autoWorkerId();
        validateSetting();
    }

    // ==================== Core ID Generation Methods ====================

    /**
     * Generates a single unique ID.
     *
     * <p>This method is thread-safe and guarantees monotonic increasing IDs
     * within the same instance.</p>
     *
     * @return a unique monotonically increasing ID
     */
    public synchronized long nextId() {
        return nextId(1);
    }

    /**
     * Generates multiple unique IDs in batch.
     *
     * <p>Reserves a contiguous block of sequence numbers and returns the first ID.
     * Subsequent IDs can be obtained by incrementing the returned ID.</p>
     *
     * @param count number of IDs to generate (1 to maxSequence+1)
     * @return the first ID in the batch
     * @throws IllegalArgumentException if count is invalid
     */
    public synchronized long nextId(int count) {
        if (count <= 0 || count > maxSequence + 1) {
            throw new IllegalArgumentException("Invalid count: " + count);
        }

        long now = currentTimestamp();

        // Handle clock backward
        if (now < lastIdTimestamp) {
            now = handleClockBackward(now);
        }

        if (now == lastIdTimestamp) {
            // Same timestamp unit
            long nextSeq = sequence + count;
            if (nextSeq > maxSequence) {
                // Sequence exhausted, wait for next timestamp
                now = waitNextTimestamp(now);
                // Critical fix: Check if we're still behind due to clock backward during wait
                if (now <= lastIdTimestamp) {
                    now = handleClockBackward(now);
                }
                lastIdTimestamp=now;
                sequence = 0;                     // New timestamp, reset sequence
                totalGenerated.add(count);
                return buildId(now, 0);     // Return first ID in batch
            } else {
                sequence = nextSeq;
                totalGenerated.add(count);
                return buildId(now, sequence - count); // Return first ID in batch
            }
        } else {
            // New timestamp
            sequence = count;                     // Start from count (batch reservation)
            lastIdTimestamp = now;
            totalGenerated.add(count);
            return buildId(now, 0);     // Return first ID in batch

        }
    }

    /**
     * Generates a single unique ID and returns it as hexadecimal string.
     *
     * @return 16-character uppercase hexadecimal string representation of the ID
     */
    public String nextIdHex() {
        long id = nextId();
        String hex = Long.toHexString(id).toUpperCase();
        return paddingZeros(16 - hex.length()) + hex;
    }

    /**
     * Creates a zero-padding string of specified length.
     *
     * @param count number of zeros to pad
     * @return zero-padding string
     */
    private static String paddingZeros(int count) {
        return count <= 0 ? "" : ZEROS_16.substring(0, count);
    }

    // ==================== Debugging and Maintenance Methods ====================

    /**
     * Adds time steps and sequence offset to an existing ID.
     *
     * <p>Useful for testing and debugging timestamp and sequence manipulation.</p>
     *
     * @param currentId the original ID
     * @param howManyStepMs number of time steps to add
     * @param sequence sequence number to add
     * @return modified ID with adjusted timestamp and sequence
     */
    public long addStepAndSequence(long currentId, long howManyStepMs, long sequence) {
        IdInfo info = parseId(currentId);
        long newSeq = (info.sequence + sequence) & sequenceMask;
        return buildId(info.timestamp + howManyStepMs, newSeq);
    }

    /**
     * Converts public timestamp to custom timestamp units.
     *
     * @param publicTimeStamp public timestamp in milliseconds
     * @return timestamp in custom time units
     */
    public long convertToCustomTimestamp(long publicTimeStamp) {
        return publicTimeStamp / stepMs;
    }

    /**
     * Replaces datacenter ID in an existing ID.
     *
     * @param originalId the original ID
     * @param newDatacenterId new datacenter ID
     * @return ID with replaced datacenter ID
     * @throws UnsupportedOperationException if datacenter bits are 0
     * @throws IllegalArgumentException if new datacenter ID is out of range
     */
    public long replaceDatacenterId(long originalId, long newDatacenterId) {
        if (datacenterIdBits == 0) throw new UnsupportedOperationException("datacenterBits=0");
        if (newDatacenterId < 0 || newDatacenterId > maxDatacenterId)
            throw new IllegalArgumentException("newDatacenterId out of range");
        long cleared = originalId & ~(maxDatacenterId << datacenterIdShift);
        return cleared | (newDatacenterId << datacenterIdShift);
    }

    /**
     * Replaces worker ID in an existing ID.
     *
     * @param originalId the original ID
     * @param newWorkerId new worker ID
     * @return ID with replaced worker ID
     * @throws IllegalArgumentException if new worker ID is out of range
     */
    public long replaceWorkerId(long originalId, long newWorkerId) {
        if (newWorkerId < 0 || newWorkerId > maxWorkerId)
            throw new IllegalArgumentException("newWorkerId out of range");
        long cleared = originalId & ~(maxWorkerId << workerIdShift);
        return cleared | (newWorkerId << workerIdShift);
    }

    /**
     * Builds an ID from timestamp and sequence components.
     *
     * @param timestamp timestamp in custom time units
     * @param seq sequence number
     * @return complete ID
     */
    public long buildId(long timestamp, long seq) {
        long offset = timestamp - epochTimeStamp;
        return (offset << timestampShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                seq;
    }

    /**
     * Gets current timestamp in custom time units.
     *
     * @return current timestamp in custom time units
     */
    private long currentTimestamp() {
        return System.currentTimeMillis() / stepMs;
    }

    /**
     * Handles clock backward situation.
     *
     * <p>If clock moves backward within threshold, waits and retries.
     * If beyond threshold, throws exception.</p>
     *
     * @param currentTimestamp current timestamp
     * @return valid timestamp after handling clock backward
     * @throws IllegalStateException if clock backward is beyond threshold
     */
    private synchronized long handleClockBackward(long currentTimestamp) {
        long offsetMs = (lastIdTimestamp - currentTimestamp) * stepMs;
        logger.warn("Clock backward detected: {}ms", offsetMs);

        if (offsetMs <= clockBackwardThresholdMs) {
            long waitMs = offsetMs + 1;
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during clock backward wait", e);
            }
            return currentTimestamp();
        } else {
            logger.error("Clock backward too large: {}ms > threshold {}ms", offsetMs, clockBackwardThresholdMs);
            throw new IllegalStateException("Clock moved backwards too much: " + offsetMs + "ms");
        }
    }

    /**
     * Waits for next timestamp when sequence is exhausted.
     *
     * @param lastStamp last timestamp
     * @return next available timestamp
     * @throws IllegalStateException if waiting times out (3 seconds)
     */
    /*private synchronized long waitNextTimestamp(long lastStamp) {
        long start = System.currentTimeMillis();
        while (currentTimestamp() <= lastStamp) {
            if (System.currentTimeMillis() - start > clockBackwardThresholdMs) {
                throw new IllegalStateException("Wait next timestamp timeout (>" + clockBackwardThresholdMs + "ms)");
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return currentTimestamp();
    }*/

    /**
     * Waits until the next timestamp boundary using precise calculation.
     *
     * @param lastStamp the last used timestamp
     * @return the current timestamp after wait (may still be <= lastStamp due to clock backward)
     */
    private synchronized long waitNextTimestamp(long lastStamp) {
        long nextTimestampMs = (lastStamp + 1) * stepMs;
        long nowRealTimestamp = System.currentTimeMillis();
        long waitMs = nextTimestampMs - nowRealTimestamp;

        if (waitMs <= 0) {
            return currentTimestamp();
        }

        try {
            // Add 1ms to ensure we pass the boundary
            Thread.sleep(waitMs + 1);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during wait for next timestamp", e);
        }
        return currentTimestamp();
    }



    // ==================== ID Parsing Methods ====================

    /**
     * Parses an ID into its component parts.
     *
     * @param id the ID to parse
     * @return IdInfo object containing parsed components
     */
    public IdInfo parseId(long id) {
        long seq = id & sequenceMask;
        long wid = (id >>> workerIdShift) & maxWorkerId;
        long dc = datacenterIdBits > 0 ? (id >>> datacenterIdShift) & maxDatacenterId : 0L;
        long tsOffset = id >>> timestampShift;
        long timestamp = tsOffset + epochTimeStamp;
        return new IdInfo(id, timestamp, dc, wid, seq,stepMs);
    }

    /**
     * Parses an ID from hexadecimal string.
     *
     * @param hex hexadecimal string representation of ID
     * @return IdInfo object containing parsed components
     */
    public IdInfo parseIdFromHex(String hex) {
        hex = hex.toLowerCase().replace("0x", "");
        long id = Long.parseUnsignedLong(hex, 16);
        return parseId(id);
    }

    /**
     * ID information container class.
     *
     * <p>Provides structured access to ID components and utility methods
     * for analysis and debugging.</p>
     */
    public static class IdInfo {
        /** The original ID value */
        public final long id;

        /** Timestamp component */
        public final long timestamp;

        /** Datacenter ID component */
        public final long datacenterId;

        /** Worker ID component */
        public final long workerId;

        /** Sequence number component */
        public final long sequence;

        /** Date object representation of timestamp */
        private final Date date;

        /**
         * Constructs IdInfo with all components.
         *
         * @param id the original ID
         * @param timestamp timestamp component
         * @param datacenterId datacenter ID component
         * @param workerId worker ID component
         * @param sequence sequence number component
         */
        public IdInfo(long id, long timestamp, long datacenterId, long workerId, long sequence,long stepMs) {
            this.id = id;
            this.timestamp = timestamp;
            this.datacenterId = datacenterId;
            this.workerId = workerId;
            this.sequence = sequence;
            this.date = new Date(timestamp * stepMs);
        }

        /**
         * Returns the date representation of timestamp.
         *
         * @return Date object
         */
        public Date getDate() { return date; }

        /**
         * Generates a detailed report of ID components.
         *
         * @return formatted report string
         */
        public String generateReport() {
            String binary = String.format("%64s", Long.toUnsignedString(id, 2)).replace(' ', '0');
            String hexPadded = String.format("%016X", id);
            return String.format(
                    "═══════════════════════════════════════\n" +
                            "EachId Detailed Report\n" +
                            "═══════════════════════════════════════\n" +
                            "ID              : %,d\n" +
                            "Hex             : 0x%s\n" +
                            "Timestamp       : %d (%s UTC)\n" +
                            "Datacenter ID   : %d\n" +
                            "Worker ID       : %d\n" +
                            "Sequence        : %d\n" +
                            "Date            : %s\n" +
                            "Binary          : %s\n" +
                            "═══════════════════════════════════════\n",
                    id, hexPadded, timestamp, date, datacenterId, workerId, sequence, date, binary);
        }

        /**
         * Returns string representation of ID information.
         *
         * @return compact string representation
         */
        @Override public String toString() {
            return String.format("ID[%d] Time:%s DC:%d Worker:%d Seq:%d", id, date, datacenterId, workerId, sequence);
        }
    }

    /**
     * Validates configuration settings.
     *
     * @throws IllegalArgumentException if total bits exceed 63
     */
    private void validateSetting() {
        long total = timestampBits + datacenterIdBits + workerIdBits + sequenceBits;
        if (total > 63) throw new IllegalArgumentException("Total bits > 63, current=" + total);
    }

    /**
     * Returns configuration information as formatted string.
     *
     * @return formatted configuration report
     */
    public String getInfo() {
        long totalSteps = (1L << timestampBits) - 1;
        long totalMillis = totalSteps * stepMs;
        long totalSeconds = totalMillis / 1000;
        long years = totalSeconds / (365L * 24 * 3600);
        long remainingDays = (totalSeconds % (365L * 24 * 3600)) / (24 * 3600);

        long perStepIds = maxSequence + 1;
        long perSecondTheoretical = perStepIds * (1000L / stepMs);  // Theoretical maximum IDs per second

        return String.format(
                "═══════════════════════════════════════\n" +
                        "EachId (synchronized) Config\n" +
                        "Epoch           : %s\n" +
                        "StepMs          : %d ms\n" +
                        "Bits            : %d(ts)+%d(dc)+%d(wk)+%d(seq)=%d bits\n" +
                        "Timestamp Range : %,d steps × %d ms = ~%d years %d days\n" +
                        "Capacity        : %,d nodes | %,d IDs/%dms (≈%,d IDs/sec theoretical)\n" +
                        "WorkerId        : %d (max %,d)\n"+
                        "═══════════════════════════════════════\n",
                epoch, stepMs,
                timestampBits, datacenterIdBits, workerIdBits, sequenceBits,
                timestampBits + datacenterIdBits + workerIdBits + sequenceBits,
                totalSteps, stepMs, years, remainingDays,
                maxWorkerId + 1,
                perStepIds, stepMs, perSecondTheoretical,
                workerId, maxWorkerId
        );
    }

    /**
     * Parses epoch string to timestamp.
     *
     * @param dateStr epoch date string
     * @return epoch timestamp in custom time units
     * @throws IllegalArgumentException if date format is invalid
     */
    private long parseEpoch(String dateStr) {
        try {
            return parseFlexibleDate(dateStr).getTime() / stepMs;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid epoch format: " + dateStr, e);
        }
    }

    /**
     * Parses date string with flexible format support.
     *
     * @param dateStr date string to parse
     * @return parsed Date object
     * @throws ParseException if date format is not supported
     */
    private Date parseFlexibleDate(String dateStr) throws ParseException {
        String normalized = dateStr.replace('.', '-').replace('/', '-').replace('_', '-').trim();
        String[] patterns = {"yyyy-MM-dd", "yyyy-M-d"};
        for (String p : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p);
                sdf.setLenient(false);
                Date d = sdf.parse(normalized);
                if (normalized.equals(sdf.format(d))) return d;
            } catch (ParseException ignored) {}
        }
        throw new ParseException("Unsupported date format: " + dateStr, 0);
    }
}
