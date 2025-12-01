import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CurrentTimeMillisBenchmark {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        long durationMs = 1000; // 测试持续时间：1秒
        System.out.println("System.currentTimeMillis() 基准测试结果（每秒调用次数）:");

        // 单线程测试
        long singleThreadCount = runBenchmark(1, durationMs);
        System.out.printf("单线程: %,d 次/秒%n", singleThreadCount);

        // 2线程测试
        long twoThreadCount = runBenchmark(2, durationMs);
        System.out.printf("2线程: %,d 次/秒%n", twoThreadCount);

        // 4线程测试
        long fourThreadCount = runBenchmark(4, durationMs);
        System.out.printf("4线程: %,d 次/秒%n", fourThreadCount);

        // 8线程测试
        long eightThreadCount = runBenchmark(8, durationMs);
        System.out.printf("8线程: %,d 次/秒%n", eightThreadCount);

        // 16线程测试
        long sixteenThreadCount = runBenchmark(16, durationMs);
        System.out.printf("16线程: %,d 次/秒%n", sixteenThreadCount);
    }

    private static long runBenchmark(int threadCount, long durationMs) throws InterruptedException, ExecutionException {
        long durationNs = durationMs * 1_000_000; // 转换为纳秒
        long startTime = System.nanoTime();
        long endTime = startTime + durationNs;

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Long>> futures = new ArrayList<>();

        // 提交任务
        for (int i = 0; i < threadCount; i++) {
            Callable<Long> task = new TimeMillisTask(endTime);
            futures.add(executor.submit(task));
        }

        executor.shutdown(); // 停止接受新任务

        // 汇总所有线程的调用次数
        long totalCalls = 0;
        for (Future<Long> future : futures) {
            totalCalls += future.get();
        }

        return totalCalls; // 总调用次数即为每秒调用次数（因测试时间固定为1秒）
    }

    // 任务类：在指定时间内循环调用 System.currentTimeMillis()
    private static class TimeMillisTask implements Callable<Long> {
        private final long endTime;

        public TimeMillisTask(long endTime) {
            this.endTime = endTime;
        }

        @Override
        public Long call() {
            long count = 0;
            // 循环直到达到结束时间
            while (System.nanoTime() < endTime) {
                System.currentTimeMillis(); // 调用目标方法
                count++;
            }
            return count;
        }
    }
}
