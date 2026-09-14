package ai.loomspan.sidecar.execution;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import ai.loomspan.api.AdmittedSkillInvocation;
import ai.loomspan.api.SkillExecutionEvent;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.sidecar.config.SidecarExecutionProperties;
import ai.loomspan.sidecar.security.ExecutionOwner;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("sidecarExecutionCoordinator")
public class ExecutionCoordinator implements ApplicationListener<ContextClosedEvent> {
    private final SkillInvocationHandoff skillInvocationHandoff;
    private final SidecarExecutionProperties properties;
    private final ApplicationContext owningContext;
    private final Clock clock;
    private final Map<UUID, ExecutionRecord> records = new java.util.concurrent.ConcurrentHashMap<>();
    private final ReentrantLock gate = new ReentrantLock();
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService expirationSweeper;
    private final Consumer<ExecutionTask> beforeDispatchHandoff;
    private boolean open = true;
    private int queuedCount;
    private long queuedBytes;

    @Autowired
    public ExecutionCoordinator(SkillInvocationHandoff skillInvocationHandoff, SidecarExecutionProperties properties,
            ApplicationContext owningContext) {
        this(skillInvocationHandoff, properties, owningContext, Clock.systemUTC());
    }

    ExecutionCoordinator(SkillInvocationHandoff skillInvocationHandoff, SidecarExecutionProperties properties,
            ApplicationContext owningContext, Clock clock) {
        this(skillInvocationHandoff, properties, owningContext, clock, task -> { });
    }

