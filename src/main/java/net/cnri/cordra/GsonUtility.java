package net.cnri.cordra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * A factory for singleton {@code Gson} instances which can properly serialize {@code Stream}s
 */
public class GsonUtility {
    private static final Logger logger = LoggerFactory.getLogger(GsonUtility.class);

    /**
     * Returns a {@code GsonBuilder} with adapters to properly serialize {@code Stream}s.
     */
    public static GsonBuilder setup(GsonBuilder gsonBuilder) {
        gsonBuilder.registerTypeAdapterFactory(new StreamTypeAdapterFactory());
        gsonBuilder.registerTypeHierarchyAdapter(PublicKey.class, new PublicKeyTypeHierarchyAdapter());
        gsonBuilder.registerTypeHierarchyAdapter(PrivateKey.class, new PrivateKeyTypeHierarchyAdapter());
        gsonBuilder.registerTypeHierarchyAdapter(JsonNode.class, new JsonNodeTypeAdapter());
        gsonBuilder.registerTypeAdapter(Document.class, new DocumentTypeAdapter());
        return gsonBuilder;
    }

    /**
     * Returns a {@code Gson}.
     */
    public static Gson getGson() {
        return GsonHolder.gson;
    }

    /**
     * Returns a {@code Gson} which is configured for pretty-printing.
     */
    public static Gson getPrettyGson() {
        return PrettyGsonHolder.prettyGson;
    }

    private static class GsonHolder {
        static Gson gson;
        static {
            gson = GsonUtility.setup(new GsonBuilder().disableHtmlEscaping()).create();
        }
    }

    private static class PrettyGsonHolder {
        static Gson prettyGson;
        static {
            prettyGson = GsonUtility.setup(new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()).create();
        }
    }

