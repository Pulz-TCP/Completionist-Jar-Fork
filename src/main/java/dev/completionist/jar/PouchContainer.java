package dev.completionist.jar;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A Container that is a live window onto the player's pouch list, so it behaves like an ender chest: the items belong
 * to the player, not to the pouch item. Only marbles may be placed in it.
 */
public final class PouchContainer implements Container {
	private final NonNullList<ItemStack> items;

	public PouchContainer(MarbleData data) {
		this.items = data.pouch();
	}

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return ContainerHelper.removeItem(items, slot, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.limitSize(getMaxStackSize(stack));
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.is(ModItems.MARBLE);
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		items.clear();
	}
}