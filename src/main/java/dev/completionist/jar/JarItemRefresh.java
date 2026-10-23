package dev.completionist.jar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Keeps the progress stored on jar item stacks (what the tooltip and item model show) up to date. */
final class JarItemRefresh {
	static final int COOLDOWN_TICKS = 40;
	private static final Map<UUID, Cached> SNAPSHOTS = new HashMap<>();

	private JarItemRefresh() {
	}

	static void refreshCarried(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			refresh(server, inventory.getItem(i));
		}

		AbstractContainerMenu menu = player.containerMenu;
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory) && refresh(server, slot.getItem())) {
				slot.setChanged();
			}
		}
		refresh(server, menu.getCarried());
		menu.broadcastChanges();
	}

	static void invalidate(UUID owner) {
		SNAPSHOTS.remove(owner);
	}

	static void clearAll() {
		SNAPSHOTS.clear();
	}

	static boolean refresh(MinecraftServer server, ItemStack stack) {
		JarContents contents = stack.get(ModComponents.JAR_CONTENTS);
		if (contents == null || !(stack.getItem() instanceof JarItem)) {
			return false;
		}
		ProgressSnapshot snapshot = snapshot(server, contents.owner());
		if (snapshot == null) {
			return false;
		}
		JarContents next = JarContents.of(contents.owner(), contents.ownerName(), snapshot);
		if (next.equals(contents)) {
			return false;
		}
		stack.set(ModComponents.JAR_CONTENTS, next);
		return true;
	}

	private static @Nullable ProgressSnapshot snapshot(MinecraftServer server, UUID owner) {
		long now = server.getTickCount();
		Cached cached = SNAPSHOTS.get(owner);
		if (cached != null && now - cached.tick() < COOLDOWN_TICKS) {
			return cached.snapshot();
		}
		ServerPlayer onlineOwner = server.getPlayerList().getPlayer(owner);
		if (onlineOwner == null) {
			return null;
		}
		ProgressSnapshot snapshot = ProgressSnapshot.compute(onlineOwner);
		SNAPSHOTS.put(owner, new Cached(snapshot, now));
		return snapshot;
	}

	private record Cached(ProgressSnapshot snapshot, long tick) {
	}
}