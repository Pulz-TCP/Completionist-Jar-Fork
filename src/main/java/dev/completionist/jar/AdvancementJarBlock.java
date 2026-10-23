package dev.completionist.jar;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class AdvancementJarBlock extends Block implements EntityBlock {
	/** True while this jar is a member of a formed 3x3x4 / 6x6x8 structure. */
	public static final BooleanProperty PART = BooleanProperty.create("part");
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

	public AdvancementJarBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PART, false));
	}

	static BlockBehaviour.Properties jarProperties() {
		return BlockBehaviour.Properties.of().strength(0.5F).noOcclusion();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(PART) ? Shapes.block() : SHAPE;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AdvancementJarBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if (!(level.getBlockEntity(pos) instanceof AdvancementJarBlockEntity be)) {
			return;
		}
		JarContents contents = stack.get(ModComponents.JAR_CONTENTS);
		if (contents != null) {
			be.restore(contents);
		} else if (placer instanceof Player player) {
			be.setOwner(player.getUUID(), player.getGameProfile().name());
		}

		if (!level.isClientSide()) {
			List<BlockPos> layer = placer instanceof Player player ? JarLayerBuilder.extend(level, pos, player, stack) : List.of();
			if (layer.isEmpty()) {
				JarStructurePlanner.onPlaced(level, pos);
			} else {
				JarStructurePlanner.onPlacedRegion(level, pos, layer);
			}
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return level.getBlockEntity(pos) instanceof AdvancementJarBlockEntity be ? be.toItemStack() : super.getCloneItemStack(level, pos, state, includeData);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AdvancementJarBlockEntity be && player instanceof ServerPlayer sp) {
			be.reportTo(sp);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (stack.getItem() instanceof MarbleItem) {
			// Falls through to MarbleItem.useOn, which does the depositing.
			return InteractionResult.PASS;
		}
		if (stack.getItem() instanceof JarItem) {
			// Clicking a side of a jar stack with a jar adds the next layer; anything else falls through to normal placement.
			return hitResult.getDirection() != Direction.UP && JarLayerBuilder.buildOnTop(level, pos, player, hand, stack)
				? InteractionResult.SUCCESS
				: InteractionResult.PASS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}
}