package net.cnri.cordra;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.NodeType;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.cfg.ValidationConfiguration;
import com.github.fge.jsonschema.cfg.ValidationConfigurationBuilder;
import com.github.fge.jsonschema.core.exceptions.JsonReferenceException;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.keyword.syntax.checkers.draftv4.RequiredSyntaxChecker;
import com.github.fge.jsonschema.core.keyword.syntax.checkers.helpers.TypeOnlySyntaxChecker;
import com.github.fge.jsonschema.core.processing.Processor;
import com.github.fge.jsonschema.core.ref.JsonRef;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.github.fge.jsonschema.format.AbstractFormatAttribute;
import com.github.fge.jsonschema.keyword.digest.draftv4.RequiredDigester;
import com.github.fge.jsonschema.keyword.validator.AbstractKeywordValidator;
import com.github.fge.jsonschema.keyword.validator.KeywordValidator;
import com.github.fge.jsonschema.library.DraftV4Library;
import com.github.fge.jsonschema.library.Keyword;
import com.github.fge.jsonschema.library.KeywordBuilder;
import com.github.fge.jsonschema.library.LibraryBuilder;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

public class JsonSchemaFactoryFactory {
    private static JsonSchemaFactory INSTANCE;

    static {
        LibraryBuilder lib = DraftV4Library.get().thaw();
//        lib.addFormatAttribute("file", new RecordingFormatAttribute("file"));
        lib.addKeyword(buildCordraRequiredKeyword());
        lib.addKeyword(recordingKeyword(Constants.CORDRA_SCHEMA_KEYWORD, CordraKeywordValidator.class));
        lib.addKeyword(recordingKeyword(Constants.OLD_REPOSITORY_SCHEMA_KEYWORD, NetCnriRepositoryKeywordValidator.class));
        ValidationConfigurationBuilder vcb = ValidationConfiguration.newBuilder();
        clearLibraries(vcb);
        vcb.setDefaultLibrary("https://cordra.org/schema", lib.freeze());
        INSTANCE = JsonSchemaFactory.newBuilder().setValidationConfiguration(vcb.freeze()).freeze();
    }

    // This allows us to override the json-schema-validator behavior,
    // regardless of the use of the $schema keyword on the schemas
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
//      return JsonSchemaFactory.byDefault();
      return JsonSchemaFactoryFactory.INSTANCE.thaw().freeze();
    }

    private static Keyword buildCordraRequiredKeyword() {
        KeywordBuilder kb = Keyword.newBuilder("required");
        kb.withDigester(RequiredDigester.getInstance());
        kb.withSyntaxChecker(RequiredSyntaxChecker.getInstance());
        kb.withValidatorClass(CordraRequiredKeywordValidator.class);
        return kb.freeze();
    }

    private static Keyword recordingKeyword(String keyword, Class<? extends KeywordValidator> klass) {
        KeywordBuilder kb = Keyword.newBuilder(keyword);
        // if we need the whole schema chunk, this should be withIdentityDigester, if only the keyword, withSimpleDigester
        kb.withIdentityDigester(NodeType.STRING, NodeType.values()); // ignored except on data json of these types
        kb.withSyntaxChecker(new TypeOnlySyntaxChecker(keyword, NodeType.OBJECT)); // attribute value of keyword in schema json must be this type or validation fails
        kb.withValidatorClass(klass);
        return kb.freeze();
    }

    @SuppressWarnings("unused")
    private static class RecordingFormatAttribute extends AbstractFormatAttribute {
        public RecordingFormatAttribute(String format) {
            super(format, NodeType.STRING); // ignored except on data json of these types
        }

        @Override
        public void validate(ProcessingReport report, MessageBundle bundle, FullData data) throws ProcessingException {
            ProcessingMessage msg = newMsg(data, bundle, "net.cnri.message");
            report.info(msg);
        }
    }

    public static class RecordingKeywordValidator extends AbstractKeywordValidator {
        final JsonNode attribute;

        public RecordingKeywordValidator(String keyword, JsonNode node) {
            super(keyword);
            this.attribute = node.get(keyword);
        }

        @Override
        public void validate(Processor<FullData,FullData> processor, ProcessingReport report, MessageBundle bundle, FullData data) throws ProcessingException {
            ProcessingMessage msg = newMsg(data, bundle, "net.cnri.message");
            msg.put("attribute", attribute);
            report.info(msg);
        }

        @Override
        public String toString() {
            return "RecordingKeywordValidator(" + keyword + ")";
        }
    }

    public static class CordraKeywordValidator extends RecordingKeywordValidator {
        public CordraKeywordValidator(JsonNode node) {
            super(Constants.CORDRA_SCHEMA_KEYWORD, node);
        }
    }

    public static class NetCnriRepositoryKeywordValidator extends RecordingKeywordValidator {
        public NetCnriRepositoryKeywordValidator(JsonNode node) {
            super(Constants.OLD_REPOSITORY_SCHEMA_KEYWORD, node);
        }
    }

    public static class CordraRequiredKeywordValidator extends AbstractKeywordValidator {
        private final Set<String> required;

        public CordraRequiredKeywordValidator(final JsonNode digest) {
            super("required");

            final ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            for (final JsonNode element : digest.get(keyword)) {
                builder.add(element.textValue());
            }
            required = builder.build();
        }

        @Override
        public String toString() {
            return keyword + ": " + required.size() + " properties";
        }

        @Override
        public void validate(final Processor<FullData, FullData> processor,
                             final ProcessingReport report, final MessageBundle bundle,
                             final FullData data)
                throws ProcessingException {
            SchemaTree schema = data.getSchema();
            SchemaTree rootSchema = schema.setPointer(JsonPointer.empty());
            final Set<String> set = Sets.newLinkedHashSet(required);

            set.removeAll(Sets.newHashSet(data.getInstance().getNode().fieldNames()));

            Set<String> autoGeneratedFields = Sets.newHashSet();
            for (String fieldName : set) {
                String fieldPointer = getFieldPointerResolvingReferences(fieldName, schema);
                JsonNode autogenNode = JsonUtil.getJsonAtPointer(fieldPointer + "/cordra/type/autoGeneratedField", rootSchema.getNode());
                if (autogenNode != null && !autogenNode.isMissingNode()) {
                    ProcessingMessage msg = newMsg(data, bundle, "net.cnri.message");
                    msg.put("fieldName", "/" + fieldName);
                    msg.put("fieldPointer", fieldPointer);
                    report.info(msg);
                    autoGeneratedFields.add(fieldName);
                }
            }
            set.removeAll(autoGeneratedFields);

            if (!set.isEmpty()) {
                report.error(newMsg(data, bundle, "err.common.object.missingMembers")
                        .put("required", required)
                        .putArgument("missing", toArrayNode(set)));
            }
        }

        private String getFieldPointerResolvingReferences(String fieldName, SchemaTree schema) {
            JsonNode schemaNode = schema.getNode();
            JsonNode propertiesNode = schemaNode.at("/properties/" + fieldName);
            String pointer;
            if (propertiesNode.has("$ref")) {
                JsonNode refNode = propertiesNode.get("$ref");
                try {
                    JsonRef jsonRef = JsonRef.fromString(refNode.asText());
                    SchemaTree rootSchema = schema.setPointer(JsonPointer.empty());
                    JsonPointer jsonPointer = rootSchema.matchingPointer(jsonRef);
                    if (jsonPointer == null) return null;
                    return jsonPointer.toString();
                } catch (JsonReferenceException e) {
                    return null;
                }
            } else {
                pointer = schema.getPointer() + "/properties/" + fieldName;
            }
            return pointer;
        }
    }
}
