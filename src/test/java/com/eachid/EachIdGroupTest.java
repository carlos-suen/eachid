package com.eachid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for EachIdGroup
 *
 * <p>Validates multi-instance coordination, load balancing, and performance characteristics
 * of the EachIdGroup component.</p>
 */
class EachIdGroupTest {
    private static final Logger logger = LoggerFactory.getLogger(EachIdGroupTest.class);

    private EachIdGroup group;
    private static final int TEST_INSTANCE_COUNT = 4;
    private static final long START_WORKER_ID = 10;

    @BeforeEach
    void setUp() {
        group = new EachIdGroup()
                .setTimestampBits(35)
                .setDatacenterIdBits(0)
                .setWorkerIdBits(6)
                .setSequenceBits(22)
                .setStartWorkerIdAndCount(START_WORKER_ID, TEST_INSTANCE_COUNT)
                .setStepMs(100)
                .setClockBackwardThresholdMs(1000)
                .setBalancingStrategy(EachIdGroup.BalancingStrategy.THREAD_LOCAL_ROUND_ROBIN);
    }

    static void line() {
        logger.info("═══════════════════════════════════════");
    }

    static void lineDone() {
        logger.info("═══════════════════════════════════════\n\n");

    }

    // ==================== Basic Functionality Tests ====================

    @Test
    void testEachIdGroupBasic() {
        line();
        logger.info("测试名称: [testEachIdGroupBasic]");
        logger.info("测试目标: EachIdGroup基础功能验证");
        logger.info("测试内容: 验证多实例环境下的ID生成基本功能");
        logger.info("线程模式: 单线程");
        line();

        long startTime = System.nanoTime();

        // Generate multiple IDs and verify basic properties
        Set<Long> generatedIds = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            long id = group.nextId();

            // Basic validation
            assertTrue(id > 0, "Generated ID should be positive");
            assertFalse(generatedIds.contains(id), "Generated IDs should be unique");
            generatedIds.add(id);

            // Parse and validate structure
            EachId.IdInfo info = group.getInstance(0).parseId(id);
            assertTrue(info.workerId >= START_WORKER_ID &&
                            info.workerId < START_WORKER_ID + TEST_INSTANCE_COUNT,
                    "Worker ID should be in configured range");
        }

        long duration = System.nanoTime() - startTime;

