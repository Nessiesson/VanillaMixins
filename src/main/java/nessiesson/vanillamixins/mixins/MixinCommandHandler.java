package nessiesson.vanillamixins.mixins;

import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandHandler.class)
public abstract class MixinCommandHandler {
	@Inject(method = "registerCommand", at = @At("HEAD"))
	private void cancer(ICommand command, CallbackInfoReturnable<ICommand> cir) {
		System.out.println(command);
	}
}
