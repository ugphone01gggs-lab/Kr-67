/*
 * SusChunkFinder - readable reconstruction from 67client-AnalFuckedNiceAndSlow.jar
 *
 * IMPORTANT:
 * This is an analysis/decompilation aid, NOT a drop-in replacement for the original mod.
 * The original jar is obfuscated and uses Minecraft intermediary mappings.
 * Names such as BLOCK_A, BLOCK_B and PROPERTY_X are intentionally unresolved:
 * their exact human-readable Yarn names must be mapped for the target MC version.
 */

import java.util.*;

public final class SusChunkFinderReconstruction {
    private static final int CHUNKS_SCANNED_PER_TICK = 8;
    private static final int RESCAN_DELAY_TICKS = 200;

    // Original fields, represented with readable names.
    private final Set<Long> candidateChunks = ConcurrentSets.newKeySet();
    private final Set<Long> reportedChunks = ConcurrentSets.newKeySet();
    private final Map<Long, Integer> firstSeenTick = new HashMap<>();
    private Set<Long> groupedResults = Set.of();

    private int scanCursor;
    private int tickCounter;
    private int lastGroupingRadius;

    /*
     * Called every client tick while a world and player exist.
     *
     * renderDistance is read from Minecraft's client options.
     * The scan square has side length (2 * renderDistance + 1).
     */
    public void onTick(WorldView world, ChunkPos playerChunk, int renderDistance) {
        if (world == null || playerChunk == null) return;

        tickCounter++;

        int side = renderDistance * 2 + 1;
        int total = side * side;
        if (total <= 0) return;

        for (int i = 0; i < CHUNKS_SCANNED_PER_TICK; i++) {
            int index = scanCursor;
            scanCursor = (scanCursor + 1) % total;

            int dx = index % side - renderDistance;
            int dz = index / side - renderDistance;
            int chunkX = playerChunk.x() + dx;
            int chunkZ = playerChunk.z() + dz;

            ChunkView chunk = world.getChunkIfLoaded(chunkX, chunkZ);
            if (chunk == null || chunk.isEmpty()) continue;

            long key = ChunkPos.pack(chunkX, chunkZ);
            firstSeenTick.putIfAbsent(key, tickCounter);

            boolean oldEnough =
                tickCounter - firstSeenTick.get(key) >= RESCAN_DELAY_TICKS;

            if (oldEnough && containsTargetBlock(chunk)) {
                if (candidateChunks.add(key)) {
                    // Original code marks the result list as needing refresh.
                }
                if (reportedChunks.add(key)) {
                    world.notifySusChunk(chunkX, chunkZ);
                }
            } else {
                candidateChunks.remove(key);
            }
        }

        // Forget chunks that have moved outside the current render-distance square.
        candidateChunks.removeIf(key ->
            outsideSquare(key, playerChunk, renderDistance));
        firstSeenTick.keySet().removeIf(key ->
            outsideSquare(key, playerChunk, renderDistance));

        // Original code rebuilds connected groups when the configured grouping
        // radius changes or when the candidate set changes.
        if (lastGroupingRadius != renderDistance || groupingNeedsRefresh()) {
            rebuildGroups(renderDistance);
        }
    }

    /*
     * Exact structure found in the original bytecode:
     * - Iterate every vertical section in the chunk.
     * - Skip null/empty sections.
     * - Check each block position in the 16 x 16 x 16 section.
     * - Return true as soon as the target predicate matches.
     *
     * The original predicate matches either of two block constants and also
     * requires a particular integer block-state property to equal 5.
     * The obfuscated intermediary field IDs are not safely nameable here
     * without the exact Minecraft/Yarn mapping version.
     */
    private boolean containsTargetBlock(ChunkView chunk) {
        for (SectionView section : chunk.sections()) {
            if (section == null || section.isEmpty()) continue;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockStateView state = section.getBlockState(x, y, z);
                        if (isOriginalTargetState(state)) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isOriginalTargetState(BlockStateView state) {
        // Equivalent to the original bytecode's two block checks
        // plus PROPERTY_X == 5. Resolve these identifiers for the target MC version.
        return (state.isBlock("BLOCK_A") || state.isBlock("BLOCK_B"))
                && state.getIntegerProperty("PROPERTY_X") == 5;
    }

    /*
     * Original grouping behavior is a breadth-first search over candidate
     * chunks. Candidates within the configured square radius are connected.
     * A group containing one chunk is represented by that chunk; larger groups
     * are represented by the rounded average chunk coordinate (centroid).
     */
    private void rebuildGroups(int radius) {
        Set<Long> remaining = new HashSet<>(candidateChunks);
        Set<Long> visited = new HashSet<>();
        Set<Long> centers = new HashSet<>();

        int searchRadius = Math.max(1, radius);

        for (long seed : remaining) {
            if (!visited.add(seed)) continue;

            List<Long> group = new ArrayList<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(seed);

            while (!queue.isEmpty()) {
                long current = queue.remove();
                group.add(current);

                int cx = ChunkPos.x(current);
                int cz = ChunkPos.z(current);

                for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                    for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                        if (dx == 0 && dz == 0) continue;

                        long neighbor = ChunkPos.pack(cx + dx, cz + dz);
                        if (remaining.contains(neighbor) && visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }

            centers.add(group.size() == 1 ? group.get(0) : roundedCenter(group));
        }

        groupedResults = Set.copyOf(centers);
        lastGroupingRadius = radius;
    }

    private static long roundedCenter(List<Long> group) {
        long sumX = 0, sumZ = 0;
        for (long key : group) {
            sumX += ChunkPos.x(key);
            sumZ += ChunkPos.z(key);
        }
        int x = (int) Math.round((double) sumX / group.size());
        int z = (int) Math.round((double) sumZ / group.size());
        return ChunkPos.pack(x, z);
    }

    private static boolean outsideSquare(long key, ChunkPos center, int radius) {
        return Math.abs(ChunkPos.x(key) - center.x()) > radius
            || Math.abs(ChunkPos.z(key) - center.z()) > radius;
    }

    private boolean groupingNeedsRefresh() {
        // The original implementation uses an internal dirty flag.
        return false; // placeholder for readable reconstruction only
    }

    public Set<Long> getGroupedResults() {
        return groupedResults;
    }

    // Small interfaces used to make the reconstruction readable without
    // pretending it is directly compilable against a particular MC version.
    interface WorldView {
        ChunkView getChunkIfLoaded(int x, int z);
        void notifySusChunk(int x, int z);
    }
    interface ChunkView {
        boolean isEmpty();
        SectionView[] sections();
    }
    interface SectionView {
        boolean isEmpty();
        BlockStateView getBlockState(int x, int y, int z);
    }
    interface BlockStateView {
        boolean isBlock(String id);
        int getIntegerProperty(String id);
    }
    record ChunkPos(int x, int z) {
        static long pack(int x, int z) { return ((long)x & 0xffffffffL) | ((long)z << 32); }
        static int x(long key) { return (int)key; }
        static int z(long key) { return (int)(key >>> 32); }
    }
    static final class ConcurrentSets {
        static <T> Set<T> newKeySet() {
            return java.util.concurrent.ConcurrentHashMap.newKeySet();
        }
    }
}
