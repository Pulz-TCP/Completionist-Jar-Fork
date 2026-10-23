package dev.completionist.jar;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

public final class ModAttachments {
	/** Per-player marble data. Persistent (saved with the player) and copied to the new body on respawn. */
	public static final AttachmentType<MarbleData> MARBLE_DATA = AttachmentRegistry.<MarbleData>create(
		Identifier.fromNamespaceAndPath(CompletionistJar.MODID, "marble_data"),
		builder -> builder.persistent(MarbleData.CODEC).copyOnDeath().initializer(MarbleData::new)
	);

	private ModAttachments() {
	}

	public static void init() {
	}
}