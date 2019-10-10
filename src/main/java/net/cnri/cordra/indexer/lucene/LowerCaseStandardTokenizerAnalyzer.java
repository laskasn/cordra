package net.cnri.cordra.indexer.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public class LowerCaseStandardTokenizerAnalyzer extends Analyzer {
    @Override
    @SuppressWarnings("resource")
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        TokenStream lowerCastFilter = new LowerCaseFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, lowerCastFilter);
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
