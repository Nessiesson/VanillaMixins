package nessiesson.vanillamixins.mixins;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {
	@Shadow
	public abstract void logInfo(String s);

	@Inject(method = "run", at = @At("HEAD"))
	private void helloWorld(CallbackInfo ci) {
		this.logInfo("Hello, world!");
	}
}
