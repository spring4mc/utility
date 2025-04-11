package org.spring4mc.utility.executor;

import lombok.NonNull;

import java.util.concurrent.Executor;

public interface ThreadAwareExecutor extends Executor {
    boolean isInExecutorThread();

    static class CurrentThread implements ThreadAwareExecutor {
        @Override
        public boolean isInExecutorThread() {
            return true;
        }

        @Override
        public void execute(@NonNull Runnable command) {
            command.run();
        }
    }
}
