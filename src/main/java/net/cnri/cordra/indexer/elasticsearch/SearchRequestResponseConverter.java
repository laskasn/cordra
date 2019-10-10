package net.cnri.cordra.indexer.elasticsearch;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.security.RefreshPolicy;
import org.elasticsearch.common.CheckedFunction;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.plugins.spi.NamedXContentProvider;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.adjacency.AdjacencyMatrixAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.adjacency.ParsedAdjacencyMatrix;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilters;
import org.elasticsearch.search.aggregations.bucket.geogrid.GeoGridAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.geogrid.ParsedGeoHashGrid;
import org.elasticsearch.search.aggregations.bucket.global.GlobalAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.global.ParsedGlobal;
import org.elasticsearch.search.aggregations.bucket.histogram.*;
import org.elasticsearch.search.aggregations.bucket.missing.MissingAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.missing.ParsedMissing;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedReverseNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.*;
import org.elasticsearch.search.aggregations.bucket.sampler.InternalSampler;
import org.elasticsearch.search.aggregations.bucket.sampler.ParsedSampler;
import org.elasticsearch.search.aggregations.bucket.significant.ParsedSignificantLongTerms;
import org.elasticsearch.search.aggregations.bucket.significant.ParsedSignificantStringTerms;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantLongTerms;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.*;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.ParsedAvg;
import org.elasticsearch.search.aggregations.metrics.cardinality.CardinalityAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.cardinality.ParsedCardinality;
import org.elasticsearch.search.aggregations.metrics.geobounds.GeoBoundsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.geobounds.ParsedGeoBounds;
import org.elasticsearch.search.aggregations.metrics.geocentroid.GeoCentroidAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.geocentroid.ParsedGeoCentroid;
import org.elasticsearch.search.aggregations.metrics.mad.MedianAbsoluteDeviationAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.mad.ParsedMedianAbsoluteDeviation;
import org.elasticsearch.search.aggregations.metrics.max.MaxAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.max.ParsedMax;
import org.elasticsearch.search.aggregations.metrics.min.MinAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.min.ParsedMin;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.InternalHDRPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.InternalHDRPercentiles;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.ParsedHDRPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.ParsedHDRPercentiles;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.scripted.ParsedScriptedMetric;
import org.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetricAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.elasticsearch.search.aggregations.metrics.stats.StatsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStatsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ParsedExtendedStats;
import org.elasticsearch.search.aggregations.metrics.sum.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.tophits.ParsedTopHits;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.valuecount.ParsedValueCount;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCountAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.weighted_avg.ParsedWeightedAvg;
import org.elasticsearch.search.aggregations.metrics.weighted_avg.WeightedAvgAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.InternalSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.ParsedSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.InternalBucketMetricValue;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.ParsedBucketMetricValue;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.percentile.ParsedPercentilesBucket;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.percentile.PercentilesBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.stats.ParsedStatsBucket;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.stats.StatsBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.stats.extended.ExtendedStatsBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.stats.extended.ParsedExtendedStatsBucket;
import org.elasticsearch.search.aggregations.pipeline.derivative.DerivativePipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.derivative.ParsedDerivative;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestion;
import org.elasticsearch.search.suggest.term.TermSuggestion;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/*
   This madness is mostly copy-pasted from Elasticsearch's builtin RequestConverter class, which is very useful and
   not at all public. This whole class can go away when we update to 7/8.
 */
class SearchRequestResponseConverter {
    private static final XContentType REQUEST_BODY_CONTENT_TYPE = XContentType.JSON;
    public static final DeprecationHandler DEPRECATION_HANDLER = new DeprecationHandler() {
        @Override
        public void usedDeprecatedName(String usedName, String modernName) {
        }

        @Override
        public void usedDeprecatedField(String usedName, String replacedWith) {
        }

        @Override
        public void deprecated(String message, Object... params) {
        }
    };
    private static NamedXContentRegistry registry = new NamedXContentRegistry(
            Stream.of(getDefaultNamedXContents().stream(), getProvidedNamedXContents().stream())
                    .flatMap(Function.identity()).collect(toList()));

