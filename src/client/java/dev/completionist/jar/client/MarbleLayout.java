package dev.completionist.jar.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.GsonHelper;

/** Where each marble sits inside a jar of one size, plus the jar's glass / rim / lid boxes (in 1/16 block units). */
public record MarbleLayout(int count, List<Marble> marbles, JarBox jar) {
	public static final MarbleLayout EMPTY = new MarbleLayout(0, List.of(), JarBox.EMPTY);

	public static MarbleLayout fromJson(JsonObject root) {
		int count = GsonHelper.getAsInt(root, "count");
		JsonArray array = GsonHelper.getAsJsonArray(root, "slots");
		List<Marble> marbles = new ArrayList<>(array.size());
		for (int i = 0; i < array.size(); i++) {
			JsonObject slot = array.get(i).getAsJsonObject();
			String color = GsonHelper.getAsString(slot, "color");
			marbles.add(new Marble(
				GsonHelper.getAsFloat(slot, "x"),
				GsonHelper.getAsFloat(slot, "y"),
				GsonHelper.getAsFloat(slot, "z"),
				GsonHelper.getAsFloat(slot, "size"),
				0xFF000000 | Integer.parseUnsignedInt(color.substring(1), 16)
			));
		}
		return new MarbleLayout(count, List.copyOf(marbles), JarBox.fromJson(GsonHelper.getAsJsonObject(root, "jar")));
	}

	private static float[] floats(JsonArray array) {
		return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
	}

	public record Box3(float x0, float y0, float z0, float x1, float y1, float z1) {
		private static final Box3 ZERO = new Box3(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);

		static Box3 fromJson(JsonObject object) {
			float[] from = floats(GsonHelper.getAsJsonArray(object, "from"));
			float[] to = floats(GsonHelper.getAsJsonArray(object, "to"));
			return new Box3(from[0], from[1], from[2], to[0], to[1], to[2]);
		}
	}

	public record JarBox(Box3 glass, Box3 rim, Box3 lid) {
		public static final JarBox EMPTY = new JarBox(Box3.ZERO, Box3.ZERO, Box3.ZERO);

		static JarBox fromJson(JsonObject object) {
			return new JarBox(
				Box3.fromJson(GsonHelper.getAsJsonObject(object, "glass")),
				Box3.fromJson(GsonHelper.getAsJsonObject(object, "rim")),
				Box3.fromJson(GsonHelper.getAsJsonObject(object, "lid"))
			);
		}
	}

	public record Marble(float x, float y, float z, float size, int argb) {
	}
}