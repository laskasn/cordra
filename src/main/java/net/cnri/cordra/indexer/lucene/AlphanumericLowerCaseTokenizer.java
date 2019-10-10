package net.cnri.cordra.indexer.lucene;

import org.apache.lucene.analysis.core.LetterTokenizer;

public class AlphanumericLowerCaseTokenizer extends LetterTokenizer {

    public AlphanumericLowerCaseTokenizer() {
        super();
    }

    @Override
    protected boolean isTokenChar(int c) {
        return Character.isLetterOrDigit(c);
    }
}
