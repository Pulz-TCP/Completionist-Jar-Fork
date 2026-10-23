package dev.completionist.jar;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class AdvancementJarBlockEntity extends BlockEntity {
	public static final int MARBLE_CAPACITY = JarSize.SMALL.marbles();
	private static final Object DEFER_MASTER = new Object();

	private @Nullable UUID owner;
	private @Nullable String ownerName;
	private ProgressSnapshot progress = ProgressSnapshot.EMPTY;
	private @Nullable BlockPos masterPos;
	private @Nullable JarSize structureSize;

	public AdvancementJarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ADVANCEMENT_JAR_BE, pos, state);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (level instanceof ServerLevel serverLevel) {
			AdvancementJarTracker.register(serverLevel, getBlockPos());
		}
	}

	/** Called by CompletionistJar when the entity is added to a server level (Fabric has no BlockEntity.onLoad). */
	void onLoadedIntoWorld() {
		selfHealIfOrphaned(true);
		if (level != null && !level.isClientSide()) {
			setPartState(isPart(), true);
		}
	}

	@Override
	public void setRemoved() {
		if (level instanceof ServerLevel serverLevel) {
			AdvancementJarTracker.unregister(serverLevel, getBlockPos());
		}
		super.setRemoved();
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide() && isPart()) {
			JarStructurePlanner.onPartRemoved(this, level, pos);
		}
		super.preRemoveSideEffects(pos, state);
	}

	public @Nullable UUID getOwner() {
		return owner;
	}

	public @Nullable String getOwnerName() {
		return ownerName;
	}

	public boolean isPart() {
		return masterPos != null;
	}

	public boolean isMaster() {
		return isPart() && masterPos.equals(getBlockPos());
	}

	public @Nullable BlockPos getMasterPos() {
		return masterPos;
	}

	public @Nullable JarSize getStructureSize() {
		return structureSize;
	}

	private @Nullable AdvancementJarBlockEntity master() {
		return probeMaster() instanceof AdvancementJarBlockEntity master ? master : null;
	}

	/** The master entity, null if it is gone, or DEFER_MASTER if its chunk is not loaded (so we must not conclude anything). */
	private Object probeMaster() {
		if (isMaster()) {
			return this;
		} else if (level == null || masterPos == null) {
			return null;
		} else if (level.isClientSide()) {
			return level.getBlockEntity(masterPos) instanceof AdvancementJarBlockEntity master ? master : null;
		} else if (!level.isLoaded(masterPos)) {
			return DEFER_MASTER;
		} else if (level.getChunk(masterPos.getX() >> 4, masterPos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk) {
			return chunk.getBlockEntity(masterPos, LevelChunk.EntityCreationType.CHECK) instanceof AdvancementJarBlockEntity master ? master : null;
		} else {
			return DEFER_MASTER;
		}
	}

	public ProgressSnapshot getProgress() {
		AdvancementJarBlockEntity master = master();
		return master != null ? master.progress : progress;
	}

	public void setOwner(UUID uuid, String name) {
		owner = uuid;
		ownerName = name;
		progress = ProgressSnapshot.EMPTY;
		refreshIfOwnerOnline();
		notifyCacheChange();
	}

	public void restore(JarContents contents) {
		owner = contents.owner();
		ownerName = contents.ownerName();
		progress = contents.snapshot();
		refreshIfOwnerOnline();
		notifyCacheChange();
	}

	void joinStructure(BlockPos masterPos, JarSize size, ProgressSnapshot snapshot) {
		this.masterPos = masterPos;
		this.structureSize = size;
		this.progress = snapshot;
		setChanged();
	}

	void freeFromStructure() {
		AdvancementJarBlockEntity master = master();
		if (isValidMaster(master)) {
			progress = master.progress;
		}
		masterPos = null;
		structureSize = null;
		setChanged();
	}

	void setPartState(boolean part, boolean loadTime) {
		if (level != null && !level.isClientSide()) {
			BlockState state = level.getBlockState(worldPosition);
			if (state.hasProperty(AdvancementJarBlock.PART) && state.getValue(AdvancementJarBlock.PART) != part) {
				// 18 = UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE, quiet on load; 3 = neighbours + clients.
				int flags = loadTime ? 18 : 3;
				level.setBlock(worldPosition, state.setValue(AdvancementJarBlock.PART, part), flags);
			}
		}
	}

	void settleMembership(boolean loadTime) {
		if (level != null && !level.isClientSide()) {
			setPartState(isPart(), loadTime);
			syncClients();
		}
	}

	private boolean isValidMaster(@Nullable AdvancementJarBlockEntity candidate) {
		if (candidate == null || !candidate.isMaster() || masterPos == null || structureSize == null) {
			return false;
		}
		if (candidate.getStructureSize() != structureSize || !Objects.equals(candidate.getOwner(), owner)) {
			return false;
		}
		BlockPos origin = candidate.getBlockPos();
		int dx = worldPosition.getX() - origin.getX();
		int dy = worldPosition.getY() - origin.getY();
		int dz = worldPosition.getZ() - origin.getZ();
		return dx >= 0 && dx < structureSize.width() && dy >= 0 && dy < structureSize.height() && dz >= 0 && dz < structureSize.depth();
	}

	public ItemStack toItemStack() {
		ItemStack stack = new ItemStack(ModBlocks.ADVANCEMENT_JAR_ITEM);
		if (owner != null) {
			stack.set(ModComponents.JAR_CONTENTS, JarContents.of(owner, ownerName == null ? owner.toString() : ownerName, getProgress()));
		}
		return stack;
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		if (owner != null) {
			components.set(ModComponents.JAR_CONTENTS, JarContents.of(owner, ownerName == null ? owner.toString() : ownerName, getProgress()));
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		JarContents contents = components.get(ModComponents.JAR_CONTENTS);
		if (contents != null) {
			owner = contents.owner();
			ownerName = contents.ownerName();
			progress = contents.snapshot();
		}
	}

	/** Recomputes progress from the owner if they are online. Returns whether the owner's data was available. */
	public boolean refreshIfOwnerOnline() {
		if (isPart() && !isMaster()) {
			AdvancementJarBlockEntity master = master();
			if (master != null) {
				if (isValidMaster(master) && !master.progress.equals(progress)) {
					progress = master.progress;
					setChanged();
				}
				return master.refreshIfOwnerOnline();
			}
		}

		if (owner != null && level instanceof ServerLevel serverLevel) {
			ServerPlayer onlineOwner = serverLevel.getServer().getPlayerList().getPlayer(owner);
			if (onlineOwner == null) {
				return false;
			}
			ProgressSnapshot next = ProgressSnapshot.compute(onlineOwner);
			boolean changed = !next.equals(progress);
			if (changed) {
				progress = next;
				setChanged();
			}
			if (changed && (!isPart() || isMaster())) {
				notifyCacheChange();
			}
			pushProgressToParts();
			return true;
		}
		return false;
	}

	private void pushProgressToParts() {
		if (level != null && !level.isClientSide() && isMaster() && structureSize != null) {
			BlockPos origin = getBlockPos();
			for (BlockPos cell : BlockPos.betweenClosed(
				origin, origin.offset(structureSize.width() - 1, structureSize.height() - 1, structureSize.depth() - 1))) {
				if (!cell.equals(origin)
					&& level.isLoaded(cell)
					&& level.getBlockEntity(cell) instanceof AdvancementJarBlockEntity part
					&& masterPos.equals(part.getMasterPos())
					&& !part.progress.equals(progress)) {
					part.progress = progress;
					part.setChanged();
				}
			}
		}
	}

	private void syncClients() {
		if (level != null && !level.isClientSide() && level.getBlockState(worldPosition).hasBlockEntity()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	void notifyCacheChange() {
		setChanged();
		syncClients();
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	public void reportTo(ServerPlayer clicker) {
		selfHealIfOrphaned(false);
		refreshIfOwnerOnline();
		clicker.sendSystemMessage(buildReport());
	}

	void selfHealIfOrphaned(boolean fromLoad) {
		if (level == null || level.isClientSide() || !isPart() || isMaster()) {
			return;
		}
		Object probe = probeMaster();
		if (probe == DEFER_MASTER) {
			return;
		}
		AdvancementJarBlockEntity master = probe instanceof AdvancementJarBlockEntity m ? m : null;
		if (isValidMaster(master)) {
			if (!master.progress.equals(progress)) {
				progress = master.progress;
				setChanged();
			}
		} else {
			freeFromStructure();
			Set<BlockPos> changed = new HashSet<>();
			changed.add(getBlockPos().immutable());
			JarStructurePlanner.onPlaced(level, getBlockPos(), changed, fromLoad);
		}
	}

	public Component buildReport() {
		if (owner == null) {
			return Component.translatable("advancement_jar.report.no_owner");
		}
		ProgressSnapshot snapshot = getProgress();
		MutableComponent report = Component.translatable(
			"advancement_jar.report.header",
			ownerName == null ? owner.toString() : ownerName, snapshot.completed(), snapshot.total(), snapshot.percent()
		);
		return report;
	}

	private Component tabTitle(Identifier rootId) {
		if (level instanceof ServerLevel serverLevel) {
			AdvancementHolder root = serverLevel.getServer().getAdvancements().get(rootId);
			if (root != null && root.value().display().isPresent()) {
				return root.value().display().get().getTitle();
			}
		}
		return Component.literal(rootId.toString());
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (owner != null) {
			output.store("Owner", UUIDUtil.CODEC, owner);
		}
		if (ownerName != null) {
			output.putString("OwnerName", ownerName);
		}
		output.store("Progress", ProgressSnapshot.CODEC, progress);
		if (masterPos != null && structureSize != null) {
			output.putLong("PartOf", masterPos.asLong());
			output.putString("StructureSize", structureSize.id());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getString("OwnerName").orElse(null);
		progress = input.read("Progress", ProgressSnapshot.CODEC).orElse(ProgressSnapshot.EMPTY);
		masterPos = input.getLong("PartOf").map(BlockPos::of).orElse(null);
		structureSize = input.getString("StructureSize").map(JarSize::byId).orElse(null);
	}
}