package com.eachid;

import java.util.ArrayList;
import java.util.List;

/**
 * EachIdGroup - High-performance ID generator group with multiple EachId instances
 *
 * <p>This class manages multiple EachId instances to achieve higher throughput
 * while maintaining ID uniqueness through distinct worker IDs.</p>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Multiple EachId instances for reduced lock contention</li>
 *   <li>High-performance load balancing strategies</li>
 *   <li>Chainable configuration methods</li>
 *   <li>Maintains ID uniqueness through worker ID separation</li>
 * </ul></p>
 *
 * @author EachId Development Team
 * @version 1.0.0
 * @since 2025
 */
public final class EachIdGroup {

    /**
     * High-performance balancing strategies for maximum throughput
     */
    public enum BalancingStrategy {
        /** Thread-local fixed binding - zero contention, highest performance */
        THREAD_LOCAL_FIXED,

        /** Thread-local round-robin - good balance, moderate performance */
        THREAD_LOCAL_ROUND_ROBIN,

        /** XOR-Shift pseudo-random - excellent distribution, no contention */
        XOR_SHIFT_RANDOM,

        /** Thread ID hash - thread affinity, no contention */
        THREAD_ID_HASH
    }

    // ==================== High-Performance Implementations ====================

    /**
     * Thread-local fixed binding implementation for maximum performance
     * Each thread is permanently bound to one instance for perfect cache locality
     */
    private static class ThreadLocalFixedBinding {
        private final ThreadLocal<EachId> threadLocalInstance;

        public ThreadLocalFixedBinding(List<EachId> instances) {
            this.threadLocalInstance = ThreadLocal.withInitial(() -> {
                // Each thread gets a fixed instance based on thread ID
                long threadId = Thread.currentThread().getId();
                int index = (int) (threadId % instances.size());
                return instances.get(index);
            });
        }

        public EachId getInstance() {
            return threadLocalInstance.get();
        }
    }

    /**
     * Thread-local round-robin implementation for balanced load distribution
     */
    private static class ThreadLocalRoundRobin {
        private final ThreadLocal<Integer> threadLocalIndex;
        private final int instanceCount;

        public ThreadLocalRoundRobin(int instanceCount) {
            this.instanceCount = instanceCount;
            this.threadLocalIndex = ThreadLocal.withInitial(() -> 0);
        }

        public int next() {
            int current = threadLocalIndex.get();
            threadLocalIndex.set((current + 1) % instanceCount);
            return current;
        }
    }

    /**
     * High-performance XOR-Shift pseudo-random number generator
     */
    private static class XORShiftRandom {
        private long seed;

        public XORShiftRandom(long seed) {
            this.seed = seed;
        }

        public int nextInt(int bound) {
            long x = seed;
            x ^= x << 13;
            x ^= x >> 7;
            x ^= x << 17;
            seed = x;
            return (int) (Math.abs(x) % bound);
        }
    }

    // ==================== Instance Fields ====================

    private final List<EachId> eachIdList = new ArrayList<>();
    private BalancingStrategy balancingStrategy = BalancingStrategy.THREAD_LOCAL_FIXED;

    // High-performance strategy implementations
    private ThreadLocalFixedBinding threadLocalFixedBinding;
    private ThreadLocalRoundRobin threadLocalRoundRobin;
    private final ThreadLocal<XORShiftRandom> threadLocalRandom =
            ThreadLocal.withInitial(() -> new XORShiftRandom(Thread.currentThread().getId()));

    // ==================== Configuration Fields ====================

    private String epoch = DefaultValue.DEFAULT_EPOCH;
    private long stepMs = DefaultValue.DEFAULT_STEP_MS;
    private long timestampBits = DefaultValue.DEFAULT_TIMESTAMP_BITS;
    private long datacenterIdBits = DefaultValue.DEFAULT_DATACENTER_ID_BITS;
    private long workerIdBits = DefaultValue.DEFAULT_WORKER_ID_BITS;
    private long sequenceBits = DefaultValue.DEFAULT_SEQUENCE_BITS;
    private long clockBackwardThresholdMs = DefaultValue.DEFAULT_CLOCK_BACKWARD_THRESHOLD_MS;
    private long datacenterId = DefaultValue.DEFAULT_DATACENTER_ID;

    // ==================== Core Configuration Methods ====================

