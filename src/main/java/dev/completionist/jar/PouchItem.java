package dev.completionist.jar;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Opens the player's own marble storage. Any pouch opens the same storage, so losing one loses nothing. */
public class PouchItem extends Item {
	public PouchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			PouchContainer container = new PouchContainer(MarbleData.get(serverPlayer));
			serverPlayer.openMenu(new SimpleMenuProvider(
				(id, inventory, p) -> ChestMenu.threeRows(id, inventory, container),
				Component.translatable("container.completionist_jar.pouch")
			));
		}
		return InteractionResult.SUCCESS;
	}
}