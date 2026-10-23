package dev.completionist.jar;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ModComponents {
	public static final DataComponentType<JarContents> JAR_CONTENTS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Identifier.fromNamespaceAndPath(CompletionistJar.MODID, "jar_contents"),
		DataComponentType.<JarContents>builder().persistent(JarContents.CODEC).networkSynchronized(JarContents.STREAM_CODEC).build()
	);

	public static final DataComponentType<MarbleTab> MARBLE_TAB = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Identifier.fromNamespaceAndPath(CompletionistJar.MODID, "marble_tab"),
		DataComponentType.<MarbleTab>builder().persistent(MarbleTab.CODEC).networkSynchronized(MarbleTab.STREAM_CODEC).build()
	);

	private ModComponents() {
	}

	/** Forces class initialization so the components register during mod init. */
	public static void init() {
	}
}