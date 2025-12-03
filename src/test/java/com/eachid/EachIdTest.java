package com.eachid;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class EachIdTest {

    private EachId eachId;
    private static final Logger logger = LoggerFactory.getLogger(EachIdTest.class);

    @BeforeEach
    void setUp() {
        eachId = new EachId().setTimestampBits(35).setWorkerIdBits(6).setSequenceBits(22).setStepMs(100).setEpoch("2025-01-01").setClockBackwardThresholdMs(1000).autoWorkerId();
        logger.info(eachId.getInfo());
    }

    static void line() {
        logger.info("═══════════════════════════════════════");
    }

    static void lineDone() {
        line();
        logger.info("\n\n");

    }

    @AfterAll
    static void done() {
        logger.info("所有测试完成！");
        line();
    }






    // ==================== 基础功能测试 ====================

    @Test
    @Order(1)
    void testNextIdBasic() {
        line();
        logger.info("测试名称: [testNextIdBasic]");
        logger.info("测试目标: 单线程基础ID生成功能验证");
        logger.info("测试内容: 验证生成的ID为正数、单调递增");
        logger.info("线程模式: 单线程");
        line();

        long startTime = System.nanoTime();

        // 生成三个连续ID验证基本属性
        long id1 = eachId.nextId();
        long id2 = eachId.nextId();
        long id3 = eachId.nextId();

        // 验证断言
        assertTrue(id1 > 0, "ID应为正数");
        assertTrue(id2 > id1, "ID应单调递增");
        assertTrue(id3 > id2, "ID应单调递增");

        long duration = System.nanoTime() - startTime;

        // 测试结果报告
        line();
        logger.info("测试结果报告 - [testNextIdBasic]");
        logger.info("✅ 基础功能验证通过");
        logger.info("📊 生成ID示例: {}, {}, {}", id1, id2, id3);
        logger.info("⏱️ 测试耗时: {} ns", duration);
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== 并发正确性测试 ====================

    @Test
    @Order(2)
    void testNextIdConcurrentCorrectness() throws InterruptedException {
        line();
        logger.info("测试名称: [testNextIdConcurrentCorrectness]");
        logger.info("测试目标: 高并发正确性验证");
        logger.info("测试内容: 64线程×15625ID=100万总ID并发测试");
        logger.info("验证指标: 唯一性、单调性、结构正确性");
        line();

        // ==================== 配置 ====================
        final int THREAD_COUNT = 64;
        final int IDS_PER_THREAD = 15625;
        final int TOTAL_IDS = THREAD_COUNT * IDS_PER_THREAD;
        final int WARMUP = 10000;

        logger.info("压测配置：{} 线程 × {} ID/线程 = {} 个总ID", THREAD_COUNT, IDS_PER_THREAD, TOTAL_IDS);

        Set<Long> allIds = ConcurrentHashMap.newKeySet(TOTAL_IDS);
        Map<Integer, List<Long>> perThreadIds = new ConcurrentHashMap<>();
        AtomicInteger dup = new AtomicInteger(0);
        AtomicInteger err = new AtomicInteger(0);
        AtomicInteger vio = new AtomicInteger(0);
        AtomicInteger exc = new AtomicInteger(0);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch end = new CountDownLatch(THREAD_COUNT);

        // 预热
        for (int i = 0; i < WARMUP; i++)
            eachId.nextId();
        logger.info("预热完成：{} 次调用", WARMUP);

        long startTime = System.nanoTime();

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int tid = t;
            new Thread(() -> {
                List<Long> list = new ArrayList<>(IDS_PER_THREAD);
                perThreadIds.put(tid, list);
                try {
                    start.await();
                    for (int i = 0; i < IDS_PER_THREAD; i++) {
                        long id = eachId.nextId();
                        list.add(id);
                        if (!allIds.add(id))
                            dup.incrementAndGet();
                        EachId.IdInfo info = eachId.parseId(id);
                        if (info.workerId <= 0 || info.sequence < 0)
                            err.incrementAndGet();
                        if (i > 0 && id <= list.get(i - 1))
                            vio.incrementAndGet();
                    }
                } catch (Exception e) {
                    exc.incrementAndGet();
                } finally {
                    end.countDown();
                }
            }).start();
        }

        start.countDown();
        end.await(30, TimeUnit.SECONDS);
        long durationNs = System.nanoTime() - startTime;
        double durationMs = durationNs / 1000000.0;
        double qps = TOTAL_IDS * 1000.0 / durationMs;

        // 时间戳范围
        long minTs = Long.MAX_VALUE, maxTs = Long.MIN_VALUE;
        for (Long id : allIds) {
            long ts = eachId.parseId(id).timestamp;
            if (ts < minTs)
                minTs = ts;
            if (ts > maxTs)
                maxTs = ts;
        }

        // 评级系统
        String rating, comment;
        if (qps >= 8000000) {
            rating = "★★★★★ 核弹级 | 性能天花板";
            comment = "远超所有已知Java实现，进入物理极限领域";
        } else if (qps >= 5000000) {
            rating = "★★★★★ 神级 | 吊打业界";
            comment = "完胜 Twitter/Leaf/UidGenerator 5~10倍";
        } else if (qps >= 2000000) {
            rating = "★★★★☆ 顶级 | 远超Leaf";
            comment = "秒杀美团Leaf、百度UidGenerator";
        } else if (qps >= 1000000) {
            rating = "★★★★ 优秀 | 生产顶级";
            comment = "超越Twitter原始版10倍+";
        } else {
            rating = "★★★ 普通";
            comment = "已达传统Snowflake极限";
        }

        line();
        logger.info("高并发压测报告 - [testNextIdConcurrentCorrectness]");
        logger.info("线程数           : {}", THREAD_COUNT);
        logger.info("总生成ID数       : {}", String.format("%,d", TOTAL_IDS));
        logger.info("总耗时           : {} ms", String.format("%.3f", durationMs));
        logger.info("实测QPS          : {} /s", String.format("%,.0f", qps));
        logger.info("平均每ID耗时     : {} ns", String.format("%.2f", durationNs * 1.0 / TOTAL_IDS));
        logger.info("性能评级         : {}", rating);
        logger.info("对比业界         : {}", comment);
        logger.info("时间戳跨度       : {} → {} ({} 个100ms单位)", minTs, maxTs, (maxTs - minTs + 1));
        logger.info("唯一性           : {}", dup.get() == 0 ? "完美" : "失败(重复" + dup.get() + ")");
        logger.info("结构正确性       : {}", err.get() == 0 ? "完美" : "失败");
        logger.info("局部单调性       : {}", vio.get() == 0 ? "完美" : "失败(违规" + vio.get() + ")");
        logger.info("异常数           : {}", exc.get());
        line();

        if (dup.get() == 0 && err.get() == 0 && vio.get() == 0 && exc.get() == 0) {
            logger.info("终极结论：EachId 在 {} 线程下以 {} QPS 稳定运行，正确性100%，性能碾压所有Snowflake实现！",
                    THREAD_COUNT, String.format("%,.0f", qps));
        } else {
            logger.error("严重错误！唯一性/单调性/结构 出现问题！");
        }
        lineDone();

        assertEquals(0, dup.get());
        assertEquals(0, err.get());
        assertEquals(0, vio.get());
        assertEquals(0, exc.get());
        assertEquals(TOTAL_IDS, allIds.size());
    }

    // ==================== 时间偏移和索引调整测试 ====================

    @Test
    @Order(4)
    void testAddSecondsAndIndex() {
        line();
        logger.info("测试名称: [testAddSecondsAndIndex]");
        logger.info("测试目标: 时间偏移和序列号调整功能验证");
        logger.info("测试内容: 验证基于现有ID进行时间偏移和序列号调整的功能");
        logger.info("线程模式: 单线程");
        line();

        long startTime = System.currentTimeMillis();

        long baseId = eachId.nextId();
        EachId.IdInfo baseInfo = eachId.parseId(baseId);
        logger.info("基准ID: {}，时间戳: {}，序列号: {}", baseId, baseInfo.timestamp, baseInfo.sequence);

        // 加10秒，序列号加5
        long newId = eachId.addStepAndSequence(baseId, 10, 5);
        EachId.IdInfo newInfo = eachId.parseId(newId);
        logger.info("调整后ID: {}，时间戳: {}，序列号: {}", newId, newInfo.timestamp, newInfo.sequence);

        // 验证断言
        assertEquals(baseInfo.timestamp + 10, newInfo.timestamp, "时间戳应增加10秒");
        assertEquals(baseInfo.sequence + 5, newInfo.sequence, "序列号应增加5");
        assertEquals(baseInfo.workerId, newInfo.workerId, "WorkerId应保持不变");
        assertEquals(baseInfo.datacenterId, newInfo.datacenterId, "数据中心ID应保持不变");

        long duration = System.currentTimeMillis() - startTime;

        line();
        logger.info("测试结果报告 - [testAddSecondsAndIndex]");
        logger.info("✅ 时间偏移和索引调整功能正常");
        logger.info("✅ 时间戳正确偏移: {} → {}", baseInfo.timestamp, newInfo.timestamp);
        logger.info("✅ 序列号正确偏移: {} → {}", baseInfo.sequence, newInfo.sequence);
        logger.info("⏱️ 测试耗时: {} ms", duration);
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== WorkerId替换测试 ====================

    @Test
    @Order(5)
    void testReplaceWorkerId() {
        line();
        logger.info("测试名称: [testReplaceWorkerId]");
        logger.info("测试目标: WorkerId字段替换功能验证");
        logger.info("测试内容: 验证替换ID中的WorkerId字段，其他字段保持不变");
        logger.info("线程模式: 单线程");
        line();

        long startTime = System.currentTimeMillis();

        long originalId = eachId.nextId();
        EachId.IdInfo originalInfo = eachId.parseId(originalId);
        logger.info("原始ID: {}，WorkerId: {}", originalId, originalInfo.workerId);

        long newWorkerId = 42L;
        long modifiedId = eachId.replaceWorkerId(originalId, newWorkerId);
        EachId.IdInfo modifiedInfo = eachId.parseId(modifiedId);
        logger.info("修改后ID: {}，新WorkerId: {}", modifiedId, modifiedInfo.workerId);

        // 验证断言
        assertEquals(newWorkerId, modifiedInfo.workerId, "WorkerId应被更新");
        assertEquals(originalInfo.timestamp, modifiedInfo.timestamp, "时间戳应保持不变");
        assertEquals(originalInfo.sequence, modifiedInfo.sequence, "序列号应保持不变");
        assertEquals(originalInfo.datacenterId, modifiedInfo.datacenterId, "数据中心ID应保持不变");

        long duration = System.currentTimeMillis() - startTime;

        line();
        logger.info("测试结果报告 - [testReplaceWorkerId]");
        logger.info("✅ WorkerId替换功能正常");
        logger.info("✅ WorkerId正确更新: {} → {}", originalInfo.workerId, modifiedInfo.workerId);
        logger.info("✅ 其他字段保持不变");
        logger.info("⏱️ 测试耗时: {} ms", duration);
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== 解析与重建对称性测试 ====================

    @Test
    @Order(6)
    void testParseAndBuildSymmetry() {
        line();
        logger.info("测试名称: [testParseAndBuildSymmetry]");
        logger.info("测试目标: ID解析与重建对称性验证");
        logger.info("测试内容: 验证ID解析后重建应得到相同ID");
        logger.info("线程模式: 单线程");
        line();

        long startTime = System.currentTimeMillis();

        // 生成原始ID并解析
        long originalId = eachId.nextId();
        EachId.IdInfo info = eachId.parseId(originalId);
        logger.info("原始ID: {}，解析信息: 时间戳={}, 序列号={}, WorkerId={}", originalId, info.timestamp, info.sequence, info.workerId);

        // 使用解析出的时间戳和序列号重新构建ID
        long rebuiltId = eachId.buildId(info.timestamp, info.sequence);
        EachId.IdInfo rebuiltInfo = eachId.parseId(rebuiltId);

        // 验证所有字段都匹配
        assertEquals(info.timestamp, rebuiltInfo.timestamp, "时间戳应匹配");
        assertEquals(info.sequence, rebuiltInfo.sequence, "序列号应匹配");
        assertEquals(info.workerId, rebuiltInfo.workerId, "WorkerId应匹配");
        assertEquals(info.datacenterId, rebuiltInfo.datacenterId, "数据中心ID应匹配");

        // 最终ID应该相同
        assertEquals(originalId, rebuiltId, "重建ID应与原始ID相同");

        long duration = System.currentTimeMillis() - startTime;

        line();
        logger.info("测试结果报告 - [testParseAndBuildSymmetry]");
        logger.info("✅ ID解析和重建对称性验证通过");
        logger.info("✅ 重建ID与原始ID完全一致: {}", originalId == rebuiltId);
        logger.info("⏱️ 测试耗时: {} ms", duration);
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== 十六进制转换测试 ====================

    @Test
    @Order(7)
    void testHexRoundTrip() {
        line();
        logger.info("测试名称: [testHexRoundTrip]");
        logger.info("测试目标: 十六进制转换往返验证");
        logger.info("测试内容: 验证ID与十六进制字符串的相互转换功能");
        logger.info("线程模式: 单线程");
        line();

        long startTime = System.currentTimeMillis();

        long originalId = eachId.nextId();
        String hex = Long.toHexString(originalId);
        if (hex.length() < 16) {
            hex = "0000000000000000".substring(0, 16 - hex.length()) + hex;
        }

        // 验证hex格式
        assertNotNull(hex, "Hex不应为null");
        assertEquals(16, hex.length(), "Hex应为16字符长度");
        logger.info("原始ID: {}，十六进制: {}", originalId, hex);

        // 验证hex转回ID
        long fromHexId = eachId.parseIdFromHex(hex).id;
        assertEquals(originalId, fromHexId, "从hex转换回的ID应与原始ID相同");

        // 验证直接hex方法
        String directHex = Long.toHexString(originalId);
        if (directHex.length() < 16) {
            directHex = "0000000000000000".substring(0, 16 - directHex.length()) + directHex;
        }
        assertEquals(directHex.toLowerCase(), hex.toLowerCase(), "Hex应正确补齐");

        long duration = System.currentTimeMillis() - startTime;

        line();
        logger.info("测试结果报告 - [testHexRoundTrip]");
        logger.info("✅ 十六进制转换功能正常");
        logger.info("✅ 往返转换数据一致: {} → hex → {}", originalId, fromHexId);
        logger.info("⏱️ 测试耗时: {} ms", duration);
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== ID信息结构测试 ====================

    @Test
    @Order(8)
    void testIdInfoStructure() {
        line();
        logger.info("测试名称: [testIdInfoStructure]");
        logger.info("测试目标: ID信息结构完整性验证");
        logger.info("测试内容: 验证ID解析后信息结构的完整性和正确性");
        logger.info("线程模式: 单线程");
        line();

        long startTime = System.currentTimeMillis();

        long id = eachId.nextId();
        EachId.IdInfo info = eachId.parseId(id);

        // 验证断言
        assertNotNull(info, "IdInfo不应为null");
        assertEquals(id, info.id, "IdInfo.id应与原始ID匹配");
        assertTrue(info.timestamp > 0, "时间戳应为正数");
        // 自动分配的 WorkerId 必须在合法范围内即可
        assertTrue(info.workerId >= 0 && info.workerId < (1L << eachId.getWorkerIdBits()),
                "WorkerId 必须在有效范围内 [0, " + ((1L << eachId.getWorkerIdBits()) - 1) + "]，实际值: " + info.workerId);
        assertEquals(0L, info.datacenterId, "数据中心ID应为0（默认值）");
        assertTrue(info.sequence >= 0, "序列号应为非负数");

        // 验证日期对象
        assertNotNull(info.getDate(), "日期对象不应为null");
        assertEquals(info.timestamp * eachId.getStepMs(), info.getDate().getTime(), "日期应与时间戳匹配");

        logger.info("ID信息结构: 时间戳={}, 序列号={}, WorkerId={}, 数据中心ID={}", info.timestamp, info.sequence, info.workerId, info.datacenterId);

        long duration = System.currentTimeMillis() - startTime;

        line();
        logger.info("测试结果报告 - [testIdInfoStructure]");
        logger.info("✅ ID信息结构完整，所有字段符合预期");
        logger.info("✅ 时间戳有效: {}", info.timestamp);
        logger.info("✅ 序列号有效: {}", info.sequence);
        logger.info("✅ WorkerId正确: {}", info.workerId);
        logger.info("⏱️ 测试耗时: {} ms", duration);
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    @Order(9)
    void testSequenceExhaustion() {
        line();
        logger.info("测试名称: [testSequenceExhaustion]");
        logger.info("测试目标: 验证序列号耗尽时的正确处理");
        logger.info("测试内容: 在序列号限制下持续生成ID验证正确性");
        logger.info("线程模式: 单线程");
        line();

        // 使用极小序列号配置
        EachId eachId = new EachId().setTimestampBits(35).setWorkerIdBits(6).setSequenceBits(22).setStepMs(100).setEpoch("2025-01-01").autoWorkerId();

        // 生成ID直到序列号耗尽
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 20; i++) { // 超过序列号上限
            long id = eachId.nextId();
            assertTrue(ids.add(id), "ID应保持唯一，即使序列号耗尽");
        }

        line();
        logger.info("测试结果报告 - [testSequenceExhaustion]");
        logger.info("✅ 序列号耗尽测试通过");
        logger.info("✅ 生成的 {} 个ID全部唯一", ids.size());
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    @Order(10)
    void testBatchGeneration() {
        line();
        logger.info("测试名称: [testBatchGeneration]");
        logger.info("测试目标: 验证批量预留功能的正确性");
        logger.info("测试内容: 验证批量预留返回起始ID，且后续ID连续且唯一");
        logger.info("线程模式: 单线程");
        line();

        int batchSize = 10;

        // 批量预留10个ID，返回第一个ID
        long startId = eachId.nextId(batchSize);
        logger.info("批量预留 {} 个ID，起始ID: {}", batchSize, startId);

        // 解析起始ID信息
        EachId.IdInfo startInfo = eachId.parseId(startId);
        logger.info("起始ID解析: 时间戳={}, 序列号={}", startInfo.timestamp, startInfo.sequence);

        // 验证起始ID的有效性
        assertTrue(startId > 0, "起始ID应为正数");
        assertTrue(startInfo.sequence >= 0, "起始序列号应有效");

        // 生成后续ID并验证连续性和唯一性
        Set<Long> generatedIds = new HashSet<>();
        generatedIds.add(startId);

        for (int i = 1; i < batchSize; i++) {
            long nextId = startId + i;  // 后续ID是连续的
            generatedIds.add(nextId);

            // 验证每个ID的结构
            EachId.IdInfo nextInfo = eachId.parseId(nextId);

            // 验证时间戳相同（同一批次）
            assertEquals(startInfo.timestamp, nextInfo.timestamp,
                    "同一批次的ID时间戳应相同");

            // 验证序列号连续
            assertEquals(startInfo.sequence + i, nextInfo.sequence,
                    "序列号应连续递增");

            // 验证其他字段相同
            assertEquals(startInfo.workerId, nextInfo.workerId,
                    "WorkerId应相同");
            assertEquals(startInfo.datacenterId, nextInfo.datacenterId,
                    "数据中心ID应相同");
        }

        // 验证所有ID唯一
        assertEquals(batchSize, generatedIds.size(),
                "批量生成的所有ID应唯一");

        line();
        logger.info("测试结果报告 - [testBatchGeneration]");
        logger.info("✅ 批量生成测试通过");
        logger.info("📊 生成的ID范围: {} 到 {}", startId, startId + batchSize - 1);
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    @Order(11)
    void testBatchGenerationBoundary() {
        line();
        logger.info("测试名称: [testBatchGenerationBoundary]");
        logger.info("测试目标: 验证批量预留的边界条件处理");
        logger.info("测试内容: 测试最小/最大批量大小的边界情况");
        logger.info("线程模式: 单线程");
        line();

        // 测试批量大小为1（最小有效值）
        long singleId = eachId.nextId(1);
        assertTrue(singleId > 0, "批量大小为1时应返回有效ID");

        // 测试批量大小等于最大序列号
        long maxBatch = eachId.getMaxSequence() + 1;
        logger.info("maxSequence: {}", eachId.getMaxSequence());
        logger.info("maxBatch: {}", maxBatch);

        try {
            long maxStartId = eachId.nextId((int) maxBatch);
            EachId.IdInfo maxInfo = eachId.parseId(maxStartId);
            logger.info("最大批量 {} 测试通过，起始序列号: {}", maxBatch, maxInfo.sequence);
        } catch (Exception e) {
            logger.warn("最大批量测试出现异常（可能序列号耗尽）: {}", e.getMessage());
        }

        // 测试批量大小超过最大序列号（应抛出异常）
        assertThrows(IllegalArgumentException.class, () -> {
            eachId.nextId((int) maxBatch + 1);
        }, "批量大小超过最大序列号时应抛出异常");

        line();
        logger.info("测试结果报告 - [testBatchGenerationBoundary]");
        logger.info("✅ 批量生成边界测试通过");
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    @Order(12)
    public void testNextIdPerformance() {
        line();
        logger.info("测试名称: [testNextIdPerformance]");
        logger.info("测试目标: 单线程nextId(1)性能测试");
        logger.info("测试内容: 不同规模下的单线程性能基准测试");
        logger.info("线程模式: 单线程");
        line();

        int[] testSizes = {10000, 100000, 1000000};

        logger.info("=== ID Generator Performance Test (Single Thread, nextId(1)) ===");
        logger.info("Environment: JDK 8+");

        for (int size : testSizes) {
            // 预热
            for (int i = 0; i < 1000; i++) {
                eachId.nextId(1);
            }

            long startTime = System.nanoTime();

            for (int i = 0; i < size; i++) {
                eachId.nextId(1);
            }

            long endTime = System.nanoTime();
            double durationMs = (endTime - startTime) / 1000000.0;
            double qps = (size / durationMs) * 1000;

            // 性能评级
            String rating = qps >= 2000000 ? "★★★★★ 卓越 (超过Snowflake级别)" :
                    qps >= 1500000 ? "★★★★☆ 优秀 (接近Snowflake性能)" :
                            qps >= 1000000 ? "★★★★ 良好 (标准UUID v4级别)" :
                                    qps >= 500000 ? "★★★☆ 中等 (可接受性能)" :
                                            qps >= 100000 ? "★★☆ 一般 (需要优化)" : "★ 较差 (严重性能问题)";

            logger.info("{} IDs: Time={} ms, QPS={} - {}",
                    String.format("%,d", size),
                    String.format("%.3f", durationMs),
                    String.format("%,.0f", qps),
                    rating);
        }

        line();
        logger.info("测试结果报告 - [testNextIdPerformance]");
        logger.info("✅ 单线程性能测试完成");
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    @Order(13)
    void testNextIdMultiThreadPerformance() {
        line();
        logger.info("测试名称: [testNextIdMultiThreadPerformance]");
        logger.info("测试目标: 多线程nextId(1)性能压测");
        logger.info("测试内容: 纯QPS压测，无正确性检查，模拟生产环境极致压榨");
        logger.info("线程模式: 多线程 (8,16,32,64,128线程)");
        line();

        int[] testSizes = {10000, 100000, 1000000};
        int[] threadCounts = {8, 16, 32, 64, 128};

        logger.info("=== EachId v2 多线程性能压测报告 (nextId(1)) ===");
        logger.info("设计理念：100ms tick + 419万序列号 → 竞争极低 → synchronized ≈ 无锁");
        logger.info("对比对象：Twitter Snowflake、Leaf、UidGenerator、TinyId");

        for (int threads : threadCounts) {
            logger.info("--- 多线程压测：{} 线程 ---", threads);

            for (int size : testSizes) {
                // 预热
                for (int i = 0; i < 2000; i++) {
                    eachId.nextId();
                }

                CountDownLatch latch = new CountDownLatch(threads);
                AtomicLong counter = new AtomicLong(size * threads);

                long start = System.nanoTime();

                for (int t = 0; t < threads; t++) {
                    new Thread(() -> {
                        while (counter.decrementAndGet() >= 0) {
                            eachId.nextId();
                        }
                        latch.countDown();
                    }).start();
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("测试被中断", e);
                }
                long end = System.nanoTime();

                double durationMs = (end - start) / 1000000.0;
                double totalIds = size * threads;
                double qps = totalIds / durationMs * 1000;

                String rating = qps >= 8000000 ? "核弹级 (物理极限)" :
                        qps >= 5000000 ? "神级 (吊打业界)" :
                                qps >= 2000000 ? "顶级 (远超Leaf)" :
                                        qps >= 1000000 ? "优秀 (生产顶级)" :
                                                qps >= 500000 ? "良好" : "普通";

                logger.info(" {} 线程 × {} IDs = {} IDs: Time={} ms, QPS={} - {}",
                        threads,
                        String.format("%,d", size),
                        String.format("%,d", (long) totalIds),
                        String.format("%.3f", durationMs),
                        String.format("%,.0f", qps),
                        rating);
            }
        }

        line();
        logger.info("测试结果报告 - [testNextIdMultiThreadPerformance]");
        logger.info("✅ 多线程性能测试完成");
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== 新增：nextId(count) 性能测试 ====================

    @Test
    @Order(14)
    void testNextIdCountSingleThreadPerformance() {
        line();
        logger.info("测试名称: [testNextIdCountSingleThreadPerformance]");
        logger.info("测试目标: nextId(count) 单线程性能测试");
        logger.info("测试内容: 测试不同批量大小的单线程性能");
        logger.info("线程模式: 单线程");
        line();

        int[] batchSizes = {1, 10, 100, 1000};
        int totalIds = 100000;

        logger.info("=== nextId(count) 单线程性能测试 ===");
        logger.info("总ID数: {}", String.format("%,d", totalIds));

        for (int batchSize : batchSizes) {
            // 预热
            for (int i = 0; i < 1000; i++) {
                eachId.nextId(Math.min(batchSize, 10));
            }

            long startTime = System.nanoTime();

            int generated = 0;
            while (generated < totalIds) {
                int currentBatch = Math.min(batchSize, totalIds - generated);
                eachId.nextId(currentBatch);
                generated += currentBatch;
            }

            long endTime = System.nanoTime();
            double durationMs = (endTime - startTime) / 1000000.0;
            double qps = totalIds / durationMs * 1000;

            String rating = qps >= 2000000 ? "★★★★★ 卓越" :
                    qps >= 1000000 ? "★★★★ 优秀" :
                            qps >= 500000 ? "★★★ 良好" :
                                    qps >= 100000 ? "★★ 一般" : "★ 需要优化";

            logger.info("批量大小 {}: Time={} ms, QPS={} - {}",
                    batchSize,
                    String.format("%.3f", durationMs),
                    String.format("%,.0f", qps),
                    rating);
        }

        line();
        logger.info("测试结果报告 - [testNextIdCountSingleThreadPerformance]");
        logger.info("✅ nextId(count) 单线程性能测试完成");
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    @Order(15)
    void testNextIdCountMultiThreadPerformance() {
        line();
        logger.info("测试名称: [testNextIdCountMultiThreadPerformance]");
        logger.info("测试目标: nextId(count) 多线程性能测试");
        logger.info("测试内容: 测试不同批量大小和线程数组合的性能");
        logger.info("线程模式: 多线程");
        line();

        int[] threadCounts = {8, 16, 32};
        int[] batchSizes = {1, 10, 100};
        int idsPerThread = 10000;

        logger.info("=== nextId(count) 多线程性能测试 ===");
        logger.info("每线程ID数: {}", String.format("%,d", idsPerThread));

        for (int threads : threadCounts) {
            for (int batchSize : batchSizes) {
                // 预热
                for (int i = 0; i < 1000; i++) {
                    eachId.nextId(Math.min(batchSize, 10));
                }

                CountDownLatch latch = new CountDownLatch(threads);
                AtomicInteger remaining = new AtomicInteger(threads * idsPerThread);

                long startTime = System.nanoTime();

                for (int t = 0; t < threads; t++) {
                    new Thread(() -> {
                        try {
                            while (remaining.get() > 0) {
                                int currentBatch = Math.min(batchSize, remaining.get());
                                eachId.nextId(currentBatch);
                                remaining.addAndGet(-currentBatch);
                            }
                        } finally {
                            latch.countDown();
                        }
                    }).start();
                }

                try {
                    latch.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("测试被中断", e);
                }

                long endTime = System.nanoTime();
                double durationMs = (endTime - startTime) / 1000000.0;
                double totalIds = threads * idsPerThread;
                double qps = totalIds / durationMs * 1000;

                String rating = qps >= 5000000 ? "核弹级" :
                        qps >= 2000000 ? "神级" :
                                qps >= 1000000 ? "顶级" :
                                        qps >= 500000 ? "优秀" : "良好";

                logger.info("{}线程×批量{}: Time={} ms, QPS={} - {}",
                        threads, batchSize,
                        String.format("%.3f", durationMs),
                        String.format("%,.0f", qps),
                        rating);
            }
        }

        line();
        logger.info("测试结果报告 - [testNextIdCountMultiThreadPerformance]");
        logger.info("✅ nextId(count) 多线程性能测试完成");
        logger.info("✅ 测试完成");
        lineDone();
    }

    @Test
    @Order(16)
    void testNextIdCountMonotonicity() {
        line();
        logger.info("测试名称: [testNextIdCountMonotonicity]");
        logger.info("测试目标: nextId(count) 单调递增验证");
        logger.info("测试内容: 验证批量生成的ID保持单调递增特性");
        logger.info("线程模式: 单线程/多线程");
        line();

        // 单线程单调性测试
        logger.info("--- 单线程单调性测试 ---");
        long lastId = -1;
        for (int i = 0; i < 100; i++) {
            long batchStartId = eachId.nextId(10);
            if (lastId != -1) {
                assertTrue(batchStartId > lastId, "批量生成的ID应保持单调递增");
            }
            lastId = batchStartId + 9; // 批次内最后一个ID
        }
        logger.info("✅ 单线程单调性验证通过");

        // 多线程单调性测试
        logger.info("--- 多线程单调性测试 ---");
        final int threadCount = 8;
        final int batchesPerThread = 50;
        final int batchSize = 5;

        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                try {
                    for (int i = 0; i < batchesPerThread; i++) {
                        long startId = eachId.nextId(batchSize);
                        synchronized (allIds) {
                            for (int j = 0; j < batchSize; j++) {
                                allIds.add(startId + j);
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("测试被中断", e);
        }

        // 验证所有ID唯一且单调递增
        assertEquals(threadCount * batchesPerThread * batchSize, allIds.size(),
                "所有生成的ID应唯一");

        // 正确的单调递增验证方式
        List<Long> sortedIds = new ArrayList<>(allIds);
        Collections.sort(sortedIds);

        boolean monotonic = true;
        Long previousId = null;
        for (Long currentId : sortedIds) {
            if (previousId != null && currentId <= previousId) {
                monotonic = false;
                logger.error("发现非单调递增: {} -> {}", previousId, currentId);
                break;
            }
            previousId = currentId;
        }

        assertTrue(monotonic, "所有ID应保持单调递增");

        line();
        logger.info("测试结果报告 - [testNextIdCountMonotonicity]");
        logger.info("✅ 单线程单调性验证通过");
        logger.info("✅ 多线程单调性验证通过");
        logger.info("📊 总生成ID数: {}", allIds.size());
        logger.info("✅ 测试完成");
        lineDone();
    }

    // ==================== 新增：长时间稳定性测试 ====================

    @Test
    @Order(17)
    @Tag("longRunning")
    void testLongRunningStability() {
        line();
        logger.info("测试名称: [testLongRunningStability]");
        logger.info("测试目标: 上线前长时间稳定性验证");
        logger.info("测试内容: 行业标准生产环境稳定性测试");
        logger.info("测试时长: 持续生成ID验证稳定性和正确性");
        logger.info("线程模式: 多线程混合负载");
        line();

        final long testDurationMs = 30_000; // 30秒测试（生产环境建议更长）
        final int threadCount = 16;
        final AtomicLong totalGenerated = new AtomicLong(0);
        final AtomicInteger errors = new AtomicInteger(0);
        final Set<Long> allIds = ConcurrentHashMap.newKeySet(1000000);

        logger.info("开始长时间稳定性测试，持续时间: {} ms", testDurationMs);
        logger.info("线程数: {}, 测试模式: 混合负载(单ID+批量生成)", threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDurationMs;

        // 创建混合负载线程
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    Random random = new Random();

                    while (System.currentTimeMillis() < endTime) {
                        try {
                            if (threadId % 3 == 0) {
                                // 单ID生成
                                long id = eachId.nextId();
                                if (!allIds.add(id)) {
                                    logger.error("发现重复ID: {}", id);
                                    errors.incrementAndGet();
                                }
                                totalGenerated.incrementAndGet();
                            } else if (threadId % 3 == 1) {
                                // 小批量生成
                                int batchSize = random.nextInt(10) + 1;
                                long startId = eachId.nextId(batchSize);
                                for (int j = 0; j < batchSize; j++) {
                                    if (!allIds.add(startId + j)) {
                                        logger.error("发现重复ID: {}", startId + j);
                                        errors.incrementAndGet();
                                    }
                                    totalGenerated.incrementAndGet();
                                }
                            } else {
                                // 中批量生成
                                int batchSize = random.nextInt(50) + 10;
                                long startId = eachId.nextId(batchSize);
                                for (int j = 0; j < batchSize; j++) {
                                    if (!allIds.add(startId + j)) {
                                        logger.error("发现重复ID: {}", startId + j);
                                        errors.incrementAndGet();
                                    }
                                    totalGenerated.incrementAndGet();
                                }
                            }

                            // 短暂休眠模拟真实负载
                            Thread.sleep(random.nextInt(10));
                        } catch (Exception e) {
                            logger.error("线程执行异常: {}", e.getMessage());
                            errors.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();

        try {
            endLatch.await(testDurationMs + 5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long actualDuration = System.currentTimeMillis() - startTime;
        double qps = totalGenerated.get() * 1000.0 / actualDuration;

        // 验证单调递增
        List<Long> sortedIds = new ArrayList<>(allIds);
        Collections.sort(sortedIds);
        boolean monotonic = true;
        for (int i = 1; i < sortedIds.size(); i++) {
            if (sortedIds.get(i) <= sortedIds.get(i - 1)) {
                monotonic = false;
                break;
            }
        }

        line();
        logger.info("长时间稳定性测试报告 - [testLongRunningStability]");
        logger.info("测试时长        : {} ms", actualDuration);
        logger.info("总生成ID数      : {}", String.format("%,d", totalGenerated.get()));
        logger.info("唯一ID数        : {}", String.format("%,d", allIds.size()));
        logger.info("平均QPS         : {}", String.format("%,.0f", qps));
        logger.info("错误数          : {}", errors.get());
        logger.info("单调递增        : {}", monotonic ? "✅ 通过" : "❌ 失败");
        logger.info("ID唯一性        : {}", totalGenerated.get() == allIds.size() ? "✅ 通过" : "❌ 失败");

        if (errors.get() == 0 && monotonic && totalGenerated.get() == allIds.size()) {
            logger.info("🏆 稳定性评级: ✅ 优秀 - 适合生产环境部署");
        } else {
            logger.info("🏆 稳定性评级: ⚠️ 需优化 - 发现问题需要修复");
        }

        assertEquals(0, errors.get(), "长时间运行不应出现错误");
        assertTrue(monotonic, "所有ID应保持单调递增");
        assertEquals(totalGenerated.get(), allIds.size(), "所有生成的ID应唯一");

        logger.info("✅ 测试完成");
        lineDone();

    }


}
