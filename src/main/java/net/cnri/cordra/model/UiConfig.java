package net.cnri.cordra.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiConfig {
    public String title;
    public String relationshipsButtonText;
    public Boolean allowUserToSpecifySuffixOnCreate;
    public Boolean allowUserToSpecifyHandleOnCreate;
    public Boolean hideTypeInObjectEditor;
    public SearchResultConfig searchResults;
    public String initialQuery = "*:*";
    public String initialFragment;
    public String initialSortFields;
    public String initialFilter;
    public List<NavBarLink> navBarLinks;
    public Integer numTypesForCreateDropdown = null;
    public List<String> aclUiSearchTypes = null;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SearchResultConfig {
        public Boolean includeType;
        public Boolean includeModifiedDate;
        public Boolean includeCreatedDate;
    }
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NavBarLink {
        public String type;
        public String title;
        public String query;
        public String sortFields;
        public String url;
        public Integer maxItems;
    }
}
