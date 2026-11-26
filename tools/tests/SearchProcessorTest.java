import gg.vape.ui.unmap.SearchBlock;
import gg.vape.utils.datas.SearchResultData;
import gg.vape.utils.processors.SearchProcessor;
import gg.vape.utils.processors.SearchResultIndex;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.WorldClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** Real SearchProcessor and shared index against a small headless block world. */
public final class SearchProcessorTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
    private static void scan(SearchProcessor processor) throws Exception {
        field(SearchProcessor.class, "o").setBoolean(processor, true);
        Method scan = SearchProcessor.class.getDeclaredMethod("scanIndexedPass");
        scan.setAccessible(true);
        scan.invoke(processor);
    }

    public static void main(String[] args) throws Exception {
        Minecraft.world = new WorldClient();
        Minecraft.player.x = -0.1;
        Minecraft.player.y = -0.1;
        Minecraft.player.z = -0.1;
        List<SearchResultData> results = new CopyOnWriteArrayList<SearchResultData>();
        List<SearchBlock> targets = new CopyOnWriteArrayList<SearchBlock>();
        SearchBlock ore = new SearchBlock(42);
        targets.add(ore);
        NumberValue radius = new NumberValue(1);
        SearchResultIndex index = new SearchResultIndex(results);
        SearchProcessor first = new SearchProcessor(results, targets, radius, radius, index);
        SearchProcessor second = new SearchProcessor(results, targets, radius, radius, index);
        Minecraft.world.put(-2, -1, -1, 42);
        scan(first);
        check(results.size() == 1 && results.get(0).F == -2 && results.get(0).O == -1,
                "negative fractional player positions use floor, including negative Y");
        SearchResultData visible = results.get(0);
        scan(second);
        check(results.size() == 1 && results.get(0) == visible, "overlapping scanners do not duplicate results");

        first.X(true);
        check(results.isEmpty(), "cave setting change invalidates existing results");
        scan(first);
        check(results.isEmpty(), "covered ore is excluded by Only caves");
        Minecraft.world.put(-2, 0, -1, 0);
        scan(second);
        check(results.size() == 1, "exposed ore appears after neighbor changes");
        Minecraft.world.put(-2, 0, -1, 1);
        scan(second);
        check(results.isEmpty(), "ore is removed when its cave exposure disappears");
        first.X(false);
        scan(first);
        check(results.size() == 1, "turning cave filter off rescans existing ore");
        Minecraft.world.put(-2, -1, -1, 1);
        scan(first);
        check(results.isEmpty(), "changed block is removed");
        Minecraft.world.put(-2, -1, -1, 42);
        scan(first);
        check(results.size() == 1, "restored block is rediscovered");
        check(visible.F == -2 && visible.O == -1 && visible.N == 42,
                "previously published result was not recycled or mutated");

        SearchBlock replacement = new SearchBlock(42);
        targets.clear();
        targets.add(replacement);
        scan(first);
        check(results.size() == 1 && results.get(0) != visible, "updated block selection replaces the old entry");
        replacement.enabled.set(false);
        scan(first);
        check(results.isEmpty(), "disabled block selection removes stale entries");
        replacement.enabled.set(true);
        scan(first);
        check(results.size() == 1, "re-enabled block selection is rediscovered");
        Minecraft.player.x = 20.0;
        scan(second);
        check(results.isEmpty(), "out-of-range results are pruned by the near scanner");
        Minecraft.player.x = -0.1;
        scan(first);
        check(results.size() == 1, "returning to range finds the block again");
        WorldClient oldWorld = Minecraft.world;
        Minecraft.world = new WorldClient();
        scan(second);
        check(results.isEmpty(), "world change removes old-world results");
        long generation = index.enterWorld(Minecraft.world.getObject());
        first.X(true);
        index.update(generation, Minecraft.world.getObject(), 0, 0, 0, 42, replacement);
        check(results.isEmpty(), "an in-flight update from a previous generation is rejected");
        first.X(false);
        targets.clear();
        scan(first);
        check(results.isEmpty(), "removing all targets leaves no results");

        // Stop a real worker while it is inside a world read. Its interrupted
        // or delayed scan must not publish after w() returns.
        targets.add(ore);
        Minecraft.world = oldWorld;
        Minecraft.world.gateReads();
        SearchProcessor stopped = new SearchProcessor(results, targets, radius, radius, index);
        index.clear();
        stopped.n();
        check(Minecraft.world.entered.await(3, TimeUnit.SECONDS), "worker entered a block read");
        stopped.w();
        Minecraft.world.release.countDown();
        Thread worker = (Thread)field(SearchProcessor.class, "worker").get(stopped);
        worker.join(3000L);
        check(!worker.isAlive(), "stopped worker exits");
        check(results.isEmpty(), "stopped worker does not publish a late result");

        Minecraft.world.gateReads();
        SearchProcessor restarted = new SearchProcessor(results, targets, radius, radius, index);
        restarted.n();
        check(Minecraft.world.entered.await(3, TimeUnit.SECONDS), "old worker entered blocked world read");
        Thread oldWorker = (Thread)field(SearchProcessor.class, "worker").get(restarted);
        restarted.w();
        restarted.n();
        Thread newWorker = (Thread)field(SearchProcessor.class, "worker").get(restarted);
        Minecraft.world.release.countDown();
        oldWorker.join(3000L);
        check(!oldWorker.isAlive(), "old generation exits after immediate restart");
        check(newWorker != oldWorker && restarted.p(), "old worker cannot stop its replacement");
        for (int i = 0; i < 300 && results.isEmpty(); ++i) Thread.sleep(10L);
        check(!results.isEmpty(), "replacement worker publishes fresh results");
        restarted.w();
        newWorker.join(3000L);
        check(!newWorker.isAlive(), "replacement worker stops cleanly");
        System.out.println("Search processor: " + checks + " headless integration assertions passed");
    }
}
