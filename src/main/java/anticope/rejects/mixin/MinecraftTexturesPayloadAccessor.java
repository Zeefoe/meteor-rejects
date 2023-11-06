package anticope.rejects.mixin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.List;

@Mixin(MinecraftTexturesPayload.class)
public interface MinecraftTexturesPayloadAccessor {
    @Accessor
    Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures();
}
