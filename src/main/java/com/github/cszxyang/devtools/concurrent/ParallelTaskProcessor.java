package com.github.cszxyang.devtools.concurrent;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

public class ParallelTaskProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ParallelTaskProcessor.class);

    private static final ThreadPoolTaskExecutor EXECUTOR = new VisualThreadPoolTaskExecutor();

    static {
        EXECUTOR.setCorePoolSize(16);
        EXECUTOR.setMaxPoolSize(100);
        EXECUTOR.setQueueCapacity(5000);
        EXECUTOR.setThreadNamePrefix("parallel_supplier_utils");
        EXECUTOR.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        EXECUTOR.setWaitForTasksToCompleteOnShutdown(true);
        EXECUTOR.setAwaitTerminationSeconds(60 * 30);
        EXECUTOR.initialize();
        logger.info("Thread pool initialization finished. corePoolSize: {}, maxPoolSize: {}",
                EXECUTOR.getPoolSize(), EXECUTOR.getMaxPoolSize());
    }

    @Data
    public static class ResultHolder<F, S, T> {
        private F first;
        private S second;
        private T third;
    }

    /**
     * Perform three tasks in parallel
     *
     * @param first  the first task
     * @param second the second task
     * @param third  the third task
     * @param <F>    type of the first task
     * @param <S>    type of the second task
     * @param <T>    type of the third task
     * @return a ResultHolder
     */
    public static <F, S, T> ResultHolder<F, S, T> procAsync(Supplier<F> first, Supplier<S> second, Supplier<T> third) {
        ResultHolder<F, S, T> resultHolder = new ResultHolder<>();
        List<CompletableFuture> futures = new ArrayList<>(3);
        if (Objects.nonNull(first)) {
            futures.add(CompletableFuture.supplyAsync(first, EXECUTOR).thenAccept(resultHolder::setFirst));
        }
        if (Objects.nonNull(second)) {
            futures.add(CompletableFuture.supplyAsync(second, EXECUTOR).thenAccept(resultHolder::setSecond));
        }
        if (Objects.nonNull(third)) {
            futures.add(CompletableFuture.supplyAsync(third, EXECUTOR).thenAccept(resultHolder::setThird));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return resultHolder;
    }

    /**
     * consume a collection of suppliers and collect res in a certain way
     *
     * @param suppliers method hangle
     * @param distinct  distinct result
     * @param <R>       result type
     * @return a collection of result
     */
    public static <R> Collection<R> procAsync(Collection<Supplier<R>> suppliers, boolean distinct) {
        if (CollectionUtils.isEmpty(suppliers)) {
            return distinct ? Collections.emptySet() : Collections.emptyList();
        }
        Collection<R> res = distinct ? new HashSet<>() : new ArrayList<>();
        // wait for all tasks to finish
        CompletableFuture.allOf(suppliers.stream().filter(Objects::nonNull).map(supplier ->
                CompletableFuture.supplyAsync(supplier, EXECUTOR).thenAccept(r -> {
                    if (Objects.nonNull(r)) {
                        res.add(r);
                    }
                })
        ).toArray(CompletableFuture[]::new)).join();
        return res;
    }
}
