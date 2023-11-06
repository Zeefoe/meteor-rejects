package anticope.rejects.mixin;

import anticope.rejects.events.PlayerRespawnEvent;
import anticope.rejects.modules.SilentDisconnect;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientCommonNetworkHandlerMixin {
    @Inject(method = "onDisconnected", at = @At("HEAD"), cancellable = true)
    private void onDisconnected(Text reason, CallbackInfo info) {
        if (Modules.get().isActive(SilentDisconnect.class) && mc.world != null && mc.player != null) {
            ChatUtils.info(Text.translatable("disconnect.lost").getString() + ":");
            ChatUtils.sendMsg(reason);
            info.cancel();
        }
    }
}
