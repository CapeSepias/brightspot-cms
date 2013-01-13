<%@ page import="

com.psddev.cms.db.Content,
com.psddev.cms.db.Page,
com.psddev.cms.db.Template,
com.psddev.cms.tool.Search,
com.psddev.cms.tool.SearchSort,
com.psddev.cms.tool.ToolPageContext,

com.psddev.dari.db.ObjectType,

com.psddev.dari.util.ObjectUtils,

java.util.ArrayList,
java.util.Collections,
java.util.HashSet,
java.util.Iterator,
java.util.List,
java.util.Map,
java.util.Set,
java.util.TreeSet,
java.util.UUID
" %><%

// --- Logic ---

ToolPageContext wp = new ToolPageContext(pageContext);
String resultTarget = wp.createId();
Search search = null;
String searchName = (String) request.getAttribute("searchName");

if (!wp.param(boolean.class, "reset") && searchName != null) {
    Map<String, Object> searchSettings = (Map<String, Object>) wp.getUserSetting("search." + searchName);

    if (searchSettings != null) {
        search = new Search(wp);
        search.getState().setValues(searchSettings);
    }
}

if (search == null) {
    UUID[] validTypeIds = (UUID[]) request.getAttribute("validTypeIds");

    if (ObjectUtils.isBlank(validTypeIds)) {
        Class<?> validTypeClass = (Class<?>) request.getAttribute("validTypeClass");

        if (validTypeClass != null) {
            validTypeIds = new UUID[] { ObjectType.getInstance(validTypeClass).getId() };
        }
    }

    if (validTypeIds != null) {
        search = new Search(wp, validTypeIds);

    } else {
        search = new Search(wp);
    }
}

Set<ObjectType> validTypes = search.findValidTypes();
ObjectType selectedType = search.getSelectedType();

if (validTypes.isEmpty()) {
    validTypes.addAll(ObjectType.getInstance(Content.class).findConcreteTypes());
}

// Segregate the valid types into main and misc.
List<ObjectType> templatedTypes = Template.Static.findUsedTypes(wp.getSite());
List<ObjectType> mainTypes = new ArrayList<ObjectType>(validTypes);
List<ObjectType> miscTypes = new ArrayList<ObjectType>();

for (Iterator<ObjectType> i = mainTypes.iterator(); i.hasNext(); ) {
    ObjectType type = i.next();

    if (!templatedTypes.contains(type)) {
        i.remove();
        miscTypes.add(type);
    }
}

Collections.sort(mainTypes);
Collections.sort(miscTypes);

String newJsp = (String) request.getAttribute("newJsp");
String newTarget = (String) request.getAttribute("newTarget");

// --- Presentation ---

%><div class="searchForm">
    <div class="searchForm-controls">
        <div class="searchForm-controlsFilters">
            <h2>Filters</h2>

            <form action="<%= wp.url(request.getAttribute("resultJsp")) %>" class="autoSubmit" method="get" target="<%= resultTarget %>">
                <input type="hidden" name="name" value="<%= wp.h(searchName) %>">

                <% for (ObjectType type : search.getRequestedTypes()) { %>
                    <input name="<%= Search.REQUESTED_TYPES_PARAMETER %>" type="hidden" value="<%= type.getId() %>">
                <% } %>

                <input name="<%= Search.IS_ONLY_PATHED %>" type="hidden" value="<%= wp.boolParam(Search.IS_ONLY_PATHED) %>">
                <input name="<%= Search.ADDITIONAL_QUERY_PARAMETER %>" type="hidden" value="<%= wp.h(wp.param(Search.ADDITIONAL_QUERY_PARAMETER)) %>">
                <input name="<%= Search.PARENT_PARAMETER %>" type="hidden" value="<%= wp.h(wp.param(Search.PARENT_PARAMETER)) %>">
                <input type="hidden" name="<%= Search.OFFSET_PARAMETER %>" value="<%= wp.h(search.getOffset()) %>">
                <input type="hidden" name="<%= Search.LIMIT_PARAMETER %>" value="<%= wp.h(search.getLimit()) %>">

                <span class="searchInput">
                    <label for="<%= wp.createId() %>">Search</label>
                    <input id="<%= wp.getId() %>" class="autoFocus" name="<%= Search.QUERY_STRING_PARAMETER %>" type="text" value="<%= wp.h(search.getQueryString()) %>">
                    <button>Go</button>
                </span>

                <% if (mainTypes.size() + miscTypes.size() > 1) { %>
                    <select name="<%= Search.SELECTED_TYPE_PARAMETER %>" data-searchable="true">
                        <option value="">All Types</option>
                        <% if (mainTypes.size() > 0) { %>
                            <optgroup label="Main Content Types">
                                <% for (ObjectType type : mainTypes) { %>
                                    <option<%= type.equals(selectedType) ? " selected" : "" %> value="<%= wp.h(type.getId()) %>"><%= wp.objectLabel(type) %></option>
                                <% } %>
                            </optgroup>
                        <% } %>
                        <% if (miscTypes.size() > 0) { %>
                            <optgroup label="Misc Content Types">
                                <%
                                String previousName = null;
                                for (ObjectType type : miscTypes) {
                                    String currentName = type.getLabel();
                                    %>
                                    <option<%= type.equals(selectedType) ? " selected" : "" %> value="<%= wp.h(type.getId()) %>"><%= wp.objectLabel(type) %>
                                        <% if (ObjectUtils.equals(previousName, currentName)) { %>
                                            (<%= wp.h(type.getObjectClassName()) %>)
                                        <% } %>
                                    </option>
                                    <%
                                    previousName = currentName;
                                }
                                %>
                            </optgroup>
                        <% } %>
                    </select>
                <% } %>
            </form>

            <a class="action-reset" href="<%= wp.url("", "reset", true) %>">Reset</a>
        </div>

        <% if (!ObjectUtils.isBlank(newJsp)) { %>
            <div class="searchForm-controlsCreate">
                <h2>Create</h2>

                <form action="<%= wp.url(newJsp) %>" method="get"<%= ObjectUtils.isBlank(newTarget) ? "" : " target=\"" + newTarget + "\"" %>>
                    <select name="typeId" data-searchable="true">
                        <% if (mainTypes.size() > 0) { %>
                            <optgroup label="Main Content Types">
                                <% for (ObjectType type : mainTypes) { %>
                                    <option<%= type.equals(selectedType) ? " selected" : "" %> value="<%= wp.h(type.getId()) %>"><%= wp.objectLabel(type) %></option>
                                <% } %>
                            </optgroup>
                        <% } %>
                        <% if (miscTypes.size() > 0) { %>
                            <optgroup label="Misc Content Types">
                                <% for (ObjectType type : miscTypes) { %>
                                    <option<%= type.equals(selectedType) ? " selected" : "" %> value="<%= wp.h(type.getId()) %>"><%= wp.objectLabel(type) %></option>
                                <% } %>
                            </optgroup>
                        <% } %>
                    </select>

                    <input type="submit" value="New" />
                </form>
            </div>
        <% } %>
    </div>

    <div class="searchForm-result frame" name="<%= resultTarget %>">
    </div>
</div>
