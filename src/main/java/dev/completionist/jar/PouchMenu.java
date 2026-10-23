package dev.completionist.jar;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The pouch screen is a vanilla 3-row chest menu, so the client needs no custom screen. Vanilla chest slots accept
 * anything, so after the chest menu builds itself we swap its pouch slots for ones that only accept marbles. The
 * server enforces this (including shift-click), which is the side that matters.
 */
public final class PouchMenu extends ChestMenu {
	public PouchMenu(int containerId, Inventory inventory, Container pouch) {
		super(MenuType.GENERIC_9x3, containerId, inventory, pouch, 3);
		for (int i = 0; i < pouch.getContainerSize(); i++) {
			Slot vanilla = slots.get(i);
			Slot marbleOnly = new MarbleSlot(pouch, i, vanilla.x, vanilla.y);
			marbleOnly.index = vanilla.index;
			slots.set(i, marbleOnly);
		}
	}

	private static final class MarbleSlot extends Slot {
		MarbleSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.is(ModItems.MARBLE);
		}
	}
}