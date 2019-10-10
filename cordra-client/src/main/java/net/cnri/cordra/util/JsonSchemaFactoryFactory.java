package net.cnri.cordra.util;

import java.lang.reflect.Field;
import java.util.Map;

import com.github.fge.jackson.NodeType;
import com.github.fge.jsonschema.cfg.ValidationConfiguration;
import com.github.fge.jsonschema.cfg.ValidationConfigurationBuilder;
import com.github.fge.jsonschema.core.keyword.syntax.checkers.helpers.TypeOnlySyntaxChecker;
import com.github.fge.jsonschema.library.DraftV4Library;
import com.github.fge.jsonschema.library.Keyword;
import com.github.fge.jsonschema.library.KeywordBuilder;
import com.github.fge.jsonschema.library.LibraryBuilder;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

/**
 * A modification of fge json-schema-validator to allow determining extra properties for {@link JsonPruner}, and
 * also to ignore the {@code $schema} keyword.
 */
public class JsonSchemaFactoryFactory {
    private static JsonSchemaFactory INSTANCE;

    static {
        LibraryBuilder lib = DraftV4Library.get().thaw();
        {
            KeywordBuilder kb = Keyword.newBuilder("additionalProperties");
            kb.withIdentityDigester(NodeType.OBJECT);
            kb.withSyntaxChecker(new TypeOnlySyntaxChecker("additionalProperties", NodeType.BOOLEAN, NodeType.OBJECT));
            kb.withValidatorClass(AdditionalPropertiesValidator.AdditionalProperties.class);
            lib.addKeyword(kb.freeze());
        }
        {
            KeywordBuilder kb = Keyword.newBuilder("properties");
            kb.withIdentityDigester(NodeType.OBJECT);
            kb.withSyntaxChecker(new TypeOnlySyntaxChecker("properties", NodeType.BOOLEAN, NodeType.OBJECT));
            kb.withValidatorClass(AdditionalPropertiesValidator.Properties.class);
            lib.addKeyword(kb.freeze());
        }
        {
            KeywordBuilder kb = Keyword.newBuilder("patternProperties");
            kb.withIdentityDigester(NodeType.OBJECT);
            kb.withSyntaxChecker(new TypeOnlySyntaxChecker("patternProperties", NodeType.BOOLEAN, NodeType.OBJECT));
            kb.withValidatorClass(AdditionalPropertiesValidator.PatternProperties.class);
            lib.addKeyword(kb.freeze());
        }
        ValidationConfigurationBuilder vcb = ValidationConfiguration.newBuilder();
        clearLibraries(vcb);
        vcb.setDefaultLibrary("https://cordra.org/", lib.freeze());
        INSTANCE = JsonSchemaFactory.newBuilder().setValidationConfiguration(vcb.freeze()).freeze();
    }

    /**
     * This allows us to override the json-schema-validator behavior,
     * regardless of the use of the $schema keyword on the schemas.
     */
    @SuppressWarnings("rawtypes")
    private static void clearLibraries(ValidationConfigurationBuilder vcb) {
        try {
            Field f = vcb.getClass().getDeclaredField("libraries");
            f.setAccessible(true);
            ((Map)f.get(vcb)).clear();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public static JsonSchemaFactory newJsonSchemaFactory() {
        return JsonSchemaFactoryFactory.INSTANCE.thaw().freeze();
    }
}
