package net.fabricmc.example.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Level.class)
public class ExtendWorldHeight {

	/**
	 * @author CorgiTaco
	 */
	@Overwrite
	public int getSectionsCount() {
		return 64;
	}

	/**
	 * @author CorgiTaco
	 */
	@Overwrite
	public int getMinSection() {
		return -16;
	}
}