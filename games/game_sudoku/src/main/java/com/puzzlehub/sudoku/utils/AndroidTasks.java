package com.puzzlehub.sudoku.utils;

import android.os.Handler;
import android.os.Looper;
import androidx.core.util.Consumer;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleOwnerKt;
import androidx.lifecycle.RepeatOnLifecycleKt;
import java.util.concurrent.TimeoutException;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.flow.Flow;

/** Delivers asynchronous results on the main thread and observes lifecycle-bound state. */
public final class AndroidTasks {
    private AndroidTasks() {}

    public static <T> SudokuTask<T> withTimeout(SudokuTask<T> operation, long timeoutMs) {
        SudokuTask<T> result = new SudokuTask<>();
        Handler main = new Handler(Looper.getMainLooper());
        Runnable expired = () -> result.completeExceptionally(new TimeoutException());
        main.postDelayed(expired, timeoutMs);
        operation.whenComplete((value, error) -> {
            main.removeCallbacks(expired);
            if (error == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    public static <T> void onComplete(SudokuTask<T> future,
                                      Consumer<T> success, Consumer<Throwable> failure) {
        Handler main = new Handler(Looper.getMainLooper());
        future.whenComplete((value, error) -> main.post(() -> {
            if (error == null) {
                success.accept(value);
            } else if (error instanceof SudokuTaskException && error.getCause() != null) {
                failure.accept(error.getCause());
            } else {
                failure.accept(error);
            }
        }));
    }

    public static <T> Job observe(LifecycleOwner owner, Flow<T> source, Consumer<T> observer) {
        return BuildersKt.launch(LifecycleOwnerKt.getLifecycleScope(owner), EmptyCoroutineContext.INSTANCE,
                CoroutineStart.DEFAULT, (scope, continuation) -> RepeatOnLifecycleKt.repeatOnLifecycle(
                        owner, Lifecycle.State.STARTED, (started, collecting) -> source.collect((value, emitted) -> {
                            observer.accept(value);
                            return Unit.INSTANCE;
                        }, collecting), continuation));
    }
}
