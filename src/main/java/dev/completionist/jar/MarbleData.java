package dev.completionist.jar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Everything the mod remembers about one player, saved with their player data (a Fabric data attachment, so it
 * lives in the world the player is in and follows them through death).
 *
 * <ul>
 *   <li><b>pouch</b>: the marbles waiting to be deposited (the contents of the Marble Pouch screen).</li>
 *   <li><b>awarded</b>: advancements that have already paid out a marble, so nothing is ever paid twice.</li>
 *   <li><b>pending</b>: marbles that did not fit in a full pouch; they move in as space frees up.</li>
 *   <li><b>deposited</b>: marbles dropped into jars, per tab. This ledger is what every jar displays.</li>
 * </ul>
 */
public final class MarbleData {
	public static final int POUCH_SIZE = 27;

	public static final Codec<MarbleData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("pouch", List.of()).forGetter(MarbleData::pouchAsList),
		Identifier.CODEC.listOf().optionalFieldOf("awarded", List.of()).forGetter(d -> List.copyOf(d.awarded)),
		Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("deposited", Map.of()).forGetter(d -> d.deposited),
		Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("pending", Map.of()).forGetter(d -> d.pending),
		Codec.BOOL.optionalFieldOf("pouch_granted", false).forGetter(d -> d.pouchGranted)
	).apply(instance, MarbleData::new));

	private final NonNullList<ItemStack> pouch = NonNullList.withSize(POUCH_SIZE, ItemStack.EMPTY);
	private final Set<Identifier> awarded = new HashSet<>();
	private final Map<Identifier, Integer> deposited = new LinkedHashMap<>();
	private final Map<Identifier, Integer> pending = new LinkedHashMap<>();
	private boolean pouchGranted;

	public MarbleData() {
	}

	private MarbleData(List<ItemStack> pouch, List<Identifier> awarded, Map<Identifier, Integer> deposited, Map<Identifier, Integer> pending, boolean pouchGranted) {
		for (int i = 0; i < Math.min(pouch.size(), POUCH_SIZE); i++) {
			this.pouch.set(i, pouch.get(i));
		}
		this.awarded.addAll(awarded);
		this.deposited.putAll(deposited);
		this.pending.putAll(pending);
		this.pouchGranted = pouchGranted;
	}

	public static MarbleData get(Player player) {
		return player.getAttachedOrCreate(ModAttachments.MARBLE_DATA);
	}

	private List<ItemStack> pouchAsList() {
		return new ArrayList<>(pouch);
	}

	/** The live list behind the pouch screen. The menu edits this directly, so marbles awarded while it is open show up. */
	public NonNullList<ItemStack> pouch() {
		return pouch;
	}

	public boolean pouchGranted() {
		return pouchGranted;
	}

	public void setPouchGranted(boolean granted) {
		this.pouchGranted = granted;
	}

	public boolean markAwarded(Identifier advancement) {
		return awarded.add(advancement);
	}

	public Map<Identifier, Integer> deposited() {
		return deposited;
	}

	public int depositedTotal() {
		int sum = 0;
		for (int count : deposited.values()) {
			sum += count;
		}
		return sum;
	}

	/** Puts as many of the stack as fit into the pouch (merging first), and returns how many were left over. */
	public int insert(ItemStack stack) {
		int remaining = stack.getCount();
		for (ItemStack existing : pouch) {
			if (remaining > 0 && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
				int moved = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
				existing.grow(moved);
				remaining -= moved;
			}
		}
		for (int i = 0; i < pouch.size() && remaining > 0; i++) {
			if (pouch.get(i).isEmpty()) {
				int moved = Math.min(remaining, stack.getMaxStackSize());
				pouch.set(i, stack.copyWithCount(moved));
				remaining -= moved;
			}
		}
		return remaining;
	}

	/** Adds marbles for a tab to the pouch; anything that does not fit is remembered as pending. */
	public void addMarbles(MarbleTab tab, int count) {
		int left = insert(MarbleItem.create(tab, count));
		if (left > 0) {
			pending.merge(tab.tab(), left, Integer::sum);
		}
	}

	/** Moves pending marbles into the pouch if there is room now. titleOf resolves a tab's display title. */
	public void flushPending(Function<Identifier, Component> titleOf) {
		if (pending.isEmpty()) {
			return;
		}
		Map<Identifier, Integer> snapshot = new HashMap<>(pending);
		pending.clear();
		snapshot.forEach((tab, count) -> addMarbles(new MarbleTab(tab, titleOf.apply(tab)), count));
	}
}