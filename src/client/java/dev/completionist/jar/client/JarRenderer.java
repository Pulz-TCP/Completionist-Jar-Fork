package dev.completionist.jar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.completionist.jar.AdvancementJarBlockEntity;
import dev.completionist.jar.CompletionistJar;
import dev.completionist.jar.JarSize;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.data.AtlasIds;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the jar body and its marbles. Everything is emitted as custom geometry in 1/16-block units, using the
 * block atlas so the textures are plain block textures.
 */
public class JarRenderer implements BlockEntityRenderer<AdvancementJarBlockEntity, JarRenderer.State> {
	private static final Identifier MARBLE = texture("block/marble");
	private static final Identifier MARBLE_FILL = texture("block/marble_fill");
	private static final Identifier GLASS = texture("block/jar_glass");
	private static final Identifier LID_SIDE = texture("block/jar_lid_side");
	private static final Identifier LID_TOP = texture("block/jar_lid_top");
	private static final Identifier RIM = texture("block/jar_rim_band");
	private static final Identifier LID_SIDE_COMPLETED = texture("block/jar_lid_side_completed");
	private static final Identifier LID_TOP_COMPLETED = texture("block/jar_lid_top_completed");
	private static final Identifier RIM_COMPLETED = texture("block/jar_rim_band_completed");

	public JarRenderer(BlockEntityRendererProvider.Context context) {
	}

	public static class State extends BlockEntityRenderState {
		boolean visible;
		boolean completed;
		MarbleLayout layout = MarbleLayout.EMPTY;
		int filled;
		Vec3 offset = Vec3.ZERO;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(
		AdvancementJarBlockEntity be, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		state.visible = !be.isPart() || be.isMaster();
		if (!state.visible) {
			return;
		}
		JarSize size = be.isMaster() && be.getStructureSize() != null ? be.getStructureSize() : JarSize.SMALL;
		state.layout = MarbleLayouts.get(size);
		var progress = be.getProgress();
		state.filled = progress.filledMarbles(state.layout.count());
		state.completed = progress.isComplete();
	}

