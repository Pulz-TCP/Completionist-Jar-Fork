package dev.completionist.jar;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Turns completed advancements into marbles. It is safe to run as often as you like: an advancement only ever pays
 * out once (tracked by id), which also makes this the "backfill" for progress made before the mod was installed.
 */
final class MarbleAwarder {
	private MarbleAwarder() {
	}

	static void sync(ServerPlayer player) {
		MarbleData data = MarbleData.get(player);
		if (!data.pouchGranted()) {
			ItemStack pouch = new ItemStack(ModItems.POUCH);
			if (!player.getInventory().add(pouch)) {
				player.drop(pouch, false);
			}
			data.setPouchGranted(true);
		}

		ServerAdvancementManager manager = player.level().getServer().getAdvancements();
		PlayerAdvancements advancements = player.getAdvancements();
		Map<Identifier, Integer> earned = new HashMap<>();

		for (AdvancementNode node : manager.tree().nodes()) {
			AdvancementHolder holder = node.holder();
			if (holder.value().display().isEmpty() || !advancements.getOrStartProgress(holder).isDone()) {
				continue;
			}
			if (data.markAwarded(holder.id())) {
				earned.merge(node.root().holder().id(), 1, Integer::sum);
			}
		}

		earned.forEach((tab, count) -> data.addMarbles(new MarbleTab(tab, tabTitle(manager, tab)), count));
		data.flushPending(tab -> tabTitle(manager, tab));
	}

	private static Component tabTitle(ServerAdvancementManager manager, Identifier tab) {
		AdvancementHolder root = manager.get(tab);
		if (root != null && root.value().display().isPresent()) {
			return root.value().display().map(DisplayInfo::getTitle).get();
		}
		return Component.literal(tab.toString());
	}
}