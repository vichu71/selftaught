package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.lock.GenerationBusyException;
import es.vicai.studylab.ai.lock.GenerationLock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** AC-11: una sola generación concurrente por usuario, con cerrojo real en BD. */
class GenerationLockTest extends AiTestBase {

    @Test
    void ac11_aSecondGenerationOfTheSameUserIsRejectedWhileTheFirstIsRunning() {
        UUID user = newUser();

        try (GenerationLock.Lease first = locks.acquire(user)) {
            assertThat(first).isNotNull();
            assertThatThrownBy(() -> locks.acquire(user)).isInstanceOf(GenerationBusyException.class);
        }
    }

    @Test
    void ac11_closingTheLeaseReleasesTheLock() {
        UUID user = newUser();

        locks.acquire(user).close();

        try (GenerationLock.Lease again = locks.acquire(user)) {
            assertThat(again).isNotNull();
        }
    }

    @Test
    void ac11_theLockIsReleasedEvenWhenTheGenerationFails() {
        UUID user = newUser();

        assertThatThrownBy(() -> {
            try (GenerationLock.Lease lease = locks.acquire(user)) {
                throw new IllegalStateException("la generación falla");
            }
        }).isInstanceOf(IllegalStateException.class);

        locks.acquire(user).close(); // no queda bloqueado
    }

    @Test
    void ac11_differentUsersDoNotBlockEachOther() {
        try (GenerationLock.Lease a = locks.acquire(newUser()); GenerationLock.Lease b = locks.acquire(newUser())) {
            assertThat(a).isNotNull();
            assertThat(b).isNotNull();
        }
    }

    @Test
    void ac11_aLockLeftByACrashedBackendExpiresOnItsOwn() {
        UUID user = newUser();
        locks.acquire(user); // nunca se cierra: simula una caída
        assertThatThrownBy(() -> locks.acquire(user)).isInstanceOf(GenerationBusyException.class);

        owner.update("UPDATE ai_generation_lock SET expires_at = now() - interval '1 second' WHERE user_id = ?", user);

        try (GenerationLock.Lease taken = locks.acquire(user)) {
            assertThat(taken).isNotNull();
        }
    }

    @Test
    void ac11_theLockExpiresTwentyMinutesAfterBeingTaken() {
        UUID user = newUser();
        locks.acquire(user);

        Double minutes = owner.queryForObject(
                "SELECT extract(epoch FROM (expires_at - acquired_at)) / 60 FROM ai_generation_lock WHERE user_id = ?",
                Double.class, user);

        assertThat(minutes).isBetween(19.9, 20.1);
    }

    @Test
    void ac11_anOldLeaseCannotReleaseALockThatNowBelongsToAnotherGeneration() {
        UUID user = newUser();
        GenerationLock.Lease old = locks.acquire(user);
        owner.update("UPDATE ai_generation_lock SET expires_at = now() - interval '1 second' WHERE user_id = ?", user);
        GenerationLock.Lease current = locks.acquire(user);

        old.close(); // la generación antigua termina tarde: no debe soltar el cerrojo de la nueva

        assertThatThrownBy(() -> locks.acquire(user)).isInstanceOf(GenerationBusyException.class);
        current.close();
    }

    @Test
    void ac11_whenManyRequestsRaceExactlyOneWins() throws Exception {
        UUID user = newUser();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> attempt = () -> {
                go.await();
                try {
                    locks.acquire(user); // se mantiene tomado a propósito
                    return true;
                } catch (GenerationBusyException busy) {
                    return false;
                }
            };
            results.add(pool.submit(attempt));
        }
        go.countDown();

        long winners = 0;
        for (Future<Boolean> r : results) if (r.get()) winners++;
        pool.shutdownNow();

        assertThat(winners).isEqualTo(1);
    }

    @Test
    void ac11_aLongGenerationCanRenewItsLockSoItNeverExpiresWhileAlive() {
        UUID user = newUser();
        GenerationLock.Lease lease = locks.acquire(user);
        owner.update("UPDATE ai_generation_lock SET expires_at = now() + interval '1 second' WHERE user_id = ?", user);

        assertThat(lease.renew()).isTrue();

        Double minutes = owner.queryForObject("SELECT extract(epoch FROM (expires_at - now())) / 60 FROM ai_generation_lock WHERE user_id = ?",
                Double.class, user);
        assertThat(minutes).isBetween(19.5, 20.1); // otra vez el TTL completo
        assertThatThrownBy(() -> locks.acquire(user)).isInstanceOf(GenerationBusyException.class);
        lease.close();
    }

    @Test
    void ac11_aLeaseThatLostItsLockCannotRenewItAndMustAbort() {
        UUID user = newUser();
        GenerationLock.Lease old = locks.acquire(user);
        owner.update("UPDATE ai_generation_lock SET expires_at = now() - interval '1 second' WHERE user_id = ?", user);
        GenerationLock.Lease current = locks.acquire(user); // otra generación tomó el cerrojo caducado

        assertThat(old.renew()).isFalse();
        assertThat(current.renew()).isTrue();
        current.close();
        assertThat(current.renew()).isFalse(); // ya liberado
    }

    @Test
    void ac18_aRejectedGenerationIsAudited() {
        UUID user = newUser();
        locks.acquire(user);

        assertThatThrownBy(() -> locks.acquire(user)).isInstanceOf(GenerationBusyException.class);

        assertThat(auditActions()).contains("AI_GENERATION_DENIED");
        assertThat(owner.queryForObject("SELECT outcome FROM audit_event WHERE action = 'AI_GENERATION_DENIED'", String.class))
                .isEqualTo("DENIED");
    }

    @Test
    void edge_aDeactivatedUserCannotTakeTheLock() {
        UUID user = newUser();
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", user);

        assertThatThrownBy(() -> locks.acquire(user)).isInstanceOf(AiException.class);
    }
}