    private static List<NamedXContentRegistry.Entry> getDefaultNamedXContents() {
        Map<String, ContextParser<Object, ? extends Aggregation>> map = new HashMap<>();
        map.put(CardinalityAggregationBuilder.NAME, (p, c) -> ParsedCardinality.fromXContent(p, (String) c));
        map.put(InternalHDRPercentiles.NAME, (p, c) -> ParsedHDRPercentiles.fromXContent(p, (String) c));
        map.put(InternalHDRPercentileRanks.NAME, (p, c) -> ParsedHDRPercentileRanks.fromXContent(p, (String) c));
        map.put(InternalTDigestPercentiles.NAME, (p, c) -> ParsedTDigestPercentiles.fromXContent(p, (String) c));
        map.put(InternalTDigestPercentileRanks.NAME, (p, c) -> ParsedTDigestPercentileRanks.fromXContent(p, (String) c));
        map.put(PercentilesBucketPipelineAggregationBuilder.NAME, (p, c) -> ParsedPercentilesBucket.fromXContent(p, (String) c));
        map.put(MedianAbsoluteDeviationAggregationBuilder.NAME, (p, c) -> ParsedMedianAbsoluteDeviation.fromXContent(p, (String) c));
        map.put(MinAggregationBuilder.NAME, (p, c) -> ParsedMin.fromXContent(p, (String) c));
        map.put(MaxAggregationBuilder.NAME, (p, c) -> ParsedMax.fromXContent(p, (String) c));
        map.put(SumAggregationBuilder.NAME, (p, c) -> ParsedSum.fromXContent(p, (String) c));
        map.put(AvgAggregationBuilder.NAME, (p, c) -> ParsedAvg.fromXContent(p, (String) c));
        map.put(WeightedAvgAggregationBuilder.NAME, (p, c) -> ParsedWeightedAvg.fromXContent(p, (String) c));
        map.put(ValueCountAggregationBuilder.NAME, (p, c) -> ParsedValueCount.fromXContent(p, (String) c));
        map.put(InternalSimpleValue.NAME, (p, c) -> ParsedSimpleValue.fromXContent(p, (String) c));
        map.put(DerivativePipelineAggregationBuilder.NAME, (p, c) -> ParsedDerivative.fromXContent(p, (String) c));
        map.put(InternalBucketMetricValue.NAME, (p, c) -> ParsedBucketMetricValue.fromXContent(p, (String) c));
        map.put(StatsAggregationBuilder.NAME, (p, c) -> ParsedStats.fromXContent(p, (String) c));
        map.put(StatsBucketPipelineAggregationBuilder.NAME, (p, c) -> ParsedStatsBucket.fromXContent(p, (String) c));
        map.put(ExtendedStatsAggregationBuilder.NAME, (p, c) -> ParsedExtendedStats.fromXContent(p, (String) c));
        map.put(ExtendedStatsBucketPipelineAggregationBuilder.NAME,
                (p, c) -> ParsedExtendedStatsBucket.fromXContent(p, (String) c));
        map.put(GeoBoundsAggregationBuilder.NAME, (p, c) -> ParsedGeoBounds.fromXContent(p, (String) c));
        map.put(GeoCentroidAggregationBuilder.NAME, (p, c) -> ParsedGeoCentroid.fromXContent(p, (String) c));
        map.put(HistogramAggregationBuilder.NAME, (p, c) -> ParsedHistogram.fromXContent(p, (String) c));
        map.put(DateHistogramAggregationBuilder.NAME, (p, c) -> ParsedDateHistogram.fromXContent(p, (String) c));
        map.put(AutoDateHistogramAggregationBuilder.NAME, (p, c) -> ParsedAutoDateHistogram.fromXContent(p, (String) c));
        map.put(StringTerms.NAME, (p, c) -> ParsedStringTerms.fromXContent(p, (String) c));
        map.put(LongTerms.NAME, (p, c) -> ParsedLongTerms.fromXContent(p, (String) c));
        map.put(DoubleTerms.NAME, (p, c) -> ParsedDoubleTerms.fromXContent(p, (String) c));
        map.put(MissingAggregationBuilder.NAME, (p, c) -> ParsedMissing.fromXContent(p, (String) c));
        map.put(NestedAggregationBuilder.NAME, (p, c) -> ParsedNested.fromXContent(p, (String) c));
        map.put(ReverseNestedAggregationBuilder.NAME, (p, c) -> ParsedReverseNested.fromXContent(p, (String) c));
        map.put(GlobalAggregationBuilder.NAME, (p, c) -> ParsedGlobal.fromXContent(p, (String) c));
        map.put(FilterAggregationBuilder.NAME, (p, c) -> ParsedFilter.fromXContent(p, (String) c));
        map.put(InternalSampler.PARSER_NAME, (p, c) -> ParsedSampler.fromXContent(p, (String) c));
        map.put(GeoGridAggregationBuilder.NAME, (p, c) -> ParsedGeoHashGrid.fromXContent(p, (String) c));
        map.put(RangeAggregationBuilder.NAME, (p, c) -> ParsedRange.fromXContent(p, (String) c));
        map.put(DateRangeAggregationBuilder.NAME, (p, c) -> ParsedDateRange.fromXContent(p, (String) c));
        map.put(GeoDistanceAggregationBuilder.NAME, (p, c) -> ParsedGeoDistance.fromXContent(p, (String) c));
        map.put(FiltersAggregationBuilder.NAME, (p, c) -> ParsedFilters.fromXContent(p, (String) c));
        map.put(AdjacencyMatrixAggregationBuilder.NAME, (p, c) -> ParsedAdjacencyMatrix.fromXContent(p, (String) c));
        map.put(SignificantLongTerms.NAME, (p, c) -> ParsedSignificantLongTerms.fromXContent(p, (String) c));
        map.put(SignificantStringTerms.NAME, (p, c) -> ParsedSignificantStringTerms.fromXContent(p, (String) c));
        map.put(ScriptedMetricAggregationBuilder.NAME, (p, c) -> ParsedScriptedMetric.fromXContent(p, (String) c));
        map.put(IpRangeAggregationBuilder.NAME, (p, c) -> ParsedBinaryRange.fromXContent(p, (String) c));
        map.put(TopHitsAggregationBuilder.NAME, (p, c) -> ParsedTopHits.fromXContent(p, (String) c));
        map.put(CompositeAggregationBuilder.NAME, (p, c) -> ParsedComposite.fromXContent(p, (String) c));
        List<NamedXContentRegistry.Entry> entries = map.entrySet().stream()
                .map(entry -> new NamedXContentRegistry.Entry(Aggregation.class, new ParseField(entry.getKey()), entry.getValue()))
                .collect(Collectors.toList());
        entries.add(new NamedXContentRegistry.Entry(Suggest.Suggestion.class, new ParseField(TermSuggestion.NAME),
                (parser, context) -> TermSuggestion.fromXContent(parser, (String) context)));
        entries.add(new NamedXContentRegistry.Entry(Suggest.Suggestion.class, new ParseField(PhraseSuggestion.NAME),
                (parser, context) -> PhraseSuggestion.fromXContent(parser, (String) context)));
        entries.add(new NamedXContentRegistry.Entry(Suggest.Suggestion.class, new ParseField(CompletionSuggestion.NAME),
                (parser, context) -> CompletionSuggestion.fromXContent(parser, (String) context)));
        return entries;
    }

