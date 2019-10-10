package net.cnri.cordra.indexer.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;

public class LowerCaseKeywordAnalyzer extends Analyzer {
    @Override
    @SuppressWarnings("resource")
    protected TokenStreamComponents createComponents(final String fieldName) {
        Tokenizer source = new LowerCaseKeywordTokenizer();
        TokenStream filter = new LowerCaseFilter(source);
        return new TokenStreamComponents(source, filter);
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
    }

    @Override
    public int getPositionIncrementGap(String fieldName) {
        return 10000;
    }
}
