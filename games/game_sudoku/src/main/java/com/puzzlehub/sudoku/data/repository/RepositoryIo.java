package com.puzzlehub.sudoku.data.repository;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.puzzlehub.sudoku.utils.SudokuTask;
import com.puzzlehub.sudoku.utils.SudokuTaskException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class RepositoryIo {
    private static final ExecutorService POOL = Executors.newCachedThreadPool(job -> {
        Thread thread = new Thread(job, "sudoku-repo-pool");
        thread.setDaemon(true);
        return thread;
    });

    private RepositoryIo() {}

    @FunctionalInterface interface Action<T> { T run() throws Exception; }

    static Executor pool() {
        return POOL;
    }

    static Executor serial(String name) {
        return Executors.newSingleThreadExecutor(job -> {
            Thread thread = new Thread(job, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    static <T> SudokuTask<T> submit(Executor executor, Action<T> action) {
        return SudokuTask.supplyAsync(() -> {
            try {
                return action.run();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new SudokuTaskException(error);
            } catch (Exception error) {
                throw new SudokuTaskException(error);
            }
        }, executor);
    }

    static <T> T await(Task<T> task, long timeoutMs) throws Exception {
        try {
            return Tasks.await(task, timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException error) {
            throw unwrap(error);
        }
    }

    static <T> T await(SudokuTask<T> future, long timeoutMs) throws Exception {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException error) {
            throw unwrap(error);
        }
    }

    private static Exception unwrap(ExecutionException error) {
        if (error.getCause() instanceof Exception cause) {
            return cause;
        }
        return error;
    }
}
