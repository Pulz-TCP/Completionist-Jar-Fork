package dev.completionist.jar;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class JarItem extends BlockItem {
	private static final int BAR_COLOR = 0xE6C354;
	private static final int BAR_WIDTH = 13;

	public JarItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		JarContents contents = stack.get(ModComponents.JAR_CONTENTS);
		return contents != null && contents.total() > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		JarContents contents = stack.get(ModComponents.JAR_CONTENTS);
		int percent = contents == null ? 0 : ProgressSnapshot.percent(contents.completed(), contents.total());
		return Math.round(BAR_WIDTH * percent / 100.0F);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}
}