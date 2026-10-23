package dev.completionist.jar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Server-side index of loaded jar positions per level, so progress changes can find a player's jars. */
final class AdvancementJarTracker {
	private static final Map<ServerLevel, Set<BlockPos>> JARS = new HashMap<>();

	private AdvancementJarTracker() {
	}

	static synchronized void register(ServerLevel level, BlockPos pos) {
		JARS.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
	}

	static synchronized void unregister(ServerLevel level, BlockPos pos) {
		Set<BlockPos> positions = JARS.get(level);
		if (positions != null) {
			positions.remove(pos.immutable());
			if (positions.isEmpty()) {
				JARS.remove(level);
			}
		}
	}

	static synchronized List<AdvancementJarBlockEntity> ownedBy(UUID owner) {
		List<AdvancementJarBlockEntity> found = new ArrayList<>();
		Iterator<Map.Entry<ServerLevel, Set<BlockPos>>> levels = JARS.entrySet().iterator();
		while (levels.hasNext()) {
			Map.Entry<ServerLevel, Set<BlockPos>> level = levels.next();
			Iterator<BlockPos> positions = level.getValue().iterator();
			while (positions.hasNext()) {
				BlockPos pos = positions.next();
				var be = level.getKey().getBlockEntity(pos);
				if (be instanceof AdvancementJarBlockEntity jar && owner.equals(jar.getOwner())) {
					found.add(jar);
				} else if (be == null) {
					positions.remove();
				}
			}
			if (level.getValue().isEmpty()) {
				levels.remove();
			}
		}
		return found;
	}

	/** One entity per standalone jar or formed structure (the master), which is what needs refreshing. */
	static synchronized List<AdvancementJarBlockEntity> refreshRoots(UUID owner) {
		List<AdvancementJarBlockEntity> roots = new ArrayList<>();
		for (AdvancementJarBlockEntity be : ownedBy(owner)) {
			if (!be.isPart() || be.isMaster()) {
				roots.add(be);
			}
		}
		return roots;
	}

	private static final Map<ServerLevel, Set<BlockPos>> QUEUED_LOADS = new HashMap<>();

	/**
	 * A jar entity was just loaded. We must not touch the world from inside the chunk-load event: reading or writing
	 * blocks in the chunk that is still being loaded makes the server wait on itself and the world hangs on load.
	 * So the work is queued and done on a later tick (see processQueuedLoads).
	 */
	static synchronized void queueLoad(ServerLevel level, BlockPos pos) {
		QUEUED_LOADS.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
	}

	/** Runs the post-load hook for queued jars whose chunk is now fully loaded; others stay queued. */
	static synchronized void processQueuedLoads() {
		if (QUEUED_LOADS.isEmpty()) {
			return;
		}
		List<Runnable> ready = new ArrayList<>();
		Iterator<Map.Entry<ServerLevel, Set<BlockPos>>> levels = QUEUED_LOADS.entrySet().iterator();
		while (levels.hasNext()) {
			Map.Entry<ServerLevel, Set<BlockPos>> entry = levels.next();
			ServerLevel level = entry.getKey();
			Iterator<BlockPos> positions = entry.getValue().iterator();
			while (positions.hasNext()) {
				BlockPos pos = positions.next();
				if (!level.isLoaded(pos)) {
					continue;
				}
				positions.remove();
				ready.add(() -> {
					if (level.getBlockEntity(pos) instanceof AdvancementJarBlockEntity jar) {
						jar.onLoadedIntoWorld();
					}
				});
			}
			if (entry.getValue().isEmpty()) {
				levels.remove();
			}
		}
		ready.forEach(Runnable::run);
	}

	static synchronized void clearLevel(ServerLevel level) {
		JARS.remove(level);
	}

	static synchronized void clearAll() {
		JARS.clear();
		QUEUED_LOADS.clear();
	}
}