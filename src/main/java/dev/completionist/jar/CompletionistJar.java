package dev.completionist.jar;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class CompletionistJar implements ModInitializer {
	public static final String MODID = "completionist_jar";
	public static final Logger LOGGER = LogUtils.getLogger();

	/** How often (in ticks) each online player's advancement progress is compared against the last known value. */
	private static final int POLL_INTERVAL_TICKS = 20;
	private static final Map<UUID, ProgressSnapshot> LAST_SEEN = new HashMap<>();

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModAttachments.init();
		ModItems.init();
		ModBlocks.init();
		RefreshJarItemsPayload.register();

		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> {
			if (be instanceof AdvancementJarBlockEntity jar) {
				AdvancementJarTracker.queueLoad(level, jar.getBlockPos());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CompletionistJar::pollAdvancements);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refreshPlayer(handler.getPlayer(), true));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_SEEN.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			AdvancementJarTracker.clearAll();
			JarItemRefresh.clearAll();
			LAST_SEEN.clear();
		});
	}

	/**
	 * Fabric has no "advancement earned" event, so progress is polled once a second. Counting the advancement tree is
	 * cheap, and polling also picks up /advancement grant and revoke.
	 */
	private static void pollAdvancements(MinecraftServer server) {
		AdvancementJarTracker.processQueuedLoads();
		if (server.getTickCount() % POLL_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refreshPlayer(player, false);
		}
	}

	private static void refreshPlayer(ServerPlayer player, boolean force) {
		MarbleAwarder.sync(player);
		ProgressSnapshot now = ProgressSnapshot.compute(player);
		ProgressSnapshot before = LAST_SEEN.put(player.getUUID(), now);
		if (!force && now.equals(before)) {
			return;
		}
		AdvancementJarTracker.refreshRoots(player.getUUID()).forEach(AdvancementJarBlockEntity::refreshIfOwnerOnline);
		JarItemRefresh.invalidate(player.getUUID());
		JarItemRefresh.refreshCarried(player);
	}
}