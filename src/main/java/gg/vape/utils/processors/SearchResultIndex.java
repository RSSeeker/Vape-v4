package gg.vape.utils.processors;

import gg.vape.ui.unmap.SearchBlock;
import gg.vape.utils.datas.SearchResultData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Shared by the near and full-range scanners; published entries are never recycled. */
public final class SearchResultIndex {
    private final List<SearchResultData> results;
    private final Map<Position, Entry> entries = new HashMap<Position, Entry>();
    private final Position lookup = new Position(0, 0, 0);
    private Object world;
    private long generation;
    private boolean onlyCaves;

    public SearchResultIndex(List<SearchResultData> results) {
        this.results = results;
    }

    public synchronized long enterWorld(Object world) {
        if (this.world != world) {
            this.world = world;
            this.clear();
        }
        return this.generation;
    }

    public synchronized long generation() {
        return this.generation;
    }

    public synchronized boolean onlyCaves() {
        return this.onlyCaves;
    }

    public synchronized void setOnlyCaves(boolean value) {
        if (this.onlyCaves != value) {
            this.onlyCaves = value;
            this.clear();
        }
    }

    public synchronized void clear() {
        ++this.generation;
        this.entries.clear();
        this.results.clear();
    }

    public synchronized void update(long generation, Object world, int x, int y, int z,
                                    int blockId, SearchBlock match) {
        if (generation != this.generation || world != this.world) return;
        this.lookup.set(x, y, z);
        Entry previous = this.entries.get(this.lookup);
        if (match == null) {
            if (previous != null) {
                this.entries.remove(this.lookup);
                this.results.remove(previous.result);
            }
            return;
        }
        if (previous != null && previous.block == match && previous.result.N == blockId) return;
        if (previous != null) this.results.remove(previous.result);
        SearchResultData result = new SearchResultData(x, y, z, blockId, match,
                match.I$src$Ljava_util_concurrent_atomic_AtomicBoolean_$10pq3bz(), 0);
        this.entries.put(new Position(x, y, z), new Entry(match, result));
        this.results.add(result);
    }

    public synchronized void prune(long generation, Object world, int x, int y, int z,
                                   int radius, List<SearchBlock> targets) {
        if (generation != this.generation || world != this.world) return;
        List<SearchResultData> removed = new ArrayList<SearchResultData>();
        Iterator<Map.Entry<Position, Entry>> iterator = this.entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Position, Entry> item = iterator.next();
            Position position = item.getKey();
            Entry entry = item.getValue();
            if (Math.abs((long)position.x - x) > radius || Math.abs((long)position.y - y) > radius
                    || Math.abs((long)position.z - z) > radius || !entry.block.T()
                    || !targets.contains(entry.block)) {
                removed.add(entry.result);
                iterator.remove();
            }
        }
        if (!removed.isEmpty()) this.results.removeAll(removed);
    }

    private static final class Entry {
        private final SearchBlock block;
        private final SearchResultData result;
        private Entry(SearchBlock block, SearchResultData result) {
            this.block = block;
            this.result = result;
        }
    }

    private static final class Position {
        private int x;
        private int y;
        private int z;
        private Position(int x, int y, int z) { this.set(x, y, z); }
        private void set(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public int hashCode() { return (this.x * 31 + this.y) * 31 + this.z; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Position)) return false;
            Position p = (Position)other;
            return this.x == p.x && this.y == p.y && this.z == p.z;
        }
    }
}
