package gg.vape.utils.processors;

import gg.vape.Vape;
import gg.vape.ui.unmap.SearchBlock;
import gg.vape.utils.BlockUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.datas.SearchResultData;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.BlockPos;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.WorldClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

public class SearchProcessor
implements Runnable {
    private volatile boolean P;
    private final List<SearchResultData> c;
    private final List<Integer> y;
    private final List<SearchBlock> H;
    private final int[][] X = new int[][]{{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private volatile boolean o;
    private final NumberValue j;
    private final NumberValue maximumRange;
    private final SearchResultIndex index;
    private volatile Thread worker;
    private volatile long workerGeneration;
    // A bounded slice keeps the full-range scan from monopolizing a worker.
    private static final int BLOCKS_PER_SLICE = 8192;
    private static final int MAX_SUPPORTED_RANGE = 100;
    // Cursor ownership is guarded by index; a restarted worker gets its own cursor.
    private SearchScanCursor cursor;
    private long cursorWorkerGeneration = -1;
    private long cursorIndexGeneration = -1;
    private Object cursorWorld;
    private SearchBlock[] cursorTargets;
    private boolean[] cursorTargetEnabled;

    private boolean sameCursorTargets(SearchBlock[] targets) {
        if (this.cursorTargets == null || this.cursorTargets.length != targets.length) return false;
        for (int i = 0; i < targets.length; ++i) {
            if (this.cursorTargets[i] != targets[i] || this.cursorTargetEnabled[i] != targets[i].T()) return false;
        }
        return true;
    }

    private void rememberCursorTargets(SearchBlock[] targets) {
        this.cursorTargets = targets;
        this.cursorTargetEnabled = new boolean[targets.length];
        for (int i = 0; i < targets.length; ++i) this.cursorTargetEnabled[i] = targets[i].T();
    }

    private boolean isRunning(long generation) {
        return this.o && this.workerGeneration == generation;
    }

    public boolean h(int n, int n2, int n3) {
        WorldClient worldClient = Minecraft.theWorld();
        for (int[] nArray : this.X) {
            Block block = worldClient.getBlockByPos(n + nArray[0], n2 + nArray[1], n3 + nArray[2]);
            int n4 = Block.R(block);
            if (!this.y.contains(n4)) continue;
            return true;
        }
        return false;
    }

    public SearchResultData X(int n, int n2, int n3) {
        for (SearchResultData searchResultData : this.c) {
            if (searchResultData.F != n || searchResultData.O != n2 || searchResultData.D != n3) continue;
            return searchResultData;
        }
        return null;
    }

    public synchronized void w() {
        if (this.index == null) {
            this.o = false;
            ++this.workerGeneration;
        } else {
            // Serialize stop with publication. A generation also prevents an
            // old blocked worker from reviving after a rapid stop/start.
            synchronized (this.index) {
                this.o = false;
                ++this.workerGeneration;
            }
        }
        Thread running = this.worker;
        if (running != null) running.interrupt();
    }

    public synchronized void n() {
        if (this.o) return;
        this.o = true;
        final long generation = ++this.workerGeneration;
        this.worker = new Thread(() -> this.runForGeneration(generation), "InevitableClient-Search");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public NumberValue F() {
        return this.j;
    }

    public SearchBlock S(int n, int n2) {
        for (SearchBlock searchBlock : this.H) {
            if (!searchBlock.b(n, n2)) continue;
            return searchBlock;
        }
        return null;
    }

    public SearchProcessor(List<SearchResultData> list, List<SearchBlock> list2, NumberValue numberValue) {
        this(list, list2, numberValue, numberValue, null);
    }

    public SearchProcessor(List<SearchResultData> list, List<SearchBlock> list2, NumberValue scanRange,
                           NumberValue maximumRange, SearchResultIndex index) {
        this.y = Arrays.asList(0, 8, 9, 30);
        this.c = list;
        this.H = list2;
        this.j = scanRange;
        this.maximumRange = maximumRange;
        this.index = index;
    }

    public void clearResults() {
        if (this.index == null) this.c.clear();
        else this.index.clear();
    }

    public List<SearchResultData> b() {
        return this.c;
    }

    public boolean p() {
        return this.o;
    }

    public void X(boolean bl) {
        this.P = bl;
        if (this.index != null) this.index.setOnlyCaves(bl);
    }

    public boolean u() {
        return this.P;
    }

    private static Exception a(Exception exception) {
        return exception;
    }

    private boolean exposedToCave(WorldClient world, int x, int y, int z,
                                  BiPredicate<Integer, Integer> chunkLoaded) {
        for (int[] offset : this.X) {
            int nx = x + offset[0], ny = y + offset[1], nz = z + offset[2];
            // An unloaded neighbor is unknown, not evidence of air.
            if (!chunkLoaded.test(nx >> 4, nz >> 4)) continue;
            Block neighbor = world.getBlockByPos(nx, ny, nz);
            if (neighbor != null && neighbor.isNotNull() && BlockUtil.p(neighbor)) return true;
        }
        return false;
    }

    private void runIndexedScan(long workerGeneration) {
        long nextFailureLog = 0L;
        try {
            while (this.isRunning(workerGeneration)) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ignored) {
                    if (!this.isRunning(workerGeneration)) break;
                }
                if (!this.isRunning(workerGeneration)) break;
                try {
                    this.scanIndexedPass(workerGeneration);
                } catch (Exception exception) {
                    // A chunk can unload between the slice's check and a
                    // block read. Retry the next slice instead of silently
                    // terminating the worker; rate-limit persistent failures.
                    long now = System.currentTimeMillis();
                    if (now >= nextFailureLog) {
                        Vape.logThrowable(exception);
                        nextFailureLog = now + 5000L;
                    }
                }
            }
        } finally {
            synchronized (this) {
                if (this.workerGeneration == workerGeneration) this.o = false;
            }
        }
    }

    // One processor scans nearby blocks frequently and another scans the full
    // configured range. Both update the same coordinate index atomically.
    private void scanIndexedPass() {
        this.scanIndexedPass(this.workerGeneration);
    }

    private void scanIndexedPass(long workerGeneration) {
        WorldClient world = Minecraft.theWorld();
        EntityPlayerSP player = Minecraft.thePlayer();
        if (world.isNull() || player.isNull()) {
            synchronized (this.index) {
                if (this.isRunning(workerGeneration)) this.index.enterWorld(null);
            }
            return;
        }
        Object worldObject = world.getObject();
        Object playerObject = player.getObject();
        if (Minecraft.theWorld().getObject() != worldObject) return;
        int centerX = (int)Math.floor(player.z());
        int centerY = (int)Math.floor(player.N());
        int centerZ = (int)Math.floor(player.h());
        int maximum = Math.max(0, Math.min(MAX_SUPPORTED_RANGE, this.maximumRange.getValue().intValue()));
        int radius = Math.max(0, Math.min(maximum, this.j.getValue().intValue()));
        // Cache only for this slice. The next slice rechecks chunk loads and
        // immediately prunes entries from chunks that have since unloaded.
        Map<Long, Boolean> loadedChunks = new HashMap<Long, Boolean>();
        // 1.16.5's isBlockLoaded also checks build height; newer hasChunk
        // ignores Y. Use a valid legacy height even if the player is above it.
        int chunkProbeY = Math.max(0, Math.min(255, centerY));
        BiPredicate<Integer, Integer> chunkLoaded = (chunkX, chunkZ) -> {
            long key = (long)chunkX << 32 ^ (chunkZ & 0xffffffffL);
            Boolean loaded = loadedChunks.get(key);
            if (loaded == null) {
                loaded = world.j$src$Z$11aji0a(BlockPos.create(chunkX << 4, chunkProbeY, chunkZ << 4));
                loadedChunks.put(key, loaded);
            }
            return loaded;
        };
        SearchBlock[] targets = this.H.toArray(new SearchBlock[0]);
        long generation;
        boolean caves;
        SearchScanCursor scan;
        synchronized (this.index) {
            if (!this.isRunning(workerGeneration) || Minecraft.theWorld().getObject() != worldObject
                    || Minecraft.thePlayer().getObject() != playerObject) return;
            generation = this.index.enterWorld(worldObject);
            caves = this.index.onlyCaves();
            this.index.prune(generation, worldObject, centerX, centerY, centerZ, maximum, this.H, chunkLoaded);
            if (this.cursor == null || this.cursorWorkerGeneration != workerGeneration
                    || this.cursorIndexGeneration != generation || this.cursorWorld != worldObject
                    || this.cursor.radius() != radius || !this.sameCursorTargets(targets)
                    || Math.abs((long)this.cursor.centerX() - centerX) > Math.max(16, radius * 2)
                    || Math.abs((long)this.cursor.centerY() - centerY) > Math.max(16, radius * 2)
                    || Math.abs((long)this.cursor.centerZ() - centerZ) > Math.max(16, radius * 2)) {
                this.cursor = new SearchScanCursor(centerX, centerY, centerZ, radius);
                this.cursorWorkerGeneration = workerGeneration;
                this.cursorIndexGeneration = generation;
                this.cursorWorld = worldObject;
                this.rememberCursorTargets(targets);
            }
            scan = this.cursor;
        }
        if (targets.length == 0) return;
        for (int remaining = BLOCKS_PER_SLICE; remaining > 0; --remaining) {
            if (!this.isRunning(workerGeneration) || this.index.generation() != generation
                    || Minecraft.theWorld().getObject() != worldObject
                    || Minecraft.thePlayer().getObject() != playerObject) return;
            if (!scan.next()) {
                synchronized (this.index) {
                    if (this.cursor == scan) this.cursor = null;
                }
                return;
            }
            int x = scan.x(), y = scan.y(), z = scan.z();
            if (!chunkLoaded.test(x >> 4, z >> 4)) {
                scan.skipColumn();
                continue;
            }
            int liveX = (int)Math.floor(player.z());
            int liveY = (int)Math.floor(player.N());
            int liveZ = (int)Math.floor(player.h());
            int liveMaximum = Math.max(0, Math.min(MAX_SUPPORTED_RANGE, this.maximumRange.getValue().intValue()));
            if (Math.abs((long)x - liveX) > liveMaximum || Math.abs((long)y - liveY) > liveMaximum
                    || Math.abs((long)z - liveZ) > liveMaximum) continue;
            Block block = world.getBlockByPos(x, y, z);
            int id = 0;
            SearchBlock match = null;
            // Description keys can be lazily initialized; they are not needed
            // to compare a block's registry id.
            if (block != null && block.isNotNull()) {
                id = Block.R(block);
                int metadata = ForgeVersion.MC_1_16_5.d() ? -1 : block.d(x, y, z);
                for (SearchBlock target : targets) {
                    if (target.b(id, metadata)) { match = target; break; }
                }
                if (match != null && caves && !this.exposedToCave(world, x, y, z, chunkLoaded)) match = null;
            }
            synchronized (this.index) {
                if (!this.isRunning(workerGeneration)
                        || Minecraft.theWorld().getObject() != worldObject
                        || Minecraft.thePlayer().getObject() != playerObject) return;
                this.index.update(generation, worldObject, x, y, z, id, match);
            }
        }
    }

    @Override
    public void run() {
        this.runForGeneration(this.workerGeneration);
    }

    private void runForGeneration(long workerGeneration) {
        if (this.index != null) {
            this.runIndexedScan(workerGeneration);
            return;
        }
        block11: {
            try {
                block4: while (this.isRunning(workerGeneration)) {
                    try {
                        Thread.sleep(50L);
                    }
                    catch (InterruptedException interruptedException) {
                        // empty catch block
                    }
                    if (Minecraft.theWorld().isNull() || Minecraft.thePlayer().isNull()) continue;
                    EntityPlayerSP entityPlayerSP = Minecraft.thePlayer();
                    WorldClient worldClient = Minecraft.theWorld();
                    int n = ((Double)this.j.getValue()).intValue();
                    this.h(0, 0, 0);
                    for (int i = -n; i < n; ++i) {
                        for (int j = -n; j < n; ++j) {
                            for (int k = -n; k < n; ++k) {
                                if (this.isRunning(workerGeneration)) {
                                    boolean bl;
                                    int n2;
                                    int n3;
                                    if (Minecraft.theWorld().isNull() || !Minecraft.theWorld().equals(worldClient) || Minecraft.thePlayer().isNull() || !Minecraft.thePlayer().equals(entityPlayerSP)) {
                                        synchronized (this) {
                                            if (!this.isRunning(workerGeneration)) break block11;
                                            this.c.clear();
                                        }
                                        continue block4;
                                    }
                                    int n4 = (int)Math.floor(entityPlayerSP.z()) + i;
                                    Block block = worldClient.getBlockByPos(n4, n3 = (int)Math.floor(entityPlayerSP.N()) + k, n2 = (int)Math.floor(entityPlayerSP.h()) + j);
                                    if (block.U() == null) continue;
                                    int n5 = Block.R(block);
                                    int n6 = ForgeVersion.MC_1_16_5.d() ? -1 : block.d(n4, n3, n2);
                                    SearchResultData searchResultData = this.X(n4, n3, n2);
                                    boolean bl2 = bl = searchResultData != null;
                                    if (!bl) {
                                        SearchBlock searchBlock = this.S(n5, n6);
                                        if (searchBlock == null || this.P && !this.h(n4, n3, n2)) continue;
                                        synchronized (this) {
                                            if (!this.isRunning(workerGeneration)) break block11;
                                            this.c.add(new SearchResultData(n4, n3, n2, n5, searchBlock, searchBlock.I$src$Ljava_util_concurrent_atomic_AtomicBoolean_$10pq3bz(), 0));
                                        }
                                        continue;
                                    }
                                    if (n5 == searchResultData.N && !(RotationUtil.p(entityPlayerSP, n4, n3, n2) > 200.0)) continue;
                                    synchronized (this) {
                                        if (!this.isRunning(workerGeneration)) break block11;
                                        this.c.remove(searchResultData);
                                    }
                                    continue;
                                }
                                break block11;
                            }
                        }
                    }
                }
            }
            catch (Exception exception) {
                Vape.logThrowable(exception);
            } finally {
                synchronized (this) {
                    if (this.workerGeneration == workerGeneration) this.o = false;
                }
            }
        }
    }
}

