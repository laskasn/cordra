package net.cnri.cordra.api;

import java.util.Collections;
import java.util.List;

/**
 * Parameters to a repository search, such as pagination and sorting.
 */
public class QueryParams {
    /**
     * Default query parameters.  Passing {@code null} to repository search methods amounts to using this.  No pagination and no sorting.
     */
    public static final QueryParams DEFAULT = new QueryParams(0, -1);


    private final List<SortField> sortFields;
    private final int pageNumber;
    private final int pageSize;
    /**
     * Construct a QueryParams.
     * @param pageNumber the page number to return.  Starts at 0.  Ignored if pageSize &lt;= 0.
     * @param pageSize the number of objects to return.  PageSize of &lt; 0 means return all.
     */
    public QueryParams(int pageNumber, int pageSize) {
        this(pageNumber, pageSize, Collections.emptyList());
    }

    public QueryParams(int pageNumber, int pageSize, List<SortField> sortFields) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.sortFields = sortFields;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public List<SortField> getSortFields() {
        return sortFields;
    }
}
