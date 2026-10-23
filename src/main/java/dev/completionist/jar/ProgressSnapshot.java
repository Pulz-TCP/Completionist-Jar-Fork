package dev.completionist.jar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/** Immutable count of completed / total advancements, overall and per advancement tab. */
public record ProgressSnapshot(int completed, int total, List<Tab> tabs) {
	public static final ProgressSnapshot EMPTY = new ProgressSnapshot(0, 0, List.of());

	public static final Codec<ProgressSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("completed").forGetter(ProgressSnapshot::completed),
		Codec.INT.fieldOf("total").forGetter(ProgressSnapshot::total),
		Codec.list(Tab.CODEC).optionalFieldOf("tabs", List.of()).forGetter(ProgressSnapshot::tabs)
	).apply(instance, ProgressSnapshot::new));

	public ProgressSnapshot {
		tabs = List.copyOf(tabs);
	}

	public int percent() {
		return percent(completed, total);
	}

	public static int percent(long completed, long total) {
		return total <= 0L ? 0 : (int) Math.floor(completed * 100.0 / total);
	}

	public static int filledMarbles(long completed, long total, int capacity) {
		return (int) Math.floor((double) percent(completed, total) * capacity / 100.0);
	}

	public int filledMarbles(int capacity) {
		return filledMarbles(completed, total, capacity);
	}

	public boolean isComplete() {
		return total > 0 && completed == total;
	}

	/**
	 * What a jar shows: marbles the owner has deposited out of every advancement that has a display (what the
	 * advancement screen shows), overall and per root tab. Deposits come from the owner's MarbleData ledger, not from
	 * their advancement progress, so progress only appears once its marble is dropped into a jar.
	 */
	public static ProgressSnapshot compute(ServerPlayer owner) {
		MarbleData ledger = MarbleData.get(owner);
		ServerAdvancementManager manager = owner.level().getServer().getAdvancements();
		Map<Identifier, int[]> perTab = new LinkedHashMap<>();
		int total = 0;

		for (AdvancementNode node : manager.tree().nodes()) {
			AdvancementHolder holder = node.holder();
			if (holder.value().display().isEmpty()) {
				continue;
			}
			total++;
			AdvancementNode root = node.root();
			if (root.holder().value().display().isPresent()) {
				perTab.computeIfAbsent(root.holder().id(), id -> new int[2])[1]++;
			}
		}

		int completed = 0;
		List<Tab> tabList = new ArrayList<>(perTab.size());
		for (Map.Entry<Identifier, int[]> entry : perTab.entrySet()) {
			int deposited = Math.min(ledger.deposited().getOrDefault(entry.getKey(), 0), entry.getValue()[1]);
			completed += deposited;
			tabList.add(new Tab(entry.getKey(), deposited, entry.getValue()[1]));
		}
		return new ProgressSnapshot(Math.min(completed, total), total, tabList);
	}

	public record Tab(Identifier id, int completed, int total) {
		public static final Codec<Tab> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("id").forGetter(Tab::id),
			Codec.INT.fieldOf("completed").forGetter(Tab::completed),
			Codec.INT.fieldOf("total").forGetter(Tab::total)
		).apply(instance, Tab::new));

		public static final StreamCodec<ByteBuf, Tab> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, Tab::id,
			ByteBufCodecs.VAR_INT, Tab::completed,
			ByteBufCodecs.VAR_INT, Tab::total,
			Tab::new
		);
	}
}