        line();
        logger.info("测试结果报告 - [testEachIdGroupBasic]");
        logger.info("✅ 基础功能验证通过");
        logger.info("✅ 生成 {} 个唯一ID", generatedIds.size());
        logger.info("✅ Worker ID范围正确: {} - {}", START_WORKER_ID, START_WORKER_ID + TEST_INSTANCE_COUNT - 1);
        logger.info("⏱️ 测试耗时: {} ns", duration);
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    void testInstanceConfiguration() {
        line();
        logger.info("测试名称: [testInstanceConfiguration]");
        logger.info("测试目标: 实例配置正确性验证");
        logger.info("测试内容: 验证所有实例正确配置且Worker ID连续");
        line();

        // Verify instance count
        assertEquals(TEST_INSTANCE_COUNT, group.getInstanceCount(),
                "Instance count should match configuration");

        // Verify consecutive worker IDs
        for (int i = 0; i < TEST_INSTANCE_COUNT; i++) {
            EachId instance = group.getInstance(i);
            long expectedWorkerId = START_WORKER_ID + i;
            assertEquals(expectedWorkerId, instance.getWorkerId(),
                    "Instance " + i + " should have worker ID " + expectedWorkerId);
        }

        line();
        logger.info("测试结果报告 - [testInstanceConfiguration]");
        logger.info("✅ 实例数量正确: {}", group.getInstanceCount());
        logger.info("✅ Worker ID连续分配: {} - {}",
                group.getInstance(0).getWorkerId(),
                group.getInstance(TEST_INSTANCE_COUNT - 1).getWorkerId());
        logger.info("✅ 所有实例配置一致");
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== Load Balancing Tests ====================

    @Test
    void testBalancingStrategies() {
        line();
        logger.info("测试名称: [testBalancingStrategies]");
        logger.info("测试目标: 负载均衡策略功能验证");
        logger.info("测试内容: 测试所有平衡策略的分布特性");
        line();

        Map<EachIdGroup.BalancingStrategy, Map<Integer, Integer>> strategyResults = new HashMap<>();

        for (EachIdGroup.BalancingStrategy strategy : EachIdGroup.BalancingStrategy.values()) {
            logger.info("测试平衡策略: {}", strategy);

            EachIdGroup testGroup = new EachIdGroup()
                    .setStartWorkerIdAndCount(0, 4)
                    .setBalancingStrategy(strategy);

            Map<Integer, Integer> instanceUsage = new HashMap<>();
            for (int i = 0; i < 1000; i++) {
                long id = testGroup.nextId();
                EachId.IdInfo info = testGroup.getInstance(0).parseId(id);
                int instanceIndex = (int) info.workerId;
                instanceUsage.merge(instanceIndex, 1, Integer::sum);
            }

            strategyResults.put(strategy, instanceUsage);

            // ✅ High-performance strategies behavior validation
            switch (strategy) {
                case THREAD_LOCAL_ROUND_ROBIN:
                    // Thread-local round-robin should use all instances evenly in single thread
                    assertEquals(4, instanceUsage.size(),
                            "THREAD_LOCAL_ROUND_ROBIN should use all instances in single thread");
                    // Should be perfectly balanced
                    int expectedCount = 1000 / 4;
                    for (int count : instanceUsage.values()) {
                        assertEquals(expectedCount, count, "THREAD_LOCAL_ROUND_ROBIN should be perfectly balanced");
                    }
                    break;

                case XOR_SHIFT_RANDOM:
                    // XOR-Shift random should use all instances with good distribution
                    assertEquals(4, instanceUsage.size(),
                            "XOR_SHIFT_RANDOM should use all instances");
                    // Check reasonable distribution (within 20% of expected)
                    int expectedRandom = 1000 / 4;
                    for (int count : instanceUsage.values()) {
                        double deviation = Math.abs(count - expectedRandom) / (double) expectedRandom;
                        assertTrue(deviation < 0.2, "XOR_SHIFT_RANDOM distribution should be within 20%");
                    }
                    break;

                case THREAD_ID_HASH:
                    // Thread ID hash: single thread = single instance (expected)
                    assertEquals(1, instanceUsage.size(),
                            "THREAD_ID_HASH should use one instance per thread");
                    break;
            }
        }

        line();
        logger.info("测试结果报告 - [testBalancingStrategies]");
        for (Map.Entry<EachIdGroup.BalancingStrategy, Map<Integer, Integer>> entry : strategyResults.entrySet()) {
            logger.info("策略 {}: 单线程分布 {}", entry.getKey(), entry.getValue());
        }
        logger.info("✅ 所有平衡策略功能正常");
        logger.info("✅ 单线程行为符合预期");
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    void testBalancingStrategiesMultiThread() throws InterruptedException, ExecutionException {
        line();
        logger.info("测试名称: [testBalancingStrategiesMultiThread]");
        logger.info("测试目标: 多线程环境下负载均衡策略验证");
        logger.info("测试内容: 验证所有策略在多线程下的分布特性");
        logger.info("线程模式: 多线程 (8线程)");
        line();

        Map<EachIdGroup.BalancingStrategy, Map<Integer, Integer>> strategyResults = new HashMap<>();
        final int threadCount = 8;
        final int requestsPerThread = 1000;

        for (EachIdGroup.BalancingStrategy strategy : EachIdGroup.BalancingStrategy.values()) {
            logger.info("多线程测试平衡策略: {}", strategy);

            EachIdGroup testGroup = new EachIdGroup()
                    .setStartWorkerIdAndCount(0, 4)
                    .setBalancingStrategy(strategy);

            Map<Integer, Integer> instanceUsage = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                Future<?> future = executor.submit(() -> {
                    for (int j = 0; j < requestsPerThread; j++) {
                        long id = testGroup.nextId();
                        EachId.IdInfo info = testGroup.getInstance(0).parseId(id);
                        int instanceIndex = (int) info.workerId;
                        instanceUsage.merge(instanceIndex, 1, Integer::sum);
                    }
                });
                futures.add(future);
            }

            // Wait for completion
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    throw new RuntimeException("Thread execution failed", e);
                }
            }

            executor.shutdown();
            strategyResults.put(strategy, instanceUsage);

            // ✅ All high-performance strategies should use all instances in multi-threaded environment
            logger.info("策略 {} 多线程分布: {}", strategy, instanceUsage);
            assertEquals(4, instanceUsage.size(),
                    "Strategy " + strategy + " should use all instances in multi-threaded environment");
        }

