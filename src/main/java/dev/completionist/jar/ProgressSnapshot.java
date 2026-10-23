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
import net.minecraft.server.PlayerAdvancements;
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

	/** Counts every advancement that has a display (what the advancement screen shows), per root tab. */
	public static ProgressSnapshot compute(ServerPlayer owner) {
		ServerAdvancementManager manager = owner.level().getServer().getAdvancements();
		PlayerAdvancements advancements = owner.getAdvancements();
		Map<Identifier, int[]> perTab = new LinkedHashMap<>();
		int completed = 0;
		int total = 0;

		for (AdvancementNode node : manager.tree().nodes()) {
			AdvancementHolder holder = node.holder();
			if (holder.value().display().isEmpty()) {
				continue;
			}
			boolean done = advancements.getOrStartProgress(holder).isDone();
			total++;
			if (done) {
				completed++;
			}
			AdvancementNode root = node.root();
			if (root.holder().value().display().isPresent()) {
				int[] counts = perTab.computeIfAbsent(root.holder().id(), id -> new int[2]);
				counts[1]++;
				if (done) {
					counts[0]++;
				}
			}
		}

		List<Tab> tabList = new ArrayList<>(perTab.size());
		perTab.forEach((id, counts) -> tabList.add(new Tab(id, counts[0], counts[1])));
		return new ProgressSnapshot(completed, total, tabList);
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