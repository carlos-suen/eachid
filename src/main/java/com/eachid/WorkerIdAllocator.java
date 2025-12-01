package com.eachid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Production-grade WorkerId auto allocator, fully compatible with JDK 8+
 * (生产级 WorkerId 自动分配器，完全兼容 JDK 8+)
 * Priority: env var → system property → IP last segment → random
 */
public final class WorkerIdAllocator {
    private static final Logger logger = LoggerFactory.getLogger(WorkerIdAllocator.class);

    private static final String ENV_KEY = "WORKER_ID";
    private static final String PROP_KEY = "worker.id";

    /**
     * Get WorkerId (recommended method)
     *
     * @param maxWorkerId maximum allowed WorkerId (e.g., 1023 for 10 bits)
     * @return value between 0 and maxWorkerId
     */
    public static long getAutoWorkerId(int maxWorkerId) {
        if (maxWorkerId < 0) {
            throw new IllegalArgumentException("maxWorkerId must be reasonable [0-32767]");
        }

        // 1. Environment variable or JVM parameter (highest priority)
        Long configured = getConfiguredWorkerId(maxWorkerId);
        if (configured != null) {
            logger.info("Using configured WorkerId: {} (source: {})", configured,
                    configuredFromEnv() ? "environment variable" : "JVM parameter");
            return configured;
        }

        // 2. IP last segment (most reliable automatic method)
        try {
            int segment = getWorkerIdFromIpLastSegment();
            long id = Math.abs(segment % (maxWorkerId + 1L));
            logger.info("Auto allocated WorkerId from IP: {} (last segment: {})", id, segment);
            return id;
        } catch (Exception e) {
            logger.warn("Failed to get IP segment: {}", e.toString());
        }

        // === 新增：MAC 地址 fallback（经典 Snowflake 做法）===
        try {
            long mac = getWorkerIdFromMacAddress();
            long id = mac % (maxWorkerId + 1L);
            logger.info("Auto allocated WorkerId from MAC: {} (MAC: {})", id, String.format("%012X", mac));
            return id;
        } catch (Exception e) {
            logger.warn("Failed to get MAC address: {}", e.toString());
        }

        // 3. Fallback to random
        long random = ThreadLocalRandom.current().nextLong(maxWorkerId + 1L);
        logger.warn("Using random WorkerId: {} (recommend setting fixed value via env or -Dworker.id)", random);
        return random;
    }

    private static long getWorkerIdFromMacAddress() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isLoopback() || !ni.isUp() || ni.isVirtual() || ni.isPointToPoint()) continue;

            byte[] mac = ni.getHardwareAddress();
            if (mac != null && mac.length == 6) {
                // 取后 6 字节转 long（经典做法）
                long value = 0;
                for (int i = 0; i < 6; i++) {
                    value = (value << 8) | (mac[i] & 0xFF);
                }
                return Math.abs(value);
            }
        }
        throw new Exception("No valid MAC address found");
    }
    private static Long getConfiguredWorkerId(int maxWorkerId) {
        String value = System.getenv(ENV_KEY);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(PROP_KEY);
        }
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            long id = Long.parseLong(value.trim());
            if (id >= 0 && id <= maxWorkerId) {
                return id;
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private static boolean configuredFromEnv() {
        return System.getenv(ENV_KEY) != null;
    }

    /** Get last numeric segment from any non-loopback IP */
    private static int getWorkerIdFromIpLastSegment() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isLoopback() || !ni.isUp()) continue;

            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr.isLoopbackAddress()) continue;

                String host = addr.getHostAddress();
                int lastDot = host.lastIndexOf('.');
                int lastColon = host.lastIndexOf(':');
                int pos = Math.max(lastDot, lastColon);

                if (pos > 0) {
                    String segment = host.substring(pos + 1);
                    try {
                        return Integer.parseInt(segment);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        throw new Exception("No valid IP found");
    }
}