package dev.completionist.jar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import dev.completionist.jar.JarContents;
import dev.completionist.jar.ModComponents;
import dev.completionist.jar.ProgressSnapshot;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Draws a jar item (in hand, in the inventory, on the ground) with its marble fill, as a special item model. The item
 * definition (items/advancement_jar.json) points at this renderer by the id registered in CompletionistJarClient.
 */
public final class JarItemRenderer implements SpecialModelRenderer<JarItemRenderer.Fill> {
	/** What the renderer needs from the stack. A record, so the game redraws the item exactly when it changes. */
	public record Fill(int percent, boolean completed) {
	}

	@Override
	public @Nullable Fill extractArgument(ItemStack stack) {
		JarContents contents = stack.get(ModComponents.JAR_CONTENTS);
		if (contents == null) {
			return new Fill(0, false);
		}
		return new Fill(ProgressSnapshot.percent(contents.completed(), contents.total()), contents.total() > 0 && contents.completed() == contents.total());
	}

	@Override
	public void submit(@Nullable Fill fill, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
		MarbleLayout layout = MarbleLayouts.small();
		if (layout == MarbleLayout.EMPTY) {
			return;
		}
		Fill shown = fill == null ? new Fill(0, false) : fill;
		poseStack.pushPose();
		poseStack.scale(0.0625F, 0.0625F, 0.0625F);
		JarRenderer.drawItemJar(poseStack, collector, layout, shown.percent(), lightCoords, shown.completed());
		poseStack.popPose();
	}

	/** The jar's bounds in block units (the glass box), used by the game to size the item in GUIs. */
	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(0.125F, 0.0F, 0.125F));
		output.accept(new Vector3f(0.875F, 1.0F, 0.875F));
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<Fill> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<Fill> bake(SpecialModelRenderer.BakingContext context) {
			return new JarItemRenderer();
		}
	}
}