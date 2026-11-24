package com.puzzlehub.sudoku.utils;

import androidx.core.util.Consumer;
import androidx.core.util.Supplier;
import com.puzzlehub.sudoku.utils.SudokuCallbacks.BiCallback;
import com.puzzlehub.sudoku.utils.SudokuCallbacks.Func;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** API 23-compatible asynchronous task matching CompletableFuture semantics. */
public final class SudokuTask<T> {
    private static final ExecutorService DEFAULT_POOL = Executors.newCachedThreadPool(job -> {
        Thread thread = new Thread(job, "sudoku-async");
        thread.setDaemon(true);
        return thread;
    });

    private final Object lock = new Object();
    private final CountDownLatch latch = new CountDownLatch(1);
    private final List<BiCallback<T, Throwable>> listeners = new ArrayList<>();
    private boolean done;
    private boolean cancelled;
    private T value;
    private Throwable error;

    public static <T> SudokuTask<T> completedFuture(T value) {
        SudokuTask<T> task = new SudokuTask<>();
        task.complete(value);
        return task;
    }

    public static <T> SudokuTask<T> failedFuture(Throwable error) {
        SudokuTask<T> task = new SudokuTask<>();
        task.completeExceptionally(error);
        return task;
    }

    public static <T> SudokuTask<T> supplyAsync(Supplier<T> supplier) {
        return supplyAsync(supplier, DEFAULT_POOL);
    }