	// Large structures are drawn only by their master, which may be far from the corner the player is looking at.
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.visible || state.layout == MarbleLayout.EMPTY) {
			return;
		}
		poseStack.pushPose();
		poseStack.scale(0.0625F, 0.0625F, 0.0625F);
		drawJar(poseStack, collector, camera.orientation, state.layout, state.filled, state.lightCoords, state.completed);
		poseStack.popPose();
	}

	static void drawJar(PoseStack poseStack, SubmitNodeCollector collector, Quaternionf cameraOrientation, MarbleLayout layout, int filled, int light, boolean completed) {
		Vector3f right = cameraOrientation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
		Vector3f up = cameraOrientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
		int count = Mth.clamp(filled, 0, layout.count());
		collector.submitCustomGeometry(poseStack, cutoutType(), (pose, vc) -> {
			TextureAtlasSprite marble = sprite(MARBLE);
			List<MarbleLayout.Marble> marbles = layout.marbles();
			for (int i = 0; i < count && i < marbles.size(); i++) {
				marble(vc, pose, marbles.get(i), right, up, marble, light);
			}
			solidShell(vc, pose, layout, light, completed);
		});
		collector.submitCustomGeometry(poseStack, translucentType(), (pose, vc) -> glass(vc, pose, layout, light));
	}

	static RenderType cutoutType() {
		return RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
	}

	static RenderType translucentType() {
		return RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
	}

	private static void solidShell(VertexConsumer vc, PoseStack.Pose pose, MarbleLayout layout, int light, boolean completed) {
		TextureAtlasSprite lidSide = sprite(completed ? LID_SIDE_COMPLETED : LID_SIDE);
		TextureAtlasSprite lidTop = sprite(completed ? LID_TOP_COMPLETED : LID_TOP);
		TextureAtlasSprite rim = sprite(completed ? RIM_COMPLETED : RIM);
		MarbleLayout.Box3 r = layout.jar().rim();
		box(vc, pose, r.x0(), r.y0(), r.z0(), r.x1(), r.y1(), r.z1(), rim, rim, rim, light);
		MarbleLayout.Box3 l = layout.jar().lid();
		box(vc, pose, l.x0(), l.y0(), l.z0(), l.x1(), l.y1(), l.z1(), lidSide, lidTop, lidSide, light);
	}

	private static void glass(VertexConsumer vc, PoseStack.Pose pose, MarbleLayout layout, int light) {
		TextureAtlasSprite glass = sprite(GLASS);
		MarbleLayout.Box3 g = layout.jar().glass();
		box(vc, pose, g.x0(), g.y0(), g.z0(), g.x1(), g.y1(), g.z1(), glass, glass, null, light);
	}

	/** Fill level for the held / dropped item: a flat slab of marbles rising with the percentage. */
	static void fillSlab(VertexConsumer vc, PoseStack.Pose pose, MarbleLayout layout, int percent, int light) {
		MarbleLayout.Box3 glass = layout.jar().glass();
		float inset = 0.02F * (glass.x1() - glass.x0());
		float top = glass.y0() + (layout.jar().rim().y0() - glass.y0()) * Mth.clamp(percent, 0, 100) / 100.0F;
		if (top <= glass.y0()) {
			return;
		}
		TextureAtlasSprite sprite = sprite(MARBLE_FILL);
		float x0 = glass.x0() + inset;
		float z0 = glass.z0() + inset;
		float x1 = glass.x1() - inset;
		float z1 = glass.z1() - inset;
		float y0 = glass.y0();
		float v = Math.min(1.0F, (top - y0) / (x1 - x0));
		quad(vc, pose, sprite, light, 255, 255, 255, 1, 0, 0, x1, y0, z0, 0, 0, x1, top, z0, 0, v, x1, top, z1, 1, v, x1, y0, z1, 1, 0);
		quad(vc, pose, sprite, light, 255, 255, 255, -1, 0, 0, x0, y0, z1, 1, 0, x0, top, z1, 1, v, x0, top, z0, 0, v, x0, y0, z0, 0, 0);
		quad(vc, pose, sprite, light, 255, 255, 255, 0, 1, 0, x0, top, z0, 0, 0, x0, top, z1, 0, 1, x1, top, z1, 1, 1, x1, top, z0, 1, 0);
		quad(vc, pose, sprite, light, 255, 255, 255, 0, 0, 1, x1, y0, z1, 1, 0, x1, top, z1, 1, v, x0, top, z1, 0, v, x0, y0, z1, 0, 0);
		quad(vc, pose, sprite, light, 255, 255, 255, 0, 0, -1, x0, y0, z0, 0, 0, x0, top, z0, 0, v, x1, top, z0, 1, v, x1, y0, z0, 1, 0);
	}

	static void drawItemJar(PoseStack poseStack, SubmitNodeCollector collector, MarbleLayout layout, int percent, int light, boolean completed) {
		collector.submitCustomGeometry(poseStack, cutoutType(), (pose, vc) -> {
			fillSlab(vc, pose, layout, percent, light);
			solidShell(vc, pose, layout, light, completed);
		});
		collector.submitCustomGeometry(poseStack, translucentType(), (pose, vc) -> glass(vc, pose, layout, light));
	}

	static TextureAtlasSprite sprite(Identifier texture) {
		return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture);
	}

	private static Identifier texture(String path) {
		return Identifier.fromNamespaceAndPath(CompletionistJar.MODID, path);
	}

	private static void marble(VertexConsumer vc, PoseStack.Pose pose, MarbleLayout.Marble m, Vector3f right, Vector3f up, TextureAtlasSprite sprite, int light) {
		float h = m.size() / 2.0F;
		float rx = right.x() * h;
		float ry = right.y() * h;
		float rz = right.z() * h;
		float ux = up.x() * h;
		float uy = up.y() * h;
		float uz = up.z() * h;
		int r = m.argb() >> 16 & 0xFF;
		int g = m.argb() >> 8 & 0xFF;
		int b = m.argb() & 0xFF;
		quad(vc, pose, sprite, light, r, g, b, 0, 1, 0,
			m.x() + rx - ux, m.y() + ry - uy, m.z() + rz - uz, 1, 1,
			m.x() + rx + ux, m.y() + ry + uy, m.z() + rz + uz, 1, 0,
			m.x() - rx + ux, m.y() - ry + uy, m.z() - rz + uz, 0, 0,
			m.x() - rx - ux, m.y() - ry - uy, m.z() - rz - uz, 0, 1);
	}

	private static void box(
		VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
		TextureAtlasSprite side, TextureAtlasSprite top, @Nullable TextureAtlasSprite bottom, int light
	) {
		quad(vc, pose, side, light, 255, 255, 255, 1, 0, 0, x1, y0, z0, 0, 0, x1, y1, z0, 0, 1, x1, y1, z1, 1, 1, x1, y0, z1, 1, 0);
		quad(vc, pose, side, light, 255, 255, 255, -1, 0, 0, x0, y0, z1, 1, 0, x0, y1, z1, 1, 1, x0, y1, z0, 0, 1, x0, y0, z0, 0, 0);
		quad(vc, pose, top, light, 255, 255, 255, 0, 1, 0, x0, y1, z0, 0, 0, x0, y1, z1, 0, 1, x1, y1, z1, 1, 1, x1, y1, z0, 1, 0);
		if (bottom != null) {
			quad(vc, pose, bottom, light, 255, 255, 255, 0, -1, 0, x0, y0, z1, 0, 0, x0, y0, z0, 0, 1, x1, y0, z0, 1, 1, x1, y0, z1, 1, 0);
		}
		quad(vc, pose, side, light, 255, 255, 255, 0, 0, 1, x1, y0, z1, 1, 0, x1, y1, z1, 1, 1, x0, y1, z1, 0, 1, x0, y0, z1, 0, 0);
		quad(vc, pose, side, light, 255, 255, 255, 0, 0, -1, x0, y0, z0, 0, 0, x0, y1, z0, 0, 1, x1, y1, z0, 1, 1, x1, y0, z0, 1, 0);
	}

	/** One quad: four (x, y, z, u, v) vertices with a shared colour and normal. */
	private static void quad(
		VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, int r, int g, int b, float nx, float ny, float nz,
		float x0, float y0, float z0, float u0, float v0,
		float x1, float y1, float z1, float u1, float v1,
		float x2, float y2, float z2, float u2, float v2,
		float x3, float y3, float z3, float u3, float v3
	) {
		vertex(vc, pose, sprite, light, r, g, b, nx, ny, nz, x0, y0, z0, u0, v0);
		vertex(vc, pose, sprite, light, r, g, b, nx, ny, nz, x1, y1, z1, u1, v1);
		vertex(vc, pose, sprite, light, r, g, b, nx, ny, nz, x2, y2, z2, u2, v2);
		vertex(vc, pose, sprite, light, r, g, b, nx, ny, nz, x3, y3, z3, u3, v3);
	}

	private static void vertex(
		VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, int r, int g, int b, float nx, float ny, float nz,
		float x, float y, float z, float u, float v
	) {
		vc.addVertex(pose, x, y, z)
			.setColor(r, g, b, 255)
			.setUv(sprite.getU(u), sprite.getV(v))
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, nx, ny, nz);
	}
}