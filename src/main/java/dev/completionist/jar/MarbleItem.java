package dev.completionist.jar;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

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

	/**
	 * Right-clicking a jar with a marble drops one in (holding the button keeps pouring); sneaking drops the whole
	 * stack. A deposit just bumps the owner's ledger, and every jar they own re-reads it, so nothing is stored in
	 * the jar itself that could be lost with it.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!(level.getBlockState(pos).getBlock() instanceof AdvancementJarBlock)) {
			return InteractionResult.PASS;
		}
		if (!(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		MarbleTab tab = stack.get(ModComponents.MARBLE_TAB);
		if (tab == null || !(level.getBlockEntity(pos) instanceof AdvancementJarBlockEntity jar)) {
			return InteractionResult.PASS;
		}
		if (jar.getOwner() == null) {
			player.sendSystemMessage(Component.translatable("advancement_jar.deposit.no_owner").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!jar.getOwner().equals(player.getUUID())) {
			String owner = jar.getOwnerName() == null ? jar.getOwner().toString() : jar.getOwnerName();
			player.sendSystemMessage(Component.translatable("advancement_jar.deposit.not_yours", owner).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}

		int count = player.isShiftKeyDown() ? stack.getCount() : 1;
		MarbleData.get(player).deposit(tab.tab(), count);
		stack.shrink(count);
		CompletionistJar.refreshJars(player);

		// Pitch climbs a little as the jar fills, so the last marbles sound different from the first.
		ProgressSnapshot progress = jar.getProgress();
		float fill = progress.total() <= 0 ? 0.0F : progress.completed() / (float) progress.total();
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_FALL, SoundSource.BLOCKS, 1.0F, 0.8F + 0.6F * fill + level.getRandom().nextFloat() * 0.1F);
		return InteractionResult.SUCCESS;
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
