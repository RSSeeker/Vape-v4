package gg.vape.module.render.search;

import gg.vape.ui.unmap.SearchBlock;
import gg.vape.utils.MathUtil;
import gg.vape.wrapper.Wrapper;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.Chunk;
import gg.vape.wrapper.impl.ChunkSection;
import gg.vape.wrapper.impl.ClientChunkProvider;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.WorldClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

public class SearchBlockChunkScanner {
    public static final Queue<SearchBlockRenderEntry> entryPool = new ConcurrentLinkedQueue<SearchBlockRenderEntry>();

    public static SearchBlockRenderEntry obtain(int blockId, int metadata, int worldX, int worldY, int worldZ) {
        SearchBlockRenderEntry searchBlockRenderEntry = entryPool.poll();
        if (searchBlockRenderEntry == null) {
            searchBlockRenderEntry = new SearchBlockRenderEntry(blockId, metadata, worldX, worldY, worldZ);
        } else {
            searchBlockRenderEntry.reset(blockId, metadata, worldX, worldY, worldZ);
        }
        return searchBlockRenderEntry;
    }


    public static void recycle(SearchBlockRenderEntry searchBlockRenderEntry) {
        entryPool.offer(searchBlockRenderEntry);
    }

    private static long packPosition(int x, int y, int z) {
        return ((long)x & 0x1FFFFFL) << 43 | ((long)z & 0x1FFFFFL) << 22 | (long)y & 0xFFFL;
    }

    private static void scanSection(char[] blockStates, int chunkX, int sectionBaseY, int chunkZ, SearchBlock[] targets, ArrayList<SearchBlockRenderEntry> results, Set<Long> airPositions, boolean onlyCaves) {
        for (int i = 0; i < blockStates.length; ++i) {
            int localX;
            char state = blockStates[i];
            int blockId = state >> 4;
            if (blockId == 0) continue;
            int metadata = state & 0xF;
            boolean matched = false;
            for (localX = 0; localX < targets.length; ++localX) {
                SearchBlock searchBlock = targets[localX];
                if (searchBlock.M() == blockId && (searchBlock.i() == -1 || searchBlock.i() == metadata)) {
                    matched = true;
                    break;
                }
                Predicate<Character> predicate = searchBlock.E();
                if (predicate == null || !predicate.test(Character.valueOf(state))) continue;
                matched = true;
                break;
            }
            if (!matched) continue;
            localX = i % 16;
            int worldY = i / 256 + sectionBaseY;
            int localZ = i / 16 % 16;
            int worldX = (chunkX << 4) + localX;
            int finalY = worldY;
            int worldZ = (chunkZ << 4) + localZ;
            if (onlyCaves && !SearchBlockChunkScanner.hasAdjacentAir(worldX, finalY, worldZ, airPositions)) continue;
            SearchBlockRenderEntry searchBlockRenderEntry = SearchBlockChunkScanner.obtain(blockId, metadata, worldX, finalY, worldZ);
            results.add(searchBlockRenderEntry);
        }
    }