    public static <T> SudokuTask<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        SudokuTask<T> task = new SudokuTask<>();
        executor.execute(() -> {
            if (task.isCancelled()) {
                return;
            }
            try {
                task.complete(supplier.get());
            } catch (Throwable failure) {
                task.completeExceptionally(failure);
            }
        });
        return task;
    }

    public static SudokuTask<Void> runAsync(Runnable action) {
        return runAsync(action, DEFAULT_POOL);
    }

    public static SudokuTask<Void> runAsync(Runnable action, Executor executor) {
        return supplyAsync(() -> {
            action.run();
            return null;
        }, executor);
    }

    public boolean complete(T result) {
        List<BiCallback<T, Throwable>> snapshot;
        synchronized (lock) {
            if (done) {
                return false;
            }
            done = true;
            value = result;
            snapshot = new ArrayList<>(listeners);
            listeners.clear();
            latch.countDown();
        }
        for (BiCallback<T, Throwable> listener : snapshot) {
            listener.accept(result, null);
        }
        return true;
    }

    public boolean completeExceptionally(Throwable failure) {
        Throwable wrapped = failure != null ? failure : new IllegalStateException("Unknown error");
        List<BiCallback<T, Throwable>> snapshot;
        synchronized (lock) {
            if (done) {
                return false;
            }
            done = true;
            error = wrapped;
            snapshot = new ArrayList<>(listeners);
            listeners.clear();
            latch.countDown();
        }
        for (BiCallback<T, Throwable> listener : snapshot) {
            listener.accept(null, wrapped);
        }
        return true;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        List<BiCallback<T, Throwable>> snapshot;
        CancellationException cancellation = new CancellationException();
        synchronized (lock) {
            if (done) {
                return false;
            }
            done = true;
            cancelled = true;
            error = cancellation;
            snapshot = new ArrayList<>(listeners);
            listeners.clear();
            latch.countDown();
        }
        for (BiCallback<T, Throwable> listener : snapshot) {
            listener.accept(null, cancellation);
        }
        return true;
    }

    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    public boolean isDone() {
        synchronized (lock) {
            return done;
        }
    }

    public SudokuTask<T> whenComplete(BiCallback<T, Throwable> callback) {
        T currentValue;
        Throwable currentError;
        boolean invokeNow;
        synchronized (lock) {
            if (done) {
                currentValue = value;
                currentError = error;
                invokeNow = true;
            } else {
                listeners.add(callback);
                currentValue = null;
                currentError = null;
                invokeNow = false;
            }
        }
        if (invokeNow) {
            callback.accept(currentValue, currentError);
        }
        return this;
    }

    public <R> SudokuTask<R> thenApply(Func<T, R> mapper) {
        SudokuTask<R> next = new SudokuTask<>();
        whenComplete((result, failure) -> {
            if (failure != null) {
                next.completeExceptionally(failure);
            } else {
                try {
                    next.complete(mapper.apply(result));
                } catch (Throwable ex) {
                    next.completeExceptionally(ex);
                }
            }
        });
        return next;
    }

    public <R> SudokuTask<R> thenApplyAsync(Func<T, R> mapper) {
        SudokuTask<R> next = new SudokuTask<>();
        whenComplete((result, failure) -> {
            if (failure != null) {
                next.completeExceptionally(failure);
            } else {
                DEFAULT_POOL.execute(() -> {
                    try {
                        next.complete(mapper.apply(result));
                    } catch (Throwable ex) {
                        next.completeExceptionally(ex);
                    }
                });
            }
        });
        return next;
    }

    public <R> SudokuTask<R> thenCompose(Func<T, SudokuTask<R>> mapper) {
        SudokuTask<R> next = new SudokuTask<>();
        whenComplete((result, failure) -> {
            if (failure != null) {
                next.completeExceptionally(failure);
            } else {
                try {
                    SudokuTask<R> inner = mapper.apply(result);
                    if (inner == null) {
                        next.complete(null);
                    } else {
                        inner.whenComplete((innerVal, innerErr) -> {
                            if (innerErr != null) {
                                next.completeExceptionally(innerErr);
                            } else {
                                next.complete(innerVal);
                            }
                        });
                    }
                } catch (Throwable ex) {
                    next.completeExceptionally(ex);
                }
            }
        });
        return next;
    }

    public <R> SudokuTask<R> thenComposeAsync(Func<T, SudokuTask<R>> mapper, Executor executor) {
        SudokuTask<R> next = new SudokuTask<>();
        whenComplete((result, failure) -> {
            if (failure != null) {
                next.completeExceptionally(failure);
            } else {
                executor.execute(() -> {
                    try {
                        SudokuTask<R> inner = mapper.apply(result);
                        if (inner == null) {
                            next.complete(null);
                        } else {
                            inner.whenComplete((innerVal, innerErr) -> {
                                if (innerErr != null) {
                                    next.completeExceptionally(innerErr);
                                } else {
                                    next.complete(innerVal);
                                }
                            });
                        }
                    } catch (Throwable ex) {
                        next.completeExceptionally(ex);
                    }
                });
            }
        });
        return next;
    }

    public SudokuTask<Void> thenAccept(Consumer<T> action) {
        return thenApply(result -> {
            action.accept(result);
            return null;
        });
    }

    public SudokuTask<Void> thenRunAsync(Runnable action, Executor executor) {
        SudokuTask<Void> next = new SudokuTask<>();
        whenComplete((result, failure) -> {
            if (failure != null) {
                next.completeExceptionally(failure);
            } else {
                executor.execute(() -> {
                    try {
                        action.run();
                        next.complete(null);
                    } catch (Throwable ex) {
                        next.completeExceptionally(ex);
                    }
                });
            }
        });
        return next;
    }

    public interface BiFunc<A, B, R> {
        R apply(A first, B second);
    }

    public <R> SudokuTask<R> handleAsync(BiFunc<T, Throwable, R> handler, Executor executor) {
        SudokuTask<R> next = new SudokuTask<>();
        whenComplete((result, failure) -> executor.execute(() -> {
            try {
                Throwable unwrapped = failure;
                if (unwrapped instanceof SudokuTaskException && unwrapped.getCause() != null) {
                    unwrapped = unwrapped.getCause();
                }
                next.complete(handler.apply(result, unwrapped));
            } catch (Throwable ex) {
                next.completeExceptionally(ex);
            }
        }));
        return next;
    }

    public SudokuTask<T> exceptionally(Func<Throwable, T> fallback) {
        SudokuTask<T> next = new SudokuTask<>();
        whenComplete((result, failure) -> {
            if (failure == null) {
                next.complete(result);
            } else {
                try {
                    next.complete(fallback.apply(failure));
                } catch (Throwable ex) {
                    next.completeExceptionally(ex);
                }
            }
        });
        return next;
    }

    public T get() throws InterruptedException, ExecutionException {
        latch.await();
        return unwrapResult();
    }

    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!latch.await(timeout, unit)) {
            throw new TimeoutException();
        }
        return unwrapResult();
    }

    private T unwrapResult() throws ExecutionException {
        synchronized (lock) {
            if (cancelled) {
                throw new CancellationException();
            }
            if (error != null) {
                if (error instanceof SudokuTaskException && error.getCause() != null) {
                    throw new ExecutionException(error.getCause());
                }
                throw new ExecutionException(error);
            }
            return value;
        }
    }
}
