package dev.completionist.jar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/** Lets a player grow a jar structure a whole layer at a time instead of block by block. */
final class JarLayerBuilder {
	private JarLayerBuilder() {
	}

	/** Right-clicking the side of a jar stack with a jar places a jar on top, which then fills out the layer. */
	static boolean buildOnTop(Level level, BlockPos clicked, Player player, InteractionHand hand, ItemStack stack) {
		Key key = Key.of(stack, player);
		if (!matches(level, clicked, key)) {
			return false;
		}

		BlockPos top = clicked;
		while (matches(level, top.above(), key)) {
			top = top.above();
		}

		Column column = findColumn(level, top.above(), key);
		if (column == null || column.layersBelow() < 2) {
			return false;
		}

		if (!level.getBlockState(top.above()).canBeReplaced()) {
			if (!level.isClientSide()) {
				reportBlocked(player, top.above(), level.getBlockState(top.above()));
			}
			return true;
		}
		if (!level.isClientSide()) {
			stack.useOn(new UseOnContext(player, hand, new BlockHitResult(Vec3.atCenterOf(top).add(0.0, 0.5, 0.0), Direction.UP, top, false)));
		}
		return true;
	}

	/** After a jar is placed on top of a ring, fill the rest of that layer, consuming jars from the inventory. */
	static List<BlockPos> extend(Level level, BlockPos pos, Player player, ItemStack placedStack) {
		if (level.isClientSide() || player.isShiftKeyDown()
			|| !(level.getBlockEntity(pos) instanceof AdvancementJarBlockEntity placed) || placed.getOwner() == null) {
			return List.of();
		}
		Key key = Key.of(placed);
		Column column = findColumn(level, pos, key);
		if (column == null) {
			return List.of();
		}

		boolean lid = column.layersBelow() == column.size().height() - 1;
		List<BlockPos> targets = new ArrayList<>();
		BlockState jarState = level.getBlockState(pos).getBlock().defaultBlockState();
		int width = column.size().width();

		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < column.size().depth(); dz++) {
				boolean ring = dx == 0 || dz == 0 || dx == width - 1 || dz == column.size().depth() - 1;
				BlockPos cell = column.origin().offset(dx, 0, dz).atY(pos.getY());
				if ((ring || lid) && !cell.equals(pos) && !matches(level, cell, key)) {
					if (!level.getBlockState(cell).canBeReplaced()
						|| !level.mayInteract(player, cell)
						|| !level.isUnobstructed(jarState, cell, CollisionContext.empty())) {
						reportBlocked(player, cell, level.getBlockState(cell));
						return List.of();
					}
					targets.add(cell);
				}
			}
		}

		boolean free = player.getAbilities().instabuild;
		if (targets.isEmpty() || (!free && available(player, placedStack, placed) < targets.size())) {
			return List.of();
		}
		for (BlockPos cell : targets) {
			JarContents contents = free ? null : take(player, placedStack, placed);
			level.setBlock(cell, jarState, 3);
			if (level.getBlockEntity(cell) instanceof AdvancementJarBlockEntity jar) {
				if (contents != null) {
					jar.restore(contents);
				} else {
					jar.setOwner(placed.getOwner(), placed.getOwnerName());
				}
			}
		}
		return targets;
	}

	private static @Nullable Column findColumn(Level level, BlockPos pos, Key key) {
		for (JarSize size : JarSize.STRUCTURE_SIZES) {
			Column best = null;
			for (int ox = pos.getX() - size.width() + 1; ox <= pos.getX(); ox++) {
				for (int oz = pos.getZ() - size.depth() + 1; oz <= pos.getZ(); oz++) {
					BlockPos origin = new BlockPos(ox, pos.getY(), oz);
					int layers = 0;
					while (layers < size.height() && completeRing(level, size, origin.below(layers + 1), key)) {
						layers++;
					}
					if (layers > 0 && layers < size.height() && (best == null || layers > best.layersBelow())) {
						best = new Column(size, origin, layers);
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	private static boolean completeRing(Level level, JarSize size, BlockPos origin, Key key) {
		for (int dx = 0; dx < size.width(); dx++) {
			for (int dz = 0; dz < size.depth(); dz++) {
				boolean ring = dx == 0 || dz == 0 || dx == size.width() - 1 || dz == size.depth() - 1;
				if (ring && !matches(level, origin.offset(dx, 0, dz), key)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean matches(Level level, BlockPos cell, Key key) {
		return level.isLoaded(cell)
			&& level.getBlockEntity(cell) instanceof AdvancementJarBlockEntity jar
			&& !jar.isPart()
			&& jar.getBlockState().is(key.block())
			&& key.owner().equals(jar.getOwner());
	}

	private static boolean usable(ItemStack stack, ItemStack placedStack, AdvancementJarBlockEntity placed) {
		if (stack.isEmpty() || !stack.is(placedStack.getItem())) {
			return false;
		}
		JarContents contents = stack.get(ModComponents.JAR_CONTENTS);
		return contents == null || placed.getOwner().equals(contents.owner());
	}

	private static int available(Player player, ItemStack placedStack, AdvancementJarBlockEntity placed) {
		Inventory inventory = player.getInventory();
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (usable(stack, placedStack, placed)) {
				count += stack == placedStack ? stack.getCount() - 1 : stack.getCount();
			}
		}
		return count;
	}

	private static @Nullable JarContents take(Player player, ItemStack placedStack, AdvancementJarBlockEntity placed) {
		Inventory inventory = player.getInventory();
		ItemStack from = null;
		for (int i = 0; i < inventory.getContainerSize() && from == null; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack != placedStack && usable(stack, placedStack, placed)) {
				from = stack;
			}
		}
		if (from == null && placedStack.getCount() > 1) {
			from = placedStack;
		}
		if (from == null) {
			return null;
		}
		JarContents contents = from.get(ModComponents.JAR_CONTENTS);
		from.shrink(1);
		return contents;
	}

	private static void reportBlocked(Player player, BlockPos cell, BlockState blocking) {
		Component message = blocking.canBeReplaced()
			? Component.translatable("advancement_jar.building.obstructed", cell.getX(), cell.getY(), cell.getZ())
			: Component.translatable("advancement_jar.building.blocked", blocking.getBlock().getName(), cell.getX(), cell.getY(), cell.getZ());
		player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
	}

	private record Column(JarSize size, BlockPos origin, int layersBelow) {
	}

	private record Key(Block block, UUID owner) {
		static Key of(AdvancementJarBlockEntity jar) {
			return new Key(jar.getBlockState().getBlock(), jar.getOwner());
		}

		static Key of(ItemStack stack, Player player) {
			Block block = ((JarItem) stack.getItem()).getBlock();
			JarContents contents = stack.get(ModComponents.JAR_CONTENTS);
			return new Key(block, contents != null ? contents.owner() : player.getUUID());
		}
	}
}