    private static List<NamedXContentRegistry.Entry> getProvidedNamedXContents() {
        List<NamedXContentRegistry.Entry> entries = new ArrayList<>();
        for (NamedXContentProvider service : ServiceLoader.load(NamedXContentProvider.class)) {
            entries.addAll(service.getNamedXContentParsers());
        }
        return entries;
    }

    static Request convertRequest(SearchRequest searchRequest, boolean totalHitsAsInt) throws IOException {
        String endpoint = endpoint(searchRequest.indices(), searchRequest.types());
        if (totalHitsAsInt) endpoint = endpoint + "?rest_total_hits_as_int=true";
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);

        Params params = new Params(request);
        addSearchRequestParams(params, searchRequest);

        if (searchRequest.source() != null) {
            request.setEntity(createEntity(searchRequest.source()));
        }
        return request;
    }

    static SearchResponse convertResponse(Response response) throws IOException {
        return parseEntity(response.getEntity(), SearchResponse::fromXContent);
    }

    private static <Resp> Resp parseEntity(final HttpEntity entity,
                                           final CheckedFunction<XContentParser, Resp, IOException> entityParser) throws IOException {
        if (entity == null) {
            throw new IllegalStateException("Response body expected but not returned");
        }
        if (entity.getContentType() == null) {
            throw new IllegalStateException("Elasticsearch didn't return the [Content-Type] header, unable to parse response body");
        }
        XContentType xContentType = XContentType.fromMediaTypeOrFormat(entity.getContentType().getValue());
        if (xContentType == null) {
            throw new IllegalStateException("Unsupported Content-Type: " + entity.getContentType().getValue());
        }
        try (XContentParser parser = xContentType.xContent().createParser(registry, DEPRECATION_HANDLER, entity.getContent())) {
            return entityParser.apply(parser);
        }
    }

    private static HttpEntity createEntity(ToXContent toXContent)
            throws IOException {
        BytesRef source = XContentHelper.toXContent(toXContent, SearchRequestResponseConverter.REQUEST_BODY_CONTENT_TYPE, ToXContent.EMPTY_PARAMS, false).toBytesRef();
        return new NByteArrayEntity(source.bytes, source.offset, source.length, createContentType());
    }

    private static ContentType createContentType() {
        return ContentType.create(SearchRequestResponseConverter.REQUEST_BODY_CONTENT_TYPE.mediaTypeWithoutParameters(), (Charset) null);
    }

    private static String endpoint(String[] indices, String[] types) {
        return new EndpointBuilder().addCommaSeparatedPathParts(indices).addCommaSeparatedPathParts(types)
                .addPathPartAsIs("_search").build();
    }

    private static void addSearchRequestParams(Params params, SearchRequest searchRequest) {
        params.putParam(RestSearchAction.TYPED_KEYS_PARAM, "true");
        params.withRouting(searchRequest.routing());
        params.withPreference(searchRequest.preference());
        params.withIndicesOptions(searchRequest.indicesOptions());
        params.putParam("search_type", searchRequest.searchType().name().toLowerCase(Locale.ROOT));
        if (searchRequest.requestCache() != null) {
            params.putParam("request_cache", Boolean.toString(searchRequest.requestCache()));
        }
        if (searchRequest.allowPartialSearchResults() != null) {
            params.putParam("allow_partial_search_results", Boolean.toString(searchRequest.allowPartialSearchResults()));
        }
        params.putParam("batched_reduce_size", Integer.toString(searchRequest.getBatchedReduceSize()));
        if (searchRequest.scroll() != null) {
            params.putParam("scroll", searchRequest.scroll().keepAlive());
        }
    }

    static class Params {
        private final Request request;

        Params(Request request) {
            this.request = request;
        }

        Params putParam(String name, String value) {
            if (Strings.hasLength(value)) {
                request.addParameter(name, value);
            }
            return this;
        }

        Params putParam(String key, TimeValue value) {
            if (value != null) {
                return putParam(key, value.getStringRep());
            }
            return this;
        }

        Params withPreference(String preference) {
            return putParam("preference", preference);
        }

        /**
         * @deprecated If creating a new HLRC ReST API call, use {@link RefreshPolicy}
         * instead of {@link WriteRequest.RefreshPolicy} from the server project
         */
        @Deprecated
        Params withRefreshPolicy(WriteRequest.RefreshPolicy refreshPolicy) {
            if (refreshPolicy != WriteRequest.RefreshPolicy.NONE) {
                return putParam("refresh", refreshPolicy.getValue());
            }
            return this;
        }

        Params withRouting(String routing) {
            return putParam("routing", routing);
        }

        /**
         * @deprecated <code>copy_settings</code> can not be set to false. If unset, behaves as <code>false</code> and won't copy settings.
         */
        @Deprecated
        Params withCopySettings(Boolean setCopySettings) {
            if (setCopySettings != null) {
                return putParam("copy_settings", setCopySettings.toString());
            }
            return this;
        }

        Params withIndicesOptions(IndicesOptions indicesOptions) {
            if (indicesOptions != null) {
                withIgnoreUnavailable(indicesOptions.ignoreUnavailable());
                putParam("allow_no_indices", Boolean.toString(indicesOptions.allowNoIndices()));
                String expandWildcards;
                if (indicesOptions.expandWildcardsOpen() == false && indicesOptions.expandWildcardsClosed() == false) {
                    expandWildcards = "none";
                } else {
                    StringJoiner joiner = new StringJoiner(",");
                    if (indicesOptions.expandWildcardsOpen()) {
                        joiner.add("open");
                    }
                    if (indicesOptions.expandWildcardsClosed()) {
                        joiner.add("closed");
                    }
                    expandWildcards = joiner.toString();
                }
                putParam("expand_wildcards", expandWildcards);
                putParam("ignore_throttled", Boolean.toString(indicesOptions.ignoreThrottled()));
            }
            return this;
        }

        Params withIgnoreUnavailable(boolean ignoreUnavailable) {
            // Always explicitly place the ignore_unavailable value.
            putParam("ignore_unavailable", Boolean.toString(ignoreUnavailable));
            return this;
        }
    }

    /**
     * Utility class to build request's endpoint given its parts as strings
     */
    static class EndpointBuilder {

        private final StringJoiner joiner = new StringJoiner("/", "/", "");

        EndpointBuilder addPathPart(String... parts) {
            for (String part : parts) {
                if (Strings.hasLength(part)) {
                    joiner.add(encodePart(part));
                }
            }
            return this;
        }

        EndpointBuilder addCommaSeparatedPathParts(String[] parts) {
            addPathPart(String.join(",", parts));
            return this;
        }

        EndpointBuilder addPathPartAsIs(String... parts) {
            for (String part : parts) {
                if (Strings.hasLength(part)) {
                    joiner.add(part);
                }
            }
            return this;
        }

        String build() {
            return joiner.toString();
        }

        private static String encodePart(String pathPart) {
            try {
                //encode each part (e.g. index, type and id) separately before merging them into the path
                //we prepend "/" to the path part to make this path absolute, otherwise there can be issues with
                //paths that start with `-` or contain `:`
                URI uri = new URI(null, null, null, -1, "/" + pathPart, null, null);
                //manually encode any slash that each part may contain
                return uri.getRawPath().substring(1).replace("/", "%2F");
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Path part [" + pathPart + "] couldn't be encoded", e);
            }
        }
    }
}
