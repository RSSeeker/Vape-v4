import gg.vape.Vape;
import gg.vape.ui.unmap.SearchBlock;
import gg.vape.utils.datas.SearchResultData;
import gg.vape.utils.processors.SearchProcessor;
import gg.vape.utils.processors.SearchResultIndex;
import gg.vape.utils.processors.SearchScanCursor;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.WorldClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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

    private static boolean hasPosition(List<SearchResultData> results, int x, int y, int z) {
        for (SearchResultData result : results) if (result.F==x && result.O==y && result.D==z) return true;
        return false;
    }

    public static void main(String[] args) throws Exception {
        for (int r = 0; r <= 5; ++r) {
            SearchScanCursor cursor = new SearchScanCursor(-30, -12, 40, r);
            Set<String> seen = new HashSet<String>();
            int firstX = 0, firstY = 0, firstZ = 0;
            while (cursor.next()) {
                if (seen.isEmpty()) { firstX=cursor.x(); firstY=cursor.y(); firstZ=cursor.z(); }
                check(Math.abs(cursor.x()+30) <= r && Math.abs(cursor.y()+12) <= r
                        && Math.abs(cursor.z()-40) <= r, "cursor stays in its cube");
                check(seen.add(cursor.x()+","+cursor.y()+","+cursor.z()), "cursor never repeats a position");
            }
            check(firstX==-30 && firstY==-12 && firstZ==40, "cursor begins at the player");
            check(seen.size() == (2*r+1)*(2*r+1)*(2*r+1), "cursor covers the whole cube");
        }
        SearchScanCursor skipped = new SearchScanCursor(0,0,0,2);
        check(skipped.next() && skipped.y()==0, "first column starts at center height");
        skipped.skipColumn();
        check(skipped.next() && (skipped.x()!=0 || skipped.z()!=0), "unloaded column can be skipped at once");

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
        check(results.size() == 1, "restored block is rediscovered even with a null description key");
        Minecraft.world.unload(-1, -1);
        scan(first);
        check(results.isEmpty(), "unloaded negative-coordinate chunk is pruned without a block read");
        Minecraft.world.load(-1, -1);
        scan(first);
        check(results.size() == 1, "reloaded chunks are scanned again");
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

        Minecraft.world = new WorldClient();
        Minecraft.player.x = 15; Minecraft.player.y = 0; Minecraft.player.z = 0;
        Minecraft.world.put(15, 0, 0, 42);
        Minecraft.world.put(16, 0, 0, 7); // simulated cave air, not the legacy numeric air id
        Minecraft.world.unload(1, 0);
        targets.add(ore);
        first.X(true);
        scan(first);
        check(results.isEmpty(), "an unloaded adjacent chunk is not treated as air");
        Minecraft.world.load(1, 0);
        scan(first);
        check(results.size()==1, "modern cave air is recognized after its chunk loads");
        first.X(false);
        Minecraft.world = new WorldClient();
        Minecraft.world.rejectOutOfHeight = true;
        Minecraft.player.x=0; Minecraft.player.y=256; Minecraft.player.z=0;
        Minecraft.world.put(0,255,0,42);
        scan(first);
        check(results.size()==1 && results.get(0).O==255,
                "legacy chunk probe stays inside build height when the player is above it");

        Minecraft.world = new WorldClient();
        Minecraft.player.x=0; Minecraft.player.y=0; Minecraft.player.z=0;
        Minecraft.world.put(0, 0, 0, 99);
        Minecraft.world.put(2, 0, 0, 42);
        Minecraft.world.put(20, 0, 20, 42);
        NumberValue wide = new NumberValue(20);
        SearchProcessor progressive = new SearchProcessor(results, targets, wide, wide, index);
        int before = Minecraft.world.reads.get();
        scan(progressive);
        check(Minecraft.world.reads.get()-before <= 8192, "full scan respects its per-slice block-read budget");
        check(results.size()==1 && results.get(0).F==2, "nearby blocks are found before the far edge");
        SearchResultData retained = results.get(0);
        SearchBlock secondTarget = new SearchBlock(99);
        targets.add(secondTarget);
        scan(progressive);
        check(hasPosition(results,0,0,0) && results.contains(retained),
                "adding a target restarts the cursor without clearing existing results");
        secondTarget.enabled.set(false);
        scan(progressive);
        check(!hasPosition(results,0,0,0), "disabling a target removes its results during a sliced scan");
        secondTarget.enabled.set(true);
        scan(progressive);
        check(hasPosition(results,0,0,0), "re-enabling a target restarts a sliced scan near the player");
        for (int i=0; i<20 && !hasPosition(results,20,0,20); ++i) scan(progressive);
        check(hasPosition(results,20,0,20), "resumed slices eventually cover the far edge");
        Minecraft.player.x=50; Minecraft.world.put(50,0,0,42);
        scan(progressive);
        check(hasPosition(results,50,0,0) && !hasPosition(results,2,0,0),
                "moving far away recenters the cursor and prunes old positions");
        targets.clear();
        scan(progressive);
        check(results.isEmpty(), "empty target list prunes results between slices");

        Minecraft.world=new WorldClient(); Minecraft.player.x=0; Minecraft.player.y=0; Minecraft.player.z=0;
        Minecraft.world.put(0,0,0,42); Minecraft.world.put(0,1,0,8);
        targets.add(ore); first.X(true); scan(first);
        check(results.isEmpty(), "modern Only caves does not mistake a liquid id for air");
        Minecraft.world.put(0,1,0,7); scan(first);
        check(hasPosition(results,0,0,0), "modern cave air qualifies regardless of legacy block id");
        Minecraft.world.put(0,1,0,30); scan(first);
        check(results.isEmpty(), "modern Only caves does not mistake a cobweb id for air");
        first.X(false);

        final WorldClient changingFilter=new WorldClient();
        changingFilter.put(0,0,0,42); Minecraft.world=changingFilter;
        changingFilter.beforeRead = () -> first.X(true);
        scan(first);
        check(results.isEmpty(), "filter changes during a read reject results from the old generation");
        scan(first);
        check(results.isEmpty(), "the new cave filter applies after the interrupted slice");
        first.X(false);
        final WorldClient oldDuringRead=new WorldClient(); oldDuringRead.put(0,0,0,42);
        final WorldClient newDuringRead=new WorldClient(); newDuringRead.put(1,0,0,42);
        Minecraft.world=oldDuringRead;
        oldDuringRead.beforeRead = () -> Minecraft.world=newDuringRead;
        scan(first);
        check(results.isEmpty(), "a world switch during a read rejects old-world publication");
        scan(first);
        check(hasPosition(results,1,0,0) && !hasPosition(results,0,0,0), "the next slice scans the new world");
        targets.clear();

        // Stop a real worker while it is inside a world read. Its interrupted
        // or delayed scan must not publish after w() returns.
        targets.add(ore);
        Minecraft.world = oldWorld;
        Minecraft.player.x=-0.1; Minecraft.player.y=-0.1; Minecraft.player.z=-0.1;
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

        final WorldClient racingChunk=new WorldClient();
        racingChunk.put(0,0,0,42); Minecraft.world=racingChunk;
        Minecraft.player.x=0; Minecraft.player.y=0; Minecraft.player.z=0;
        index.clear();
        final CountDownLatch unloadedDuringRead=new CountDownLatch(1);
        int previouslyLogged=Vape.logged.get();
        racingChunk.beforeRead=() -> { racingChunk.unload(0,0); unloadedDuringRead.countDown(); };
        SearchProcessor recovering = new SearchProcessor(results, targets, radius, radius, index);
        recovering.n();
        check(unloadedDuringRead.await(3,TimeUnit.SECONDS), "chunk unloads after a cached loaded check");
        for (int i=0; i<300 && Vape.logged.get()==previouslyLogged; ++i) Thread.sleep(10L);
        check(Vape.logged.get()>previouslyLogged && recovering.p(), "a transient block-read failure does not kill the worker");
        racingChunk.load(0,0);
        for (int i=0; i<300 && !hasPosition(results,0,0,0); ++i) Thread.sleep(10L);
        check(hasPosition(results,0,0,0), "a later slice finds blocks after the chunk reloads");
        recovering.w();
        Thread recoveredWorker=(Thread)field(SearchProcessor.class,"worker").get(recovering);
        recoveredWorker.join(3000L);
        check(!recoveredWorker.isAlive(), "recovered worker stops cleanly");
        System.out.println("Search processor: " + checks + " headless integration assertions passed");
    }
}