    /**
     * Sets the starting worker ID and number of EachId instances
     *
     * <p>This is the key method that creates multiple EachId instances with
     * consecutive worker IDs to ensure global uniqueness.</p>
     *
     * @param startWorkerId the first worker ID in the sequence
     * @param instanceCount number of EachId instances to create
     * @return this EachIdGroup for method chaining
     * @throws IllegalArgumentException if parameters are invalid
     */
    public EachIdGroup setStartWorkerIdAndCount(long startWorkerId, int instanceCount) {
        if (instanceCount <= 0) {
            throw new IllegalArgumentException("instanceCount must be positive");
        }
        if (startWorkerId < 0) {
            throw new IllegalArgumentException("startWorkerId must be non-negative");
        }

        eachIdList.clear();

        for (int i = 0; i < instanceCount; i++) {
            EachId instance = new EachId();

            // Apply all current configuration to the new instance
            instance.setEpoch(epoch)
                    .setStepMs(stepMs)
                    .setTimestampBits(timestampBits)
                    .setDatacenterIdBits(datacenterIdBits)
                    .setWorkerIdBits(workerIdBits)
                    .setSequenceBits(sequenceBits)
                    .setClockBackwardThresholdMs(clockBackwardThresholdMs)
                    .setDatacenterId(datacenterId)
                    .setWorkerId(startWorkerId + i);  // Critical: consecutive worker IDs

            eachIdList.add(instance);
        }

        // Initialize high-performance strategies
        this.threadLocalFixedBinding = new ThreadLocalFixedBinding(eachIdList);
        this.threadLocalRoundRobin = new ThreadLocalRoundRobin(instanceCount);

        return this;
    }

    /**
     * Sets the balancing strategy for instance selection
     *
     * @param strategy the balancing strategy to use
     * @return this EachIdGroup for method chaining
     */
    public EachIdGroup setBalancingStrategy(BalancingStrategy strategy) {
        this.balancingStrategy = strategy;
        return this;
    }

    // ==================== Chainable Configuration Methods ====================

    /**
     * Sets epoch for all instances
     */
    public EachIdGroup setEpoch(String epoch) {
        this.epoch = epoch;
        for (EachId instance : eachIdList) {
            instance.setEpoch(epoch);
        }
        return this;
    }

    /**
     * Sets time step for all instances
     */
    public EachIdGroup setStepMs(long stepMs) {
        this.stepMs = stepMs;
        for (EachId instance : eachIdList) {
            instance.setStepMs(stepMs);
        }
        return this;
    }

    /**
     * Sets timestamp bits for all instances
     */
    public EachIdGroup setTimestampBits(long bits) {
        this.timestampBits = bits;
        for (EachId instance : eachIdList) {
            instance.setTimestampBits(bits);
        }
        return this;
    }

    /**
     * Sets datacenter ID bits for all instances
     */
    public EachIdGroup setDatacenterIdBits(long bits) {
        this.datacenterIdBits = bits;
        for (EachId instance : eachIdList) {
            instance.setDatacenterIdBits(bits);
        }
        return this;
    }

    /**
     * Sets worker ID bits for all instances
     */
    public EachIdGroup setWorkerIdBits(long bits) {
        this.workerIdBits = bits;
        for (EachId instance : eachIdList) {
            instance.setWorkerIdBits(bits);
        }
        return this;
    }

    /**
     * Sets sequence bits for all instances
     */
    public EachIdGroup setSequenceBits(long bits) {
        this.sequenceBits = bits;
        for (EachId instance : eachIdList) {
            instance.setSequenceBits(bits);
        }
        return this;
    }

    /**
     * Sets clock backward threshold for all instances
     */
    public EachIdGroup setClockBackwardThresholdMs(long ms) {
        this.clockBackwardThresholdMs = ms;
        for (EachId instance : eachIdList) {
            instance.setClockBackwardThresholdMs(ms);
        }
        return this;
    }

    /**
     * Sets datacenter ID for all instances
     */
    public EachIdGroup setDatacenterId(long datacenterId) {
        this.datacenterId = datacenterId;
        for (EachId instance : eachIdList) {
            instance.setDatacenterId(datacenterId);
        }
        return this;
    }

    // ==================== ID Generation Methods ====================

