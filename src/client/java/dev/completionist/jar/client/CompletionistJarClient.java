package dev.completionist.jar.client;

import dev.completionist.jar.CompletionistJar;
import dev.completionist.jar.JarContents;
import dev.completionist.jar.JarItem;
import dev.completionist.jar.JarSize;
import dev.completionist.jar.ModBlocks;
import dev.completionist.jar.ModComponents;
import dev.completionist.jar.ProgressSnapshot;
import dev.completionist.jar.RefreshJarItemsPayload;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

public class CompletionistJarClient implements ClientModInitializer {
	private static long lastRefreshRequest;

	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlocks.ADVANCEMENT_JAR_BE, JarRenderer::new);
		// Lets items/advancement_jar.json say {"type": "completionist_jar:jar"} to draw the jar with its fill.
		SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath(CompletionistJar.MODID, "jar"), JarItemRenderer.Unbaked.MAP_CODEC);
		ResourceLoader.get(PackType.CLIENT_RESOURCES)
			.registerReloadListener(Identifier.fromNamespaceAndPath(CompletionistJar.MODID, "marble_layouts"), new MarbleLayouts());
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> onTooltip(stack, lines));
	}

	private static void onTooltip(ItemStack stack, List<Component> lines) {
		if (!(stack.getItem() instanceof JarItem)) {
			return;
		}
		JarContents contents = stack.get(ModComponents.JAR_CONTENTS);
		if (contents == null) {
			lines.add(Component.translatable("advancement_jar.tooltip.unowned"));
		} else {
			lines.add(Component.translatable("advancement_jar.tooltip.owner", contents.ownerName()));
			lines.add(Component.translatable("advancement_jar.tooltip.progress", contents.completed(), contents.total(),
				ProgressSnapshot.percent(contents.completed(), contents.total())));
			requestRefresh();
		}

		if (Minecraft.getInstance().hasShiftDown()) {
			lines.add(Component.translatable("advancement_jar.tooltip.sizes").withStyle(ChatFormatting.GOLD));
			for (JarSize size : JarSize.values()) {
				lines.add(Component.translatable("advancement_jar.tooltip.size." + size.id(),
					size.width(), size.depth(), size.height(), jarsNeeded(size), size.marbles()).withStyle(ChatFormatting.GRAY));
			}
			lines.add(Component.translatable("advancement_jar.tooltip.building").withStyle(ChatFormatting.GOLD));
			for (String line : List.of("layer", "side", "sneak", "report")) {
				lines.add(Component.translatable("advancement_jar.tooltip.building." + line).withStyle(ChatFormatting.GRAY));
			}
		} else {
			lines.add(Component.translatable("advancement_jar.tooltip.more").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static int jarsNeeded(JarSize size) {
		int all = size.width() * size.depth() * size.height();
		int inside = Math.max(0, size.width() - 2) * Math.max(0, size.depth() - 2) * Math.max(0, size.height() - 2);
		return all - inside;
	}

	/** Hovering a jar asks the server for fresh progress, at most every two seconds. */
	private static void requestRefresh() {
		long now = Util.getMillis();
		if (now - lastRefreshRequest >= 2000L && Minecraft.getInstance().getConnection() != null) {
			lastRefreshRequest = now;
			ClientPlayNetworking.send(RefreshJarItemsPayload.INSTANCE);
		}
	}
}