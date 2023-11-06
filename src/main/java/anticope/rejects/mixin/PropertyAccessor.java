package anticope.rejects.mixin;

import com.mojang.authlib.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.List;

@Mixin(Property.class)
public interface PropertyAccessor {
    @Accessor
    String getValue();
}