    /**
     * Generates a single unique ID using the configured balancing strategy
     *
     * @return a unique ID from one of the instances
     * @throws IllegalStateException if no instances are configured
     */
    public long nextId() {
        EachId instance = selectInstance();
        return instance.nextId();
    }

    /**
     * Generates a single unique ID as hexadecimal string
     *
     * @return hexadecimal string representation of the ID
     */
    public String nextIdHex() {
        EachId instance = selectInstance();
        return instance.nextIdHex();
    }

    /**
     * Generates multiple unique IDs in batch from the same instance
     *
     * @param count number of IDs to generate
     * @return the first ID in the batch
     */
    public long nextId(int count) {
        EachId instance = selectInstance();
        return instance.nextId(count);
    }

    // ==================== Instance Selection Methods ====================

    /**
     * Selects an EachId instance based on the configured balancing strategy
     *
     * @return selected EachId instance
     * @throws IllegalStateException if no instances available
     */
    private EachId selectInstance() {
        if (eachIdList.isEmpty()) {
            throw new IllegalStateException("No EachId instances configured. Call setStartWorkerIdAndCount() first.");
        }

        int index;
        switch (balancingStrategy) {
            case THREAD_LOCAL_FIXED:
                return threadLocalFixedBinding.getInstance();

            case THREAD_LOCAL_ROUND_ROBIN:
                index = threadLocalRoundRobin.next();
                break;

            case XOR_SHIFT_RANDOM:
                index = threadLocalRandom.get().nextInt(eachIdList.size());
                break;

            case THREAD_ID_HASH:
                long threadId = Thread.currentThread().getId();
                index = (int) (threadId % eachIdList.size());
                break;

            default:
                index = 0;
        }

        return eachIdList.get(index);
    }

    // ==================== Utility Methods ====================

    /**
     * Returns the number of configured instances
     *
     * @return instance count
     */
    public int getInstanceCount() {
        return eachIdList.size();
    }

    /**
     * Returns the current balancing strategy
     *
     * @return active balancing strategy
     */
    public BalancingStrategy getBalancingStrategy() {
        return balancingStrategy;
    }

    /**
     * Gets a specific instance by index
     *
     * @param index instance index (0-based)
     * @return the EachId instance
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public EachId getInstance(int index) {
        return eachIdList.get(index);
    }

    /**
     * Returns detailed configuration information
     *
     * @return formatted configuration report
     */
    public String getInfo() {
        if (eachIdList.isEmpty()) {
            return "EachIdGroup: No instances configured";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("EachIdGroup Configuration\n");
        sb.append("═══════════════════════════════════════\n");
        sb.append("Instance Count    : ").append(eachIdList.size()).append("\n");
        sb.append("Balancing Strategy: ").append(balancingStrategy).append("\n");
        sb.append("Worker ID Range   : ").append(eachIdList.get(0).getWorkerId())
                .append(" to ").append(eachIdList.get(eachIdList.size() - 1).getWorkerId()).append("\n");
        sb.append("Total Capacity    : ").append(eachIdList.size()).append(" instances\n");
        sb.append("═══════════════════════════════════════\n");

        // Add individual instance info
        for (int i = 0; i < Math.min(eachIdList.size(), 5); i++) { // Show first 5 instances
            sb.append("Instance ").append(i).append(": WorkerID=")
                    .append(eachIdList.get(i).getWorkerId()).append("\n");
        }
        if (eachIdList.size() > 5) {
            sb.append("... and ").append(eachIdList.size() - 5).append(" more instances\n");
        }

        return sb.toString();
    }

    /**
     * Returns performance statistics across all instances
     *
     * @return formatted performance report
     */
    public String getPerformanceInfo() {
        if (eachIdList.isEmpty()) {
            return "EachIdGroup: No instances configured";
        }

        long totalMaxSequence = eachIdList.get(0).getMaxSequence() * eachIdList.size();
        long theoreticalQps = (totalMaxSequence * 1000L) / eachIdList.get(0).getStepMs();

        return String.format(
                "EachIdGroup Performance\n" +
                        "Instances       : %d\n" +
                        "Total Capacity  : %,d IDs/%dms (theoretical)\n" +
                        "Theoretical QPS : ~%,d IDs/second\n" +
                        "Balancing       : %s\n",
                eachIdList.size(),
                totalMaxSequence, eachIdList.get(0).getStepMs(),
                theoreticalQps,
                balancingStrategy
        );
    }
}