    ExecutionCoordinator(SkillInvocationHandoff skillInvocationHandoff, SidecarExecutionProperties properties,
            ApplicationContext owningContext, Clock clock, Consumer<ExecutionTask> beforeDispatchHandoff) {
        properties.validate();
        this.skillInvocationHandoff = skillInvocationHandoff;
        this.properties = properties;
        this.owningContext = owningContext;
        this.clock = clock;
        this.beforeDispatchHandoff = beforeDispatchHandoff;
        BlockingQueue<Runnable> queue = new AdmissionQueue();
        this.executor = new ThreadPoolExecutor(properties.getMaxConcurrent(), properties.getMaxConcurrent(),
                0L, TimeUnit.MILLISECONDS, queue,
                Thread.ofPlatform().name("sidecar-execution-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.expirationSweeper = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("sidecar-expiration-sweep").factory());
        long sweepMillis = Math.max(1L, Math.min(60_000L, properties.getCompletedTtl().toMillis()));
        expirationSweeper.scheduleWithFixedDelay(this::expireSafely, sweepMillis, sweepMillis,
                TimeUnit.MILLISECONDS);
    }

    public UUID admit(String skillName, Map<String, Object> input, long inputBytes,
            ExecutionOwner owner, JwtAuthenticationToken authentication) {
        gate.lock();
        try {
            if (!open) throw new ExecutionUnavailableException();
            expireLocked(clock.instant());
            if (records.size() >= properties.getMaxRetained()) {
                throw new ExecutionCapacityException("Retained execution capacity is full");
            }
            UUID id = UUID.randomUUID();
            var record = new ExecutionRecord(id, skillName, owner, clock.instant());
            var task = new ExecutionTask(this, record, input, inputBytes, authentication);
            records.put(id, record);
            try {
                executor.execute(task);
            } catch (RejectedExecutionException failure) {
                records.remove(id, record);
                task.discardLocked();
                throw new ExecutionCapacityException("Execution queue capacity is full");
            }
            return id;
        } finally {
            gate.unlock();
        }
    }

    public Optional<ExecutionSnapshot> find(UUID id, ExecutionOwner owner) {
        gate.lock();
        try {
            expireLocked(clock.instant());
            var record = records.get(id);
            return record != null && record.owner.equals(owner) ? Optional.of(record.snapshot) : Optional.empty();
        } finally {
            gate.unlock();
        }
    }

    private void expireLocked(Instant now) {
        records.values().removeIf(record -> record.snapshot.completedAt() != null
                && !now.isBefore(record.snapshot.completedAt().plus(properties.getCompletedTtl())));
    }

    private void expireSafely() {
        gate.lock();
        try {
            expireLocked(clock.instant());
        } finally {
            gate.unlock();
        }
    }

    AdmittedSkillInvocation handoff(ExecutionTask task) {
        gate.lock();
        try {
            task.releaseQueueReservationLocked();
            if (!open || records.get(task.record.id) != task.record) {
                records.remove(task.record.id, task.record);
                return null;
            }
            var previousContext = SecurityContextHolder.getContext();
            var handoffContext = SecurityContextHolder.createEmptyContext();
            handoffContext.setAuthentication(task.authentication);
            SecurityContextHolder.setContext(handoffContext);
            AdmittedSkillInvocation admitted;
            try {
                admitted = Objects.requireNonNull(
                        skillInvocationHandoff.handoff(task.record.skillName, task.input),
                        "skill invocation handoff must not return null");
            } finally {
                SecurityContextHolder.setContext(previousContext);
            }
            task.record.snapshot = new ExecutionSnapshot(task.record.id, task.record.skillName,
                    ExecutionStatus.RUNNING, task.record.createdAt, null, null, null, null);
            task.clear();
            return admitted;
        } finally {
            gate.unlock();
        }
    }

    void complete(ExecutionTask task, String result, Throwable failure, List<SkillExecutionEvent> events) {
        gate.lock();
        try {
            if (records.get(task.record.id) != task.record) return;
            boolean failed = failure != null;
            List<SkillExecutionEvent> selected = switch (properties.getDiagnostics()) {
                case NEVER -> null;
                case ONERROR -> failed ? List.copyOf(events) : null;
                case ALWAYS -> List.copyOf(events);
            };
            task.record.snapshot = new ExecutionSnapshot(task.record.id, task.record.skillName,
                    failed ? ExecutionStatus.FAILED : ExecutionStatus.COMPLETED,
                    task.record.createdAt, clock.instant(), failed ? null : result,
                    failed ? ExecutionFailureClassifier.classify(failure) : null, selected);
        } finally {
            gate.unlock();
        }
    }

    void releaseQueueReservationLocked(ExecutionTask task) {
        if (task.reserved) {
            queuedCount--;
            queuedBytes -= task.inputBytes;
            task.reserved = false;
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (event.getApplicationContext() != owningContext) return;
        gate.lock();
        try {
            open = false;
            AvailabilityChangeEvent.publish(owningContext, ReadinessState.REFUSING_TRAFFIC);
            List<Runnable> waiting = new ArrayList<>();
            executor.getQueue().drainTo(waiting);
            for (Runnable runnable : waiting) {
                var task = (ExecutionTask) runnable;
                records.remove(task.record.id, task.record);
                task.discardLocked();
            }
        } finally {
            gate.unlock();
        }
    }

    @Override
    public boolean supportsAsyncExecution() { return false; }

    @PreDestroy
    void destroy() {
        expirationSweeper.shutdownNow();
        for (Runnable runnable : executor.shutdownNow()) {
            var task = (ExecutionTask) runnable;
            gate.lock();
            try {
                records.remove(task.record.id, task.record);
                task.discardLocked();
            } finally {
                gate.unlock();
            }
        }
    }

    int retainedCount() { return records.size(); }
    int queuedCount() { gate.lock(); try { return queuedCount; } finally { gate.unlock(); } }
    long queuedBytes() { gate.lock(); try { return queuedBytes; } finally { gate.unlock(); } }

    private final class AdmissionQueue extends LinkedTransferQueue<Runnable> {
        @Override
        public boolean offer(Runnable runnable) {
            if (tryTransfer(runnable)) return true;
            var task = (ExecutionTask) runnable;
            gate.lock();
            try {
                if (!open || queuedCount >= properties.getMaxQueued()
                        || task.inputBytes > properties.getMaxQueuedInputSize().toBytes() - queuedBytes) {
                    return false;
                }
                task.reserved = true;
                queuedCount++;
                queuedBytes += task.inputBytes;
                return super.offer(runnable);
            } finally {
                gate.unlock();
            }
        }

        @Override
        public Runnable take() throws InterruptedException {
            Runnable runnable = super.take();
            release((ExecutionTask) runnable);
            return runnable;
        }

        @Override
        public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            Runnable runnable = super.poll(timeout, unit);
            if (runnable != null) release((ExecutionTask) runnable);
            return runnable;
        }

        @Override
        public Runnable poll() {
            Runnable runnable = super.poll();
            if (runnable != null) release((ExecutionTask) runnable);
            return runnable;
        }

        private void release(ExecutionTask task) {
            gate.lock();
            try {
                task.releaseQueueReservationLocked();
            } finally {
                gate.unlock();
            }
        }
    }

    static final class ExecutionTask implements Runnable {
        private final ExecutionCoordinator coordinator;
        private final ExecutionRecord record;
        private final long inputBytes;
        private volatile Map<String, Object> input;
        private volatile JwtAuthenticationToken authentication;
        private boolean reserved;

        ExecutionTask(ExecutionCoordinator coordinator, ExecutionRecord record, Map<String, Object> input,
                long inputBytes, JwtAuthenticationToken authentication) {
            this.coordinator = coordinator;
            this.record = record;
            this.input = input;
            this.inputBytes = inputBytes;
            this.authentication = authentication;
        }

        @Override
        public void run() {
            coordinator.beforeDispatchHandoff.accept(this);
            var views = new ArrayList<SkillExecutionView>(1);
            AdmittedSkillInvocation admitted = null;
            try {
                admitted = coordinator.handoff(this);
                if (admitted == null) { clear(); return; }
                String result = admitted.invoke(views::add);
                coordinator.complete(this, result, null, events(views));
            } catch (RuntimeException failure) {
                coordinator.complete(this, null, failure, events(views));
            } finally {
                if (admitted != null) admitted.release();
                clear();
            }
        }

        private List<SkillExecutionEvent> events(List<SkillExecutionView> views) {
            return views.stream().flatMap(view -> view.events().stream()).toList();
        }

        void releaseQueueReservationLocked() { coordinator.releaseQueueReservationLocked(this); }
        void discardLocked() { releaseQueueReservationLocked(); clear(); }
        boolean referencesCleared() { return input == null && authentication == null; }
        private void clear() { input = null; authentication = null; }
    }
}
