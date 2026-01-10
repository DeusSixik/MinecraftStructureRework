package dev.sixik.mcsr.task_system;

import java.util.function.Function;

public final class CoroutineTasks {

    /**
     * Создает задачу на основе итератора или коллекции.
     * Идеально для обработки списков объектов (например, чанков или сущностей).
     */
    public static <T, R> CoroutineScheduler.StepTask<T, R> forEach(
            Iterable<T> iterable,
            java.util.function.BiConsumer<T, CoroutineScheduler.TaskContext<T>> action,
            R finalResult
    ) {
        var it = iterable.iterator();
        return ctx -> {
            if (!it.hasNext()) return new CoroutineScheduler.Step.Done<>(finalResult);
            T item = it.next();
            action.accept(item, ctx);
            return new CoroutineScheduler.Step.Continue<>();
        };
    }

    /**
     * Гибкий построитель для задач с внутренним состоянием.
     */
    public static <S, Y, R> Builder<S, Y, R> builder(S initialState) {
        return new Builder<>(initialState);
    }

    public static class Builder<S, Y, R> {
        private S state;
        private Function<S, CoroutineScheduler.Step<Y, R>> stepLogic;

        private Builder(S state) { this.state = state; }

        public Builder<S, Y, R> next(Function<S, CoroutineScheduler.Step<Y, R>> logic) {
            this.stepLogic = logic;
            return this;
        }

        public CoroutineScheduler.StepTask<Y, R> build() {
            return ctx -> {
                ctx.checkpoint();
                return stepLogic.apply(state);
            };
        }
    }
}
