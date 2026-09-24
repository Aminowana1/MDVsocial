package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TickWorkQueueTest {
    @Test void boundedFairWorkAndRequiredJobsSurviveShutdown() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("performance.work-items-per-tick", 2);
        config.set("performance.work-budget-ms", 10);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("queue"));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskTimer(eq(plugin), tick.capture(), eq(1L), eq(1L))).thenReturn(task);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            TickWorkQueue queue = new TickWorkQueue(plugin);
            List<Integer> result = new ArrayList<>();
            AtomicInteger done = new AtomicInteger();
            assertTrue(queue.submit("a", List.of(1, 3, 5).iterator(), result::add, done::incrementAndGet, true));
            assertTrue(queue.submit("b", List.of(2, 4, 6).iterator(), result::add, done::incrementAndGet, true));
            assertFalse(queue.submit("a", List.of(999).iterator(), result::add, () -> {}, true));
            tick.getValue().run();
            assertTrue(result.size() <= 2);
            if (result.size() == 2) assertEquals(List.of(1, 2), result);
            // Completion can enqueue another required job (campaign scan -> deletion).
            queue.submit("chain", List.of(7).iterator(), result::add, () ->
                    queue.submit("child", List.of(8).iterator(), result::add, () -> {}, true), true);
            queue.close();
            assertEquals(2, done.get());
            assertEquals(Set.of(1,2,3,4,5,6,7,8), new HashSet<>(result));
            assertEquals(8, result.size());
            verify(task).cancel();
        }
    }
}
