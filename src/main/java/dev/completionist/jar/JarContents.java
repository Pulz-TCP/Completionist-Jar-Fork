package dev.completionist.jar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The data component carried by a jar item: who owns it and the last known progress. */
public record JarContents(UUID owner, String ownerName, int completed, int total, List<ProgressSnapshot.Tab> tabs) {
	public static final Codec<JarContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		UUIDUtil.CODEC.fieldOf("owner").forGetter(JarContents::owner),
		Codec.STRING.fieldOf("owner_name").forGetter(JarContents::ownerName),
		Codec.INT.fieldOf("completed").forGetter(JarContents::completed),
		Codec.INT.fieldOf("total").forGetter(JarContents::total),
		Codec.list(ProgressSnapshot.Tab.CODEC).optionalFieldOf("tabs", List.of()).forGetter(JarContents::tabs)
	).apply(instance, JarContents::new));

	public static final StreamCodec<ByteBuf, JarContents> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, JarContents::owner,
		ByteBufCodecs.STRING_UTF8, JarContents::ownerName,
		ByteBufCodecs.VAR_INT, JarContents::completed,
		ByteBufCodecs.VAR_INT, JarContents::total,
		ProgressSnapshot.Tab.STREAM_CODEC.apply(ByteBufCodecs.list()), JarContents::tabs,
		JarContents::new
	);

	public static JarContents of(UUID owner, String ownerName, ProgressSnapshot snapshot) {
		return new JarContents(owner, ownerName, snapshot.completed(), snapshot.total(), snapshot.tabs());
	}

	public ProgressSnapshot snapshot() {
		return new ProgressSnapshot(completed, total, tabs);
	}
}