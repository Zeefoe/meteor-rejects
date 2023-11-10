// Credit to https://github.com/IAFEnvoy/AccountSwitcher

package anticope.rejects.utils.accounts;

import anticope.rejects.MeteorRejectsAddon;
import anticope.rejects.mixin.MinecraftTexturesPayloadAccessor;
import anticope.rejects.mixin.PropertyAccessor;
import com.google.common.collect.Iterables;
import com.google.gson.*;
import com.mojang.authlib.Environment;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.InsecurePublicKeyException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import com.mojang.util.UUIDTypeAdapter;
import meteordevelopment.meteorclient.utils.network.Http;
import net.minecraft.client.session.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class CustomYggdrasilLogin {
    public static Session login(String name, String password, String server) throws AuthenticationException {
        try {
            String url = server + "/authserver/authenticate";
            JsonObject agent = new JsonObject();
            agent.addProperty("name", "Minecraft");
            agent.addProperty("version", 1);

            JsonObject root = new JsonObject();
            root.add("agent", agent);
            root.addProperty("username", name);
            root.addProperty("password", password);

            String data = Http.post(url).bodyJson(root).sendString();
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            if (json.has("error")) {
                throw new AuthenticationException(json.get("errorMessage").getAsString());
            }
            String token = json.get("accessToken").getAsString();
            UUID uuid = UUID.fromString(json.get("selectedProfile").getAsJsonObject().get("id").getAsString());
            String username = json.get("selectedProfile").getAsJsonObject().get("name").getAsString();
            return new Session(username, uuid, token, Optional.empty(), Optional.empty(), Session.AccountType.MOJANG);
        } catch (Exception e) {
            throw new AuthenticationException(e);
        }
    }

    public static class LocalYggdrasilApi {
        private final String url;

        public LocalYggdrasilApi(String serverUrl) {
            MeteorRejectsAddon.LOG.info("LocalYggrasilApi({}) called!", serverUrl);
            this.url = serverUrl;
        }

        public String authHost() {
            MeteorRejectsAddon.LOG.info("LocalYggdrasilApi.authHost() called!");
            return url + "/authserver";
        }

        public String accountsHost() {
            MeteorRejectsAddon.LOG.info("LocalYggdrasilApi.accountsHost() called!");
            return url + "/api";
        }

        public String sessionHost() {
            MeteorRejectsAddon.LOG.info("LocalYggdrasilApi.sessionHost() called!");
            return url + "/sessionserver";
        }

        public String servicesHost() {
            MeteorRejectsAddon.LOG.info("LocalYggdrasilApi.servicesHost() called!");
            return url + "/minecraftservices";
        }

        public String name() {
            MeteorRejectsAddon.LOG.info("LocalYggdrasilApi.name() called!");
            return "Custom-Yggdrasil";
        }

        public String toString() {
            return new StringJoiner(", ", "", "")
                    .add("authHost='" + authHost() + "'")
                    .add("accountsHost='" + accountsHost() + "'")
                    .add("sessionHost='" + sessionHost() + "'")
                    .add("servicesHost='" + servicesHost() + "'")
                    .add("name='" + name() + "'")
                    .toString();
        }
    }

    public static class LocalYggdrasilMinecraftSessionService extends YggdrasilMinecraftSessionService {
        private static final Logger LOGGER = LogManager.getLogger();
        private final PublicKey publicKey;
        private final Gson gson = new GsonBuilder().registerTypeAdapter(UUID.class, new UUIDTypeAdapter()).create();

        public LocalYggdrasilMinecraftSessionService(YggdrasilAuthenticationService service, Proxy proxy, String serverUrl) {
            super(service.getServicesKeySet(), proxy, (Environment) (Object) new LocalYggdrasilApi(serverUrl));
            String data = Http.get(serverUrl).sendString();
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            this.publicKey = getPublicKey(json.get("signaturePublickey").getAsString());
        }

        private static PublicKey getPublicKey(String key) {
            key = key.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
            try {
                byte[] byteKey = Base64.getDecoder().decode(key.replace("\n", ""));
                X509EncodedKeySpec spec = new X509EncodedKeySpec(byteKey);
                KeyFactory factory = KeyFactory.getInstance("RSA");
                return factory.generatePublic(spec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures(final GameProfile profile, final boolean requireSecure) {
            final Property textureProperty = Iterables.getFirst(profile.getProperties().get("textures"), null);

            if (textureProperty == null)
                return new HashMap<>();

            if (requireSecure) {
                if (!textureProperty.hasSignature()) {
                    LOGGER.error("Signature is missing from textures payload");
                    throw new InsecurePublicKeyException("Signature is missing from textures payload");
                }
                if (!textureProperty.isSignatureValid(publicKey)) {
                    LOGGER.error("Textures payload has been tampered with (signature invalid)");
                    throw new InsecurePublicKeyException("Textures payload has been tampered with (signature invalid)");
                }
            }

            final MinecraftTexturesPayload result;
            try {
                // TODO: replace with built-in base64 lib
                final String json = new String(org.apache.commons.codec.binary.Base64.decodeBase64(((PropertyAccessor) (Object) textureProperty).getValue()), StandardCharsets.UTF_8);
                result = gson.fromJson(json, MinecraftTexturesPayload.class);
            } catch (final JsonParseException e) {
                LOGGER.error("Could not decode textures payload", e);
                return new HashMap<>();
            }

            final Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = ((MinecraftTexturesPayloadAccessor) (Object) result).getTextures();
            if (result == null || textures == null)
                return new HashMap<>();

            return textures;
        }
    }

    public static class LocalYggdrasilAuthenticationService extends YggdrasilAuthenticationService {
        public final String server;

        public LocalYggdrasilAuthenticationService(Proxy proxy, String server) {
            super(proxy, (Environment) (Object) new LocalYggdrasilApi(server));
            this.server = server;
        }
    }

}