    public static class StreamTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            // boilerplate for polymorphic type adapter creation from TypeAdapterFactory javadoc
            Type type = typeToken.getType();
            if (typeToken.getRawType() != Stream.class || !(type instanceof ParameterizedType)) {
                return null;
            }
            Type elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
            TypeAdapter<?> elementAdapter = gson.getAdapter(TypeToken.get(elementType));
            return (TypeAdapter<T>) new StreamGsonTypeAdapter<>(elementAdapter).nullSafe();
        }
    }

    /**
     * Serializing {@code Stream}s of objects in a streaming fashion, which will be useful
     * for outputting extremely large search results.
     */
    public static class StreamGsonTypeAdapter<T> extends TypeAdapter<Stream<T>> {
        private final TypeAdapter<T> elementAdapter;

        public StreamGsonTypeAdapter(TypeAdapter<T> elementAdapter) {
            this.elementAdapter = elementAdapter;
        }

        @Override
        public Stream<T> read(JsonReader reader) throws IOException {
            List<T> list = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
                T obj = elementAdapter.read(reader);
                list.add(obj);
            }
            reader.endArray();
            return list.stream();
        }

        @Override
        public void write(JsonWriter writer, Stream<T> stream) throws IOException {
            if (stream != null) {
                writer.beginArray();
                try {
                    stream.forEach(obj -> {
                        try {
                            elementAdapter.write(writer, obj);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (UncheckedIOException e) {
                    throw e.getCause();
                }
                writer.endArray();
            }
        }
    }

    public static class PublicKeyTypeHierarchyAdapter implements JsonSerializer<PublicKey>, JsonDeserializer<PublicKey> {
        @Override
        public JsonElement serialize(PublicKey key, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = new JsonObject();
            Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();
            if (key instanceof DSAPublicKey) {
                DSAPublicKey dsaKey = (DSAPublicKey) key;
                byte[] y = dsaKey.getY().toByteArray();
                DSAParams dsaParams = dsaKey.getParams();
                byte[] p = dsaParams.getP().toByteArray();
                byte[] q = dsaParams.getQ().toByteArray();
                byte[] g = dsaParams.getG().toByteArray();
                json.addProperty("kty", "DSA");
                json.addProperty("y", base64Encoder.encodeToString(unsigned(y)));
                json.addProperty("p", base64Encoder.encodeToString(unsigned(p)));
                json.addProperty("q", base64Encoder.encodeToString(unsigned(q)));
                json.addProperty("g", base64Encoder.encodeToString(unsigned(g)));
            } else if (key instanceof RSAPublicKey) {
                RSAPublicKey rsaKey = (RSAPublicKey) key;
                byte[] n = rsaKey.getModulus().toByteArray();
                byte[] e = rsaKey.getPublicExponent().toByteArray();
                json.addProperty("kty", "RSA");
                json.addProperty("n", base64Encoder.encodeToString(unsigned(n)));
                json.addProperty("e", base64Encoder.encodeToString(unsigned(e)));
            } else {
                throw new UnsupportedOperationException("Unsupported key type " + key.getClass().getName());
            }
            return json;
        }

        @Override
        public PublicKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                JsonObject obj = json.getAsJsonObject();
                String kty = obj.get("kty").getAsString();
                if ("DSA".equalsIgnoreCase(kty)) {
                    byte[] y = Base64.getUrlDecoder().decode(obj.get("y").getAsString());
                    byte[] p = Base64.getUrlDecoder().decode(obj.get("p").getAsString());
                    byte[] q = Base64.getUrlDecoder().decode(obj.get("q").getAsString());
                    byte[] g = Base64.getUrlDecoder().decode(obj.get("g").getAsString());
                    DSAPublicKeySpec keySpec = new DSAPublicKeySpec(new BigInteger(1, y), new BigInteger(1, p), new BigInteger(1, q), new BigInteger(1, g));
                    KeyFactory dsaKeyFactory = KeyFactory.getInstance("DSA");
                    return dsaKeyFactory.generatePublic(keySpec);
                } else if ("RSA".equalsIgnoreCase(kty)) {
                    byte[] n = Base64.getUrlDecoder().decode(obj.get("n").getAsString());
                    byte[] e = Base64.getUrlDecoder().decode(obj.get("e").getAsString());
                    RSAPublicKeySpec keySpec = new RSAPublicKeySpec(new BigInteger(1, n), new BigInteger(1, e));
                    KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");
                    return rsaKeyFactory.generatePublic(keySpec);
                } else {
                    throw new UnsupportedOperationException("Unsupported key type " + kty);
                }
            } catch (JsonParseException e) {
                return null;
                // throw e;
            } catch (Exception e) {
                logger.error("Unable to deserialize JWK, returning null", e);
                return null;
                //throw new JsonParseException(e);
            }
        }
    }

    public static class PrivateKeyTypeHierarchyAdapter implements JsonSerializer<PrivateKey>, JsonDeserializer<PrivateKey> {
        @Override
        public JsonElement serialize(PrivateKey key, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = new JsonObject();
            Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();
            if (key instanceof DSAPrivateKey) {
                DSAPrivateKey dsaKey = (DSAPrivateKey) key;
                byte[] x = dsaKey.getX().toByteArray();
                DSAParams dsaParams = dsaKey.getParams();
                byte[] p = dsaParams.getP().toByteArray();
                byte[] q = dsaParams.getQ().toByteArray();
                byte[] g = dsaParams.getG().toByteArray();
                json.addProperty("kty", "DSA");
                json.addProperty("x", base64Encoder.encodeToString(unsigned(x)));
                json.addProperty("p", base64Encoder.encodeToString(unsigned(p)));
                json.addProperty("q", base64Encoder.encodeToString(unsigned(q)));
                json.addProperty("g", base64Encoder.encodeToString(unsigned(g)));
            } else if (key instanceof RSAPrivateKey) {
                RSAPrivateKey rsaKey = (RSAPrivateKey) key;
                byte[] n = rsaKey.getModulus().toByteArray();
                byte[] d = rsaKey.getPrivateExponent().toByteArray();
                json.addProperty("kty", "RSA");
                if (key instanceof RSAPrivateCrtKey) {
                    RSAPrivateCrtKey rsacrtKey = (RSAPrivateCrtKey) rsaKey;
                    byte[] e = rsacrtKey.getPublicExponent().toByteArray();
                    byte[] p = rsacrtKey.getPrimeP().toByteArray();
                    byte[] q = rsacrtKey.getPrimeQ().toByteArray();
                    byte[] dp = rsacrtKey.getPrimeExponentP().toByteArray();
                    byte[] dq = rsacrtKey.getPrimeExponentQ().toByteArray();
                    byte[] qi = rsacrtKey.getCrtCoefficient().toByteArray();
                    json.addProperty("n", base64Encoder.encodeToString(unsigned(n)));
                    json.addProperty("e", base64Encoder.encodeToString(unsigned(e)));
                    json.addProperty("d", base64Encoder.encodeToString(unsigned(d)));
                    json.addProperty("p", base64Encoder.encodeToString(unsigned(p)));
                    json.addProperty("q", base64Encoder.encodeToString(unsigned(q)));
                    json.addProperty("dp", base64Encoder.encodeToString(unsigned(dp)));
                    json.addProperty("dq", base64Encoder.encodeToString(unsigned(dq)));
                    json.addProperty("qi", base64Encoder.encodeToString(unsigned(qi)));
                } else {
                    json.addProperty("n", base64Encoder.encodeToString(unsigned(n)));
                    json.addProperty("d", base64Encoder.encodeToString(unsigned(d)));
                }
            } else {
                throw new UnsupportedOperationException("Unsupported key type " + key.getClass().getName());
            }
            return json;
        }

        @Override
        public PrivateKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                JsonObject obj = json.getAsJsonObject();
                String kty = obj.get("kty").getAsString();
                if ("DSA".equalsIgnoreCase(kty)) {
                    byte[] x = Base64.getUrlDecoder().decode(obj.get("x").getAsString());
                    byte[] p = Base64.getUrlDecoder().decode(obj.get("p").getAsString());
                    byte[] q = Base64.getUrlDecoder().decode(obj.get("q").getAsString());
                    byte[] g = Base64.getUrlDecoder().decode(obj.get("g").getAsString());
                    DSAPrivateKeySpec keySpec = new DSAPrivateKeySpec(new BigInteger(1, x), new BigInteger(1, p), new BigInteger(1, q), new BigInteger(1, g));
                    KeyFactory dsaKeyFactory = KeyFactory.getInstance("DSA");
                    return dsaKeyFactory.generatePrivate(keySpec);
                } else if ("RSA".equalsIgnoreCase(kty)) {
                    byte[] n = Base64.getUrlDecoder().decode(obj.get("n").getAsString());
                    byte[] d = Base64.getUrlDecoder().decode(obj.get("d").getAsString());
                    RSAPrivateKeySpec keySpec;
                    if (obj.has("qi")) {
                        byte[] e = Base64.getUrlDecoder().decode(obj.get("e").getAsString());
                        byte[] p = Base64.getUrlDecoder().decode(obj.get("p").getAsString());
                        byte[] q = Base64.getUrlDecoder().decode(obj.get("q").getAsString());
                        byte[] dp = Base64.getUrlDecoder().decode(obj.get("dp").getAsString());
                        byte[] dq = Base64.getUrlDecoder().decode(obj.get("dq").getAsString());
                        byte[] qi = Base64.getUrlDecoder().decode(obj.get("qi").getAsString());
                        keySpec = new RSAPrivateCrtKeySpec(new BigInteger(1, n), new BigInteger(1, e), new BigInteger(1, d), new BigInteger(1, p), new BigInteger(1, q), new BigInteger(1, dp), new BigInteger(1, dq), new BigInteger(1, qi));
                    } else {
                        keySpec = new RSAPrivateKeySpec(new BigInteger(1, n), new BigInteger(1, d));
                    }
                    KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");
                    return rsaKeyFactory.generatePrivate(keySpec);
                } else {
                    throw new UnsupportedOperationException("Unsupported key type " + kty);
                }
            } catch (JsonParseException e) {
                throw e;
            } catch (Exception e) {
                throw new JsonParseException(e);
            }
        }
    }

    private static byte[] unsigned(byte[] arr) {
        if (arr.length == 0) return new byte[1];
        int zeros = 0;
        for (byte element : arr) {
            if (element == 0) zeros++;
            else break;
        }
        if (zeros == arr.length) zeros--;
        if (zeros == 0) return arr;
        byte[] res = new byte[arr.length - zeros];
        System.arraycopy(arr, zeros, res, 0, arr.length - zeros);
        return res;
    }

    public static class JsonNodeTypeAdapter implements JsonSerializer<JsonNode> {

        @Override
        public JsonElement serialize(JsonNode jsonNode, Type typeOfSrc, JsonSerializationContext context) {
            if (jsonNode == null || jsonNode.isMissingNode()) {
                return null;
            }
            if (jsonNode.isNull()) {
                return JsonNull.INSTANCE;
            }
            if (jsonNode.isBoolean()) {
                return new JsonPrimitive(jsonNode.asBoolean());
            }
            if (jsonNode.isNumber()) {
                return new JsonPrimitive(jsonNode.numberValue());
            }
            if (jsonNode.isTextual()) {
                return new JsonPrimitive(jsonNode.asText());
            }
            if (jsonNode.isArray()) {
                JsonArray arr = new JsonArray();
                Iterator<JsonNode> iter = jsonNode.elements();
                while (iter.hasNext()) {
                    arr.add(serialize(iter.next(), null, null));
                }
                return arr;
            }
            if (jsonNode.isObject()) {
                JsonObject obj = new JsonObject();
                Iterator<Map.Entry<String, JsonNode>> iter = jsonNode.fields();
                while (iter.hasNext()) {
                    Map.Entry<String, JsonNode> field = iter.next();
                    obj.add(field.getKey(), serialize(field.getValue(), null, null));
                }
                return obj;
            }
            throw new JsonParseException("Unexpected JsonNode of no known JSON type");
        }
    }

    public static class DocumentTypeAdapter implements JsonSerializer<Document> {
        private static final Type MAP_STRING_OBJECT_TYPE = new TypeToken<Map<String,Object>>() { }.getType();
        @Override
        public JsonElement serialize(Document src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src, MAP_STRING_OBJECT_TYPE);
        }
    }
}
