package dev.completionist.jar;

import java.util.List;

public enum JarSize {
	SMALL("small", 1, 1, 1, 100),
	MEDIUM("medium", 3, 3, 4, 300),
	LARGE("large", 6, 6, 8, 1000);

	public static final List<JarSize> STRUCTURE_SIZES = List.of(LARGE, MEDIUM);

	private final String id;
	private final int width;
	private final int depth;
	private final int height;
	private final int marbles;

	JarSize(String id, int width, int depth, int height, int marbles) {
		this.id = id;
		this.width = width;
		this.depth = depth;
		this.height = height;
		this.marbles = marbles;
	}

	public String id() {
		return id;
	}

	public int width() {
		return width;
	}

	public int depth() {
		return depth;
	}

	public int height() {
		return height;
	}

	public int marbles() {
		return marbles;
	}

	public static JarSize byId(String id) {
		for (JarSize size : values()) {
			if (size.id.equals(id)) {
				return size;
			}
		}
		return null;
	}
}