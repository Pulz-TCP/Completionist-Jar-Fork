package dev.completionist.jar;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent by a client that is looking at a jar tooltip, asking the server to refresh jar items it carries. */
public record RefreshJarItemsPayload() implements CustomPacketPayload {
	public static final RefreshJarItemsPayload INSTANCE = new RefreshJarItemsPayload();
	public static final Type<RefreshJarItemsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CompletionistJar.MODID, "refresh_jar_items"));
	public static final StreamCodec<ByteBuf, RefreshJarItemsPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<RefreshJarItemsPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> JarItemRefresh.refreshCarried(context.player()));
	}
}