package dev.completionist.jar.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.completionist.jar.CompletionistJar;
import dev.completionist.jar.JarSize;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Loads assets/completionist_jar/marble_layout/{small,medium,large}.json on every resource reload. */
public final class MarbleLayouts implements ResourceManagerReloadListener {
	private static final Map<JarSize, MarbleLayout> LAYOUTS = new HashMap<>();

	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		Map<JarSize, MarbleLayout> loaded = new HashMap<>();
		for (JarSize size : JarSize.values()) {
			Identifier file = Identifier.fromNamespaceAndPath(CompletionistJar.MODID, "marble_layout/" + size.id() + ".json");
			resourceManager.getResource(file).ifPresent(resource -> {
				try (BufferedReader reader = resource.openAsReader()) {
					JsonElement element = JsonParser.parseReader(reader);
					if (element.isJsonObject()) {
						loaded.put(size, MarbleLayout.fromJson((JsonObject) element));
					}
				} catch (Exception e) {
					CompletionistJar.LOGGER.error("Failed to load marble layout {}", file, e);
				}
			});
		}
		LAYOUTS.clear();
		LAYOUTS.putAll(loaded);
	}

	public static MarbleLayout get(JarSize size) {
		return LAYOUTS.getOrDefault(size, MarbleLayout.EMPTY);
	}

	public static MarbleLayout small() {
		return get(JarSize.SMALL);
	}
}