package dev.completionist.jar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Data component on a Marble item: which advancement tab it came from. The tab's title is stored too, because the
 * tooltip is built in common code that cannot look up advancements.
 */
public record MarbleTab(Identifier tab, Component title) {
	public static final Codec<MarbleTab> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Identifier.CODEC.fieldOf("tab").forGetter(MarbleTab::tab),
		ComponentSerialization.CODEC.fieldOf("title").forGetter(MarbleTab::title)
	).apply(instance, MarbleTab::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, MarbleTab> STREAM_CODEC = StreamCodec.composite(
		Identifier.STREAM_CODEC, MarbleTab::tab,
		ComponentSerialization.STREAM_CODEC, MarbleTab::title,
		MarbleTab::new
	);

	public int color() {
		return MarbleColors.forTab(tab);
	}
}