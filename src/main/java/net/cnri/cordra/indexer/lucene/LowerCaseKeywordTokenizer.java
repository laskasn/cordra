package net.cnri.cordra.indexer.lucene;

import org.apache.lucene.analysis.core.LetterTokenizer;

public class LowerCaseKeywordTokenizer extends LetterTokenizer {
    public LowerCaseKeywordTokenizer() {
        super();
    }

    @Override
    protected boolean isTokenChar(int c) {
        return true;
    }
}