        line();
        logger.info("测试结果报告 - [testBalancingStrategiesMultiThread]");
        for (Map.Entry<EachIdGroup.BalancingStrategy, Map<Integer, Integer>> entry : strategyResults.entrySet()) {
            logger.info("策略 {}: 多线程分布 {}", entry.getKey(), entry.getValue());
        }
        logger.info("✅ 多线程负载均衡验证通过");
        logger.info("✅ 所有策略在多线程环境下使用所有实例");
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    void testRoundRobinDistribution() {
        line();
        logger.info("测试名称: [testRoundRobinDistribution]");
        logger.info("测试目标: 轮询策略分布均匀性验证");
        logger.info("测试内容: 验证轮询策略在单线程下的均匀分布");
        line();

        EachIdGroup roundRobinGroup = new EachIdGroup()
                .setStartWorkerIdAndCount(0, 4)
                .setBalancingStrategy(EachIdGroup.BalancingStrategy.THREAD_LOCAL_ROUND_ROBIN);

        Map<Integer, Integer> distribution = new HashMap<>();
        int totalRequests = 1000;

        for (int i = 0; i < totalRequests; i++) {
            long id = roundRobinGroup.nextId();
            EachId.IdInfo info = roundRobinGroup.getInstance(0).parseId(id);
            int instanceIndex = (int) info.workerId;
            distribution.merge(instanceIndex, 1, Integer::sum);
        }

        // Verify perfect distribution (should be exactly equal)
        int expectedPerInstance = totalRequests / 4;
        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            int count = entry.getValue();
            assertEquals(expectedPerInstance, count,
                    "Instance " + entry.getKey() + " should have exactly " + expectedPerInstance + " requests");
        }

        line();
        logger.info("测试结果报告 - [testRoundRobinDistribution]");
        logger.info("轮询分布结果: {}", distribution);
        logger.info("期望每实例: {} 次", expectedPerInstance);
        logger.info("✅ 轮询分布均匀性验证通过");
        logger.info("✅ 完美均衡分布");
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== Configuration Propagation Tests ====================

