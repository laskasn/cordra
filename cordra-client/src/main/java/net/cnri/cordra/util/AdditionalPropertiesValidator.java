package net.cnri.cordra.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.processing.Processor;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.core.util.RhinoHelper;
import com.github.fge.jsonschema.keyword.validator.AbstractKeywordValidator;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * A modification of fge json-schema-validator to allow determining extra properties for {@link JsonPruner}.
 */
public class AdditionalPropertiesValidator extends AbstractKeywordValidator {
    public static class AdditionalProperties extends AdditionalPropertiesValidator {
        public AdditionalProperties(JsonNode digest) {
            super("additionalProperties", digest);
        }
    }
    public static class Properties extends AdditionalPropertiesValidator {
        public Properties(JsonNode digest) {
            super("properties", digest);
        }
    }
    public static class PatternProperties extends AdditionalPropertiesValidator {
        public PatternProperties(JsonNode digest) {
            super("patternProperties", digest);
        }
    }

    private static final Joiner TOSTRING_JOINER = Joiner.on("; or ");

    private final boolean active;
    private final boolean additionalOk;
    private final Set<String> properties;
    private final Set<String> patternProperties;

    public AdditionalPropertiesValidator(String keyword, final JsonNode digest) {
        super(keyword);

        if ("additionalProperties".equals(keyword)) {
            active = true;
            additionalOk = digest.get(keyword).booleanValue();
        } else if ("properties".equals(keyword)) {
            additionalOk = true;
            if (digest.has("additionalProperties")) {
                active = false;
            } else {
                active = true;
            }
        } else { // patternProperties
            additionalOk = true;
            if (digest.has("additionalProperties")) {
                active = false;
            } else if (digest.has("properties")) {
                active = false;
            } else {
                active = true;
            }
        }

        if (!active) {
            properties = null;
            patternProperties = null;
            return;
        }

        {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            if (digest.has("properties")) {
                digest.get("properties").fieldNames()
                .forEachRemaining(builder::add);
            }
            properties = builder.build();
        }
        {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            if (digest.has("patternProperties")) {
                digest.get("patternProperties").fieldNames()
                .forEachRemaining(builder::add);
            }
            patternProperties = builder.build();
        }
    }

    @Override
    public void validate(final Processor<FullData, FullData> processor,
        final ProcessingReport report, final MessageBundle bundle,
        final FullData data) throws ProcessingException {

        if (!active) return;

        final JsonNode instance = data.getInstance().getNode();
        final Set<String> fields = Sets.newHashSet(instance.fieldNames());

        fields.removeAll(properties);

        final Set<String> tmp = Sets.newHashSet();

        for (final String field: fields)
            for (final String regex: patternProperties)
                if (RhinoHelper.regMatch(regex, field))
                    tmp.add(field);

        fields.removeAll(tmp);

        if (fields.isEmpty())
            return;

        /*
         * Display extra properties in order in the report
         */
        final ArrayNode node = JacksonUtils.nodeFactory().arrayNode();
        for (final String field: Ordering.natural().sortedCopy(fields))
            node.add(field);
        report.info(newMsg(data, bundle,
            "net.cnri.additionalProperties")
            .putArgument("additionalProperties", node));
        if (!additionalOk) {
            report.error(newMsg(data, bundle,
                "err.common.additionalProperties.notAllowed")
                .putArgument("unwanted", node));
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(keyword + ": ");

        sb.append("none");

        if (properties.isEmpty() && patternProperties.isEmpty())
            return sb.toString();

        sb.append(", unless: ");

        final Set<String> further = Sets.newLinkedHashSet();

        if (!properties.isEmpty())
            further.add("one property is any of: " + properties);

        if (!patternProperties.isEmpty())
            further.add("a property matches any regex among: "
                + patternProperties);

        sb.append(TOSTRING_JOINER.join(further));

        return sb.toString();
    }
}
