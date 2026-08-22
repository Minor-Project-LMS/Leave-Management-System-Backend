package com.lms.Leave_Management_System_Backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncConfigTest {

    @Test
    void asyncConfig_ShouldCreateTaskExecutor() {
        AsyncConfig asyncConfig = new AsyncConfig();
        Executor executor = asyncConfig.taskExecutor();

        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals("async-", taskExecutor.getThreadNamePrefix());
        assertEquals(5, taskExecutor.getCorePoolSize());
        assertEquals(10, taskExecutor.getMaxPoolSize());
        assertEquals(100, taskExecutor.getQueueCapacity());
    }

    @Test
    void taskExecutor_ShouldExecuteAsyncTask() throws InterruptedException {
        AsyncConfig asyncConfig = new AsyncConfig();
        Executor executor = asyncConfig.taskExecutor();
        
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        taskExecutor.initialize();

        StringBuilder result = new StringBuilder();

        executor.execute(() -> {
            result.append("Async task executed");
        });

        // Wait for async execution
        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals("Async task executed", result.toString());
        
        taskExecutor.shutdown();
    }

    @Test
    void taskExecutor_ShouldHandleMultipleTasks() throws InterruptedException {
        AsyncConfig asyncConfig = new AsyncConfig();
        Executor executor = asyncConfig.taskExecutor();
        
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        taskExecutor.initialize();

        int taskCount = 5;
        int[] completedTasks = {0};

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            executor.execute(() -> {
                completedTasks[0]++;
            });
        }

        // Wait for all async executions
        TimeUnit.MILLISECONDS.sleep(500);

        assertEquals(taskCount, completedTasks[0]);
        
        taskExecutor.shutdown();
    }
}