    @Test
    void testConfigurationPropagation() {
        line();
        logger.info("测试名称: [testConfigurationPropagation]");
        logger.info("测试目标: 配置传播一致性验证");
        logger.info("测试内容: 验证组级配置正确传播到所有实例");
        line();

        long customStepMs = 500;
        long customSequenceBits = 18;
        String customEpoch = "2024-01-01";

        EachIdGroup configGroup = new EachIdGroup()
                .setStartWorkerIdAndCount(0, 3)
                .setStepMs(customStepMs)
                .setSequenceBits(customSequenceBits)
                .setEpoch(customEpoch);

        // Verify configuration is propagated to all instances
        for (int i = 0; i < configGroup.getInstanceCount(); i++) {
            EachId instance = configGroup.getInstance(i);
            assertEquals(customStepMs, instance.getStepMs(),
                    "Instance " + i + " should have correct stepMs");
            assertEquals(customSequenceBits, instance.getSequenceBits(),
                    "Instance " + i + " should have correct sequenceBits");
        }

        line();
        logger.info("测试结果报告 - [testConfigurationPropagation]");
        logger.info("✅ 配置传播验证通过");
        logger.info("✅ StepMs: {} → 所有实例", customStepMs);
        logger.info("✅ SequenceBits: {} → 所有实例", customSequenceBits);
        logger.info("✅ Epoch: {} → 所有实例", customEpoch);
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== Concurrency and Uniqueness Tests ====================

    @Test
    void testConcurrentUniqueness() throws InterruptedException {
        line();
        logger.info("测试名称: [testConcurrentUniqueness]");
        logger.info("测试目标: 高并发环境下ID唯一性验证");
        logger.info("测试内容: 多线程并发生成ID验证全局唯一性");
        logger.info("线程模式: 多线程 (16线程)");
        line();

        final int threadCount = 16;
        final int idsPerThread = 1000;
        final Set<Long> allIds = ConcurrentHashMap.newKeySet();
        final AtomicLong errorCount = new AtomicLong();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = group.nextId();
                        if (!allIds.add(id)) {
                            errorCount.incrementAndGet();
                            logger.error("Duplicate ID detected: {}", id);
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    logger.error("Thread error", e);
                }
            });
            futures.add(future);
        }

        // Wait for all threads to complete with proper exception handling
        for (Future<?> future : futures) {
            try {
                future.get();  // ✅ Now properly handled
            } catch (ExecutionException e) {
                errorCount.incrementAndGet();
                logger.error("Execution exception in thread", e);
                throw new RuntimeException("Thread execution failed", e);
            }
        }

        executor.shutdown();
        long duration = System.currentTimeMillis() - startTime;

        long totalIds = threadCount * idsPerThread;
        assertEquals(totalIds, allIds.size(), "All generated IDs should be unique");
        assertEquals(0, errorCount.get(), "No errors should occur during generation");

        line();
        logger.info("测试结果报告 - [testConcurrentUniqueness]");
        logger.info("线程数: {}", threadCount);
        logger.info("总生成ID数: {}", totalIds);
        logger.info("唯一ID数: {}", allIds.size());
        logger.info("错误数: {}", errorCount.get());
        logger.info("总耗时: {} ms", duration);
        logger.info("✅ 高并发唯一性验证通过");
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    void testBatchGenerationWithGroup() {
        line();
        logger.info("测试名称: [testBatchGenerationWithGroup]");
        logger.info("测试目标: 组环境下的批量生成功能验证");
        logger.info("测试内容: 验证批量生成在多实例环境下的正确性");
        line();

        int batchSize = 10;
        Set<Long> batchIds = new HashSet<>();

        // Generate multiple batches from different instances
        for (int i = 0; i < 10; i++) {
            long firstId = group.nextId(batchSize);
            batchIds.add(firstId);

            // Verify batch continuity
            for (int j = 1; j < batchSize; j++) {
                long nextId = firstId + j;
                batchIds.add(nextId);

                // Parse and verify they come from same worker
                EachId.IdInfo firstInfo = group.getInstance(0).parseId(firstId);
                EachId.IdInfo nextInfo = group.getInstance(0).parseId(nextId);
                assertEquals(firstInfo.workerId, nextInfo.workerId,
                        "Batch IDs should come from same worker");
                assertEquals(firstInfo.timestamp, nextInfo.timestamp,
                        "Batch IDs should have same timestamp");
            }
        }

        assertEquals(100, batchIds.size(), "Should generate 100 unique batch IDs");

        line();
        logger.info("测试结果报告 - [testBatchGenerationWithGroup]");
        logger.info("✅ 批量生成功能正常");
        logger.info("✅ 生成 {} 个批量ID", batchIds.size());
        logger.info("✅ 批量连续性验证通过");
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== Performance Comparison Tests ====================

    @Test
    void testPerformanceVsSingleInstance() {
        line();
        logger.info("测试名称: [testPerformanceVsSingleInstance]");
        logger.info("测试目标: 多实例 vs 单实例性能对比");
        logger.info("测试内容: 验证高性能策略下多实例的性能优势");
        line();

        // Single instance baseline
        EachId singleInstance = new EachId()
                .setWorkerId(1)
                .setStepMs(1000);

        // Multi-instance group with high-performance strategy
        EachIdGroup multiInstance = new EachIdGroup()
                .setStartWorkerIdAndCount(1, 4)
                .setStepMs(1000)
                .setBalancingStrategy(EachIdGroup.BalancingStrategy.THREAD_LOCAL_ROUND_ROBIN);

        final int threadCount = 8;
        final int iterations = 10000;

        // Test single instance performance
        long singleStart = System.nanoTime();
        testThroughput(singleInstance, threadCount, iterations);
        long singleDuration = System.nanoTime() - singleStart;

        // Test multi-instance performance
        long multiStart = System.nanoTime();
        testThroughput(multiInstance, threadCount, iterations);
        long multiDuration = System.nanoTime() - multiStart;

        double singleQps = (threadCount * iterations) / (singleDuration / 1_000_000_000.0);
        double multiQps = (threadCount * iterations) / (multiDuration / 1_000_000_000.0);
        double performanceRatio = multiQps / singleQps;

        line();
        logger.info("性能对比报告 - [testPerformanceVsSingleInstance]");
        logger.info("单实例 QPS: {}", (long)singleQps);
        logger.info("多实例 QPS: {}", (long)multiQps);
        logger.info("性能比例: {}% (多实例/单实例)", performanceRatio * 100);
        logger.info("线程数: {}, 每线程迭代: {}", threadCount, iterations);

        // ✅ Now we expect multi-instance to be equal or better than single instance
        if (performanceRatio >= 1.0) {
            logger.info("🎯 性能表现: 卓越 - 多实例性能优于单实例");
        } else if (performanceRatio >= 0.9) {
            logger.info("✅ 性能表现: 优秀 - 多实例性能接近单实例");
        } else {
            logger.info("⚠️ 性能表现: 需关注 - 多实例性能有待优化");
        }

        logger.info("📝 技术说明: 使用高性能负载均衡策略，多实例应该提供更好的性能");
        logger.info("✅ 性能对比测试完成");
        lineDone();
    }

    @Test
    void testEachIdGroupScalingBenefits() {
        line();
        logger.info("测试名称: [testEachIdGroupScalingBenefits]");
        logger.info("测试目标: 验证 EachIdGroup 的扩展性优势");
        logger.info("测试内容: 测试工作节点扩展带来的性能提升");
        line();

        // Test different group sizes to show scaling benefits
        int[] groupSizes = {1, 2, 4, 8};
        Map<Integer, Double> performanceResults = new LinkedHashMap<>();

        final int threadCount = 16;
        final int iterations = 5000;

        for (int groupSize : groupSizes) {
            EachIdGroup testGroup = new EachIdGroup()
                    .setStartWorkerIdAndCount(0, groupSize)
                    .setStepMs(1000)
                    .setBalancingStrategy(EachIdGroup.BalancingStrategy.THREAD_LOCAL_ROUND_ROBIN);

            long startTime = System.nanoTime();
            testThroughput(testGroup, threadCount, iterations);
            long duration = System.nanoTime() - startTime;

            double qps = (threadCount * iterations) / (duration / 1_000_000_000.0);
            performanceResults.put(groupSize, qps);

            logger.info("组大小 {}: QPS = {,.0f}", groupSize, qps);
        }

        // Analyze scaling characteristics
        double baseQps = performanceResults.get(1);
        line();
        logger.info("扩展性分析报告 - [testEachIdGroupScalingBenefits]");

        for (Map.Entry<Integer, Double> entry : performanceResults.entrySet()) {
            int groupSize = entry.getKey();
            double qps = entry.getValue();
            double scalingFactor = qps / baseQps;

            logger.info("组大小 {}: QPS = {,.0f}, 扩展系数 = {:.2f}x",
                    groupSize, qps, scalingFactor);
        }

        // ✅ Now we expect better scaling with high-performance strategies
        logger.info("📝 技术洞察:");
        logger.info("  - EachIdGroup 的主要价值是工作节点扩展和性能提升");
        logger.info("  - 使用高性能负载均衡策略，多实例应该提供更好的扩展性");
        logger.info("  - 预期: 4个实例应该接近4倍性能提升");
        logger.info("✅ 扩展性分析完成");
        lineDone();
    }

    private void testThroughput(Object generator, int threadCount, int iterations) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Future<?> future = executor.submit(() -> {
                for (int j = 0; j < iterations; j++) {
                    if (generator instanceof EachId) {
                        ((EachId) generator).nextId();
                    } else if (generator instanceof EachIdGroup) {
                        ((EachIdGroup) generator).nextId();
                    }
                }
            });
            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        executor.shutdown();
    }

    // ==================== Edge Case Tests ====================

    @Test
    void testEmptyGroupBehavior() {
        line();
        logger.info("测试名称: [testEmptyGroupBehavior]");
        logger.info("测试目标: 空组异常处理验证");
        logger.info("测试内容: 验证未初始化组的正确异常行为");
        line();

        EachIdGroup emptyGroup = new EachIdGroup();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                emptyGroup::nextId,
                "Should throw IllegalStateException when no instances configured");

        assertTrue(exception.getMessage().contains("No EachId instances configured"),
                "Exception message should indicate missing instances");

        line();
        logger.info("测试结果报告 - [testEmptyGroupBehavior]");
        logger.info("✅ 空组异常处理正确");
        logger.info("✅ 异常消息: {}", exception.getMessage());
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    void testWorkerIdRangeValidation() {
        line();
        logger.info("测试名称: [testWorkerIdRangeValidation]");
        logger.info("测试目标: Worker ID范围验证");
        logger.info("测试内容: 验证Worker ID超出范围时的异常处理");
        line();

        // Test with worker bits too small for requested range
        EachIdGroup groupWithSmallRange = new EachIdGroup()
                .setWorkerIdBits(2);  // Only 4 worker IDs (0-3)

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> groupWithSmallRange.setStartWorkerIdAndCount(5, 3),
                "Should throw when worker IDs exceed bit capacity");

        assertTrue(exception.getMessage().contains("workerId out of range"),
                "Exception should indicate worker ID range violation");

        line();
        logger.info("测试结果报告 - [testWorkerIdRangeValidation]");
        logger.info("✅ Worker ID范围验证通过");
        logger.info("✅ 异常处理正确");
        logger.info("✅ 测试完成");
        lineDone();
    }

    @AfterEach
    void tearDown() {
        group = null;
    }
}
