package dev.completionist.jar;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;

/** One marble, earned by completing one advancement. Its colour comes from the advancement tab it belongs to. */
public class MarbleItem extends Item {
	public MarbleItem(Properties properties) {
		super(properties);
	}

	/**
	 * Builds a marble stack. The colour is also written to CUSTOM_MODEL_DATA, which the item model reads as a tint,
	 * so marbles can be coloured per tab without registering any custom client code.
	 */
	public static ItemStack create(MarbleTab tab, int count) {
		ItemStack stack = new ItemStack(ModItems.MARBLE, count);
		stack.set(ModComponents.MARBLE_TAB, tab);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(tab.color())));
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		MarbleTab tab = stack.get(ModComponents.MARBLE_TAB);
		if (tab != null) {
			builder.accept(tab.title().copy().withColor(tab.color()));
		}
		builder.accept(Component.translatable("item.completionist_jar.marble.hint"));
	}
}