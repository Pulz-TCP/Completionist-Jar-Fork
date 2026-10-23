package dev.completionist.jar;

import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlocks {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(CompletionistJar.MODID, "advancement_jar");

	public static final AdvancementJarBlock ADVANCEMENT_JAR = Registry.register(
		BuiltInRegistries.BLOCK, ID,
		new AdvancementJarBlock(AdvancementJarBlock.jarProperties().setId(ResourceKey.create(Registries.BLOCK, ID)))
	);

	public static final JarItem ADVANCEMENT_JAR_ITEM = Registry.register(
		BuiltInRegistries.ITEM, ID,
		new JarItem(ADVANCEMENT_JAR, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID)).useBlockDescriptionPrefix())
	);

	public static final BlockEntityType<AdvancementJarBlockEntity> ADVANCEMENT_JAR_BE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, ID,
		new BlockEntityType<>(AdvancementJarBlockEntity::new, Set.<Block>of(ADVANCEMENT_JAR))
	);

	private ModBlocks() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(ADVANCEMENT_JAR_ITEM));
	}
}