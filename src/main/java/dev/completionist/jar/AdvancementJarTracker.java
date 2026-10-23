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

	static synchronized void clearLevel(ServerLevel level) {
		JARS.remove(level);
	}

	static synchronized void clearAll() {
		JARS.clear();
	}
}