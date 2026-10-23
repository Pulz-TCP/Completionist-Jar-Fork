package dev.completionist.jar;

import java.util.Map;
import net.minecraft.resources.Identifier;

/** Gives every advancement tab a stable marble colour (RGB, no alpha). */
public final class MarbleColors {
	private static final Map<String, Integer> KNOWN = Map.of(
		"minecraft:story/root", 0x5BBF4A,
		"minecraft:nether/root", 0xD9453B,
		"minecraft:end/root", 0xB06BE0,
		"minecraft:adventure/root", 0xE8943A,
		"minecraft:husbandry/root", 0xE9D24A
	);

	private MarbleColors() {
	}

	/** Vanilla tabs get hand-picked colours; any other tab (e.g. from a mod) gets a colour derived from its id. */
	public static int forTab(Identifier tab) {
		Integer known = KNOWN.get(tab.toString());
		if (known != null) {
			return known;
		}
		int hash = tab.toString().hashCode();
		float hue = ((hash & 0xFFFF) % 360) / 360.0F;
		return hsvToRgb(hue, 0.6F, 0.9F);
	}

	private static int hsvToRgb(float h, float s, float v) {
		float c = v * s;
		float x = c * (1 - Math.abs((h * 6) % 2 - 1));
		float m = v - c;
		float r;
		float g;
		float b;
		int sector = (int) (h * 6) % 6;
		switch (sector) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
	}
}