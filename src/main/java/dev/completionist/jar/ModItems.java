package dev.completionist.jar;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final MarbleItem MARBLE = register("marble", key -> new MarbleItem(new Item.Properties().setId(key)));
	public static final PouchItem POUCH = register("marble_pouch", key -> new PouchItem(new Item.Properties().setId(key).stacksTo(1)));

	private ModItems() {
	}

	private static <T extends Item> T register(String name, Function<ResourceKey<Item>, T> factory) {
		Identifier id = Identifier.fromNamespaceAndPath(CompletionistJar.MODID, name);
		return Registry.register(BuiltInRegistries.ITEM, id, factory.apply(ResourceKey.create(Registries.ITEM, id)));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(POUCH));
	}
}