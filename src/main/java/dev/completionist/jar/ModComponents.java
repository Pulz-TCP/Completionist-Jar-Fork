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

	private ModComponents() {
	}

	/** Forces class initialization so the component registers during mod init. */
	public static void init() {
	}
}