package dev.completionist.jar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Detects hollow jar shells (3x3x4 and 6x6x8) and links their members to a master entity. */
final class JarStructurePlanner {
	private JarStructurePlanner() {
	}

	static void onPlaced(Level level, BlockPos pos) {
		onPlaced(level, pos, new HashSet<>(), false);
	}

	static void onPlaced(Level level, BlockPos pos, Set<BlockPos> alreadyChanged, boolean loadTime) {
		if (!level.isClientSide()) {
			int[] span = maxSpan();
			advance(level, pos.offset(-span[0], -span[1], -span[2]), pos, null, alreadyChanged, loadTime);
		}
	}

	static void onPlacedRegion(Level level, BlockPos pos, List<BlockPos> others) {
		if (!level.isClientSide()) {
			BlockPos min = pos;
			BlockPos max = pos;
			for (BlockPos cell : others) {
				min = BlockPos.min(min, cell);
				max = BlockPos.max(max, cell);
			}
			int[] span = maxSpan();
			advance(level, min.offset(-span[0], -span[1], -span[2]), max, null, new HashSet<>(), false);
		}
	}

	static void onPartRemoved(AdvancementJarBlockEntity removed, Level level, BlockPos breakPos) {
		BlockPos masterPos = removed.getMasterPos();
		JarSize size = removed.getStructureSize();
		if (level.isClientSide() || !removed.isPart() || masterPos == null || size == null) {
			return;
		}
		Set<BlockPos> changed = new HashSet<>();

		for (BlockPos cell : BlockPos.betweenClosed(masterPos, masterPos.offset(size.width() - 1, size.height() - 1, size.depth() - 1))) {
			if (!cell.equals(breakPos)
				&& !cell.equals(masterPos)
				&& level.isLoaded(cell)
				&& level.getBlockEntity(cell) instanceof AdvancementJarBlockEntity be
				&& masterPos.equals(be.getMasterPos())) {
				be.freeFromStructure();
				changed.add(cell.immutable());
			}
		}

		if (!breakPos.equals(masterPos)
			&& level.getBlockEntity(masterPos) instanceof AdvancementJarBlockEntity master
			&& masterPos.equals(master.getMasterPos())) {
			master.freeFromStructure();
			changed.add(masterPos.immutable());
		}

		int[] span = maxSpan();
		advance(
			level,
			masterPos.offset(-span[0], -span[1], -span[2]),
			masterPos.offset(size.width() - 1, size.height() - 1, size.depth() - 1),
			breakPos,
			changed,
			false
		);
	}

	private static void advance(Level level, BlockPos from, BlockPos to, BlockPos ignore, Set<BlockPos> changed, boolean loadTime) {
		List<AdvancementJarBlockEntity> free = new ArrayList<>();
		for (BlockPos cell : BlockPos.betweenClosed(from, to)) {
			if (!cell.equals(ignore) && level.isLoaded(cell) && level.getBlockEntity(cell) instanceof AdvancementJarBlockEntity be && !be.isPart()) {
				free.add(be);
			}
		}
		for (Formed formed : plan(level, free, ignore)) {
			merge(formed, changed);
		}
		settle(level, changed, loadTime);
	}

	private static void settle(Level level, Set<BlockPos> changed, boolean loadTime) {
		for (BlockPos cell : changed) {
			if (level.isLoaded(cell) && level.getBlockEntity(cell) instanceof AdvancementJarBlockEntity be) {
				be.settleMembership(loadTime);
			}
		}
	}

	private static int[] maxSpan() {
		int rx = 0;
		int ry = 0;
		int rz = 0;
		for (JarSize size : JarSize.STRUCTURE_SIZES) {
			rx = Math.max(rx, size.width() - 1);
			ry = Math.max(ry, size.height() - 1);
			rz = Math.max(rz, size.depth() - 1);
		}
		return new int[] {rx, ry, rz};
	}

	private static void merge(Formed formed, Set<BlockPos> changed) {
		AdvancementJarBlockEntity master = formed.master();
		ProgressSnapshot snapshot = mergeSnapshot(formed);
		if (master.refreshIfOwnerOnline()) {
			snapshot = master.getProgress();
		}
		BlockPos masterPos = master.getBlockPos();
		for (AdvancementJarBlockEntity member : formed.members()) {
			member.joinStructure(masterPos, formed.size(), snapshot);
			changed.add(member.getBlockPos());
		}
	}

	/** When jars with differing cached progress merge, keep the most advanced one. */
	private static ProgressSnapshot mergeSnapshot(Formed formed) {
		ProgressSnapshot best = null;
		for (AdvancementJarBlockEntity jar : formed.members()) {
			ProgressSnapshot candidate = jar.getProgress();
			if (candidate.total() > 0
				&& (best == null || candidate.completed() > best.completed()
					|| candidate.completed() == best.completed() && candidate.total() > best.total())) {
				best = candidate;
			}
		}
		return best == null ? ProgressSnapshot.EMPTY : best;
	}

	private static List<Formed> plan(Level level, List<AdvancementJarBlockEntity> free, BlockPos ignore) {
		free.sort(Comparator.<AdvancementJarBlockEntity>comparingInt(b -> b.getBlockPos().getY())
			.thenComparingInt(b -> b.getBlockPos().getX())
			.thenComparingInt(b -> b.getBlockPos().getZ()));
		Set<BlockPos> claimed = new HashSet<>();
		List<Formed> formed = new ArrayList<>();

		for (JarSize size : JarSize.STRUCTURE_SIZES) {
			for (AdvancementJarBlockEntity candidate : free) {
				BlockPos origin = candidate.getBlockPos();
				if (claimed.contains(origin)) {
					continue;
				}
				List<AdvancementJarBlockEntity> members = validBox(level, size, candidate, ignore, claimed);
				if (members != null) {
					formed.add(new Formed(size, candidate, members));
					for (AdvancementJarBlockEntity member : members) {
						claimed.add(member.getBlockPos());
					}
				}
			}
		}
		return formed;
	}

	/** A box is valid when its shell is entirely jars of one owner and its interior holds only jars or air. */
	private static List<AdvancementJarBlockEntity> validBox(
		Level level, JarSize size, AdvancementJarBlockEntity originJar, BlockPos ignore, Set<BlockPos> claimed
	) {
		if (originJar.getOwner() == null) {
			return null;
		}
		BlockPos origin = originJar.getBlockPos();
		UUID owner = originJar.getOwner();
		List<AdvancementJarBlockEntity> members = new ArrayList<>(size.width() * size.height() * size.depth());

		for (int dy = 0; dy < size.height(); dy++) {
			for (int dx = 0; dx < size.width(); dx++) {
				for (int dz = 0; dz < size.depth(); dz++) {
					boolean shell = dx == 0 || dx == size.width() - 1 || dz == 0 || dz == size.depth() - 1 || dy == 0 || dy == size.height() - 1;
					BlockPos cell = origin.offset(dx, dy, dz);
					if (cell.equals(ignore)) {
						if (shell) {
							return null;
						}
						continue;
					}
					if (!level.isLoaded(cell)) {
						return null;
					}
					if (level.getBlockEntity(cell) instanceof AdvancementJarBlockEntity jar) {
						if (jar.isPart() || claimed.contains(cell) || !owner.equals(jar.getOwner())) {
							return null;
						}
						members.add(jar);
					} else if (shell || !level.getBlockState(cell).isAir()) {
						return null;
					}
				}
			}
		}
		return members;
	}

	record Formed(JarSize size, AdvancementJarBlockEntity master, List<AdvancementJarBlockEntity> members) {
	}
}