    private static boolean hasAdjacentAir(int x, int y, int z, Set<Long> airPositions) {
        int[][] offsets;
        for (int[] offset : offsets = new int[][]{{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
            int neighborX = x + offset[0];
            int neighborY = y + offset[1];
            int neighborZ = z + offset[2];
            long key = SearchBlockChunkScanner.packPosition(neighborX, neighborY, neighborZ);
            if (!airPositions.contains(key)) continue;
            return true;
        }
        return false;
    }

    public static ArrayList<SearchBlockRenderEntry> scanLoadedChunks(List<SearchBlock> searchBlocks, int maxDistance, boolean onlyCaves) {
        int chunkZ;
        int chunkX;
        ArrayList<SearchBlockRenderEntry> results = new ArrayList<SearchBlockRenderEntry>();
        WorldClient worldClient = Minecraft.theWorld();
        EntityPlayerSP entityPlayerSP = Minecraft.thePlayer();
        double playerX = entityPlayerSP.z();
        double playerZ = entityPlayerSP.h();
        // Modern versions (1.16.5+) store block states in paletted containers,
        // not the 1.8-1.12 char[] data field, and ClientChunkProvider's
        // chunkListing field does not exist there; scan by world coordinates.
        if (ForgeVersion.MC_1_16_5.d()) {
            return SearchBlockChunkScanner.scanLoadedChunksModern(
                    null, searchBlocks, maxDistance, onlyCaves,
                    worldClient, playerX, playerZ);
        }
        ClientChunkProvider clientChunkProvider = worldClient.U();
        List<Chunk> loadedChunks = clientChunkProvider.L();
        HashSet<Long> airPositions = new HashSet<Long>();
        if (onlyCaves) {
            for (Chunk chunk : loadedChunks) {
                List<ChunkSection> sections = chunk.U();
                for (Object section : sections) {
                    if (section == null || ((Wrapper)section).isNull() || ((ChunkSection)section).C() == null) continue;
                    int sectionBaseY = ((ChunkSection)section).l();
                    char[] blockStates = ((ChunkSection)section).C();
                    int cx = chunk.a();
                    chunkZ = (int)MathUtil.Z(playerX, 0.0, playerZ, cx << 4, 0.0, (chunkX = chunk.j()) << 4);
                    if (chunkZ > maxDistance) continue;
                    SearchBlockChunkScanner.markAirBlocks(blockStates, cx, sectionBaseY, chunkX, airPositions);
                }
            }
        }
        SearchBlock[] targets = searchBlocks.toArray(new SearchBlock[0]);
        for (Chunk chunk : loadedChunks) {
            List<ChunkSection> sections = chunk.U();
            for (ChunkSection chunkSection : sections) {
                if (chunkSection == null || chunkSection.isNull() || chunkSection.C() == null) continue;
                int sectionBaseY = chunkSection.l();
                char[] blockStates = chunkSection.C();
                chunkX = chunk.a();
                int distance = (int)MathUtil.Z(playerX, 0.0, playerZ, (chunkX << 4) + 8, 0.0, ((chunkZ = chunk.j()) << 4) + 8);
                if (distance > maxDistance) continue;
                SearchBlockChunkScanner.scanSection(blockStates, chunkX, sectionBaseY, chunkZ, targets, results, airPositions, onlyCaves);
            }
        }
        return results;
    }

    /** Modern (1.16.5+) scan: block states live in paletted containers, so the
     *  1.8-1.12 char[] shortcut is unusable, and ClientChunkProvider's
     *  chunkListing field does not exist on the modern ClientChunkCache. Walk
     *  the world coordinates around the player instead, comparing each block's
     *  registry id with the search blocks. */
    private static ArrayList<SearchBlockRenderEntry> scanLoadedChunksModern(
            List<Chunk> ignoredChunks, List<SearchBlock> searchBlocks,
            int maxDistance, boolean onlyCaves, WorldClient worldClient,
            double playerX, double playerZ) {
        ArrayList<SearchBlockRenderEntry> results = new ArrayList<SearchBlockRenderEntry>();
        SearchBlock[] targets = searchBlocks.toArray(new SearchBlock[0]);
        if (targets.length == 0) {
            return results;
        }
        int chunkRadius = (maxDistance / 16) + 1;
        int centerChunkX = ((int)Math.floor(playerX)) >> 4;
        int centerChunkZ = ((int)Math.floor(playerZ)) >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; ++dx) {
            for (int dz = -chunkRadius; dz <= chunkRadius; ++dz) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                int distance = (int)MathUtil.Z(playerX, 0.0, playerZ,
                        (chunkX << 4) + 8, 0.0, (chunkZ << 4) + 8);
                if (distance > maxDistance) continue;
                for (int y = 0; y < 256; ++y) {
                    for (int z = 0; z < 16; ++z) {
                        for (int x = 0; x < 16; ++x) {
                            int worldX = (chunkX << 4) + x;
                            int worldY = y;
                            int worldZ = (chunkZ << 4) + z;
                            Block block = worldClient.getBlockByPos(worldX, worldY, worldZ);
                            if (block == null || block.isNull()) continue;
                            int blockId = Block.R(block);
                            if (blockId == 0) continue;
                            for (SearchBlock searchBlock : targets) {
                                if (!searchBlock.T() || searchBlock.M() == -1
                                        || searchBlock.M() != blockId) continue;
                                if (onlyCaves && !SearchBlockChunkScanner
                                        .hasAdjacentAirModern(worldClient, worldX, worldY, worldZ)) {
                                    continue;
                                }
                                int metadata = 0;
                                SearchBlockRenderEntry entry = SearchBlockChunkScanner.obtain(
                                        blockId, metadata, worldX, worldY, worldZ);
                                results.add(entry);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    private static boolean hasAdjacentAirModern(WorldClient worldClient,
                                                int x, int y, int z) {
        int[][] offsets = new int[][]{{0, 1, 0}, {0, -1, 0}, {1, 0, 0},
                {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            Block neighbor = worldClient.getBlockByPos(
                    x + offset[0], y + offset[1], z + offset[2]);
            if (neighbor != null && !neighbor.isNull()) {
                int id = Block.R(neighbor);
                if (id == 0 || id == 8 || id == 9 || id == 30) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private static void markAirBlocks(char[] blockStates, int chunkX, int sectionBaseY, int chunkZ, Set<Long> airPositions) {
        for (int i = 0; i < blockStates.length; ++i) {
            char state = blockStates[i];
            int blockId = state >> 4;
            if (blockId != 0 && blockId != 8 && blockId != 9 && blockId != 30) continue;
            int localX = i % 16;
            int worldY = i / 256 + sectionBaseY;
            int localZ = i / 16 % 16;
            int worldX = (chunkX << 4) + localX;
            int finalY = worldY;
            int worldZ = (chunkZ << 4) + localZ;
            long key = SearchBlockChunkScanner.packPosition(worldX, finalY, worldZ);
            airPositions.add(key);
        }
    }
}
