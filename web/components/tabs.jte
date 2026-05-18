<%--
    Tab bar. Tabs are rendered as <a> links so navigation is server-side.

    Usage:
        @template.components.tabs(
            tabs = List.of(
                new Tab("overview", "Overview", null, "/groups/io-nimbusworks"),
                new Tab("artifacts", "Artifacts", group.artifacts().size(), "/groups/io-nimbusworks/artifacts"),
                ...
            ),
            activeId = "overview"
        )
--%>
@import java.util.List
@param List<Tab> tabs
@param String activeId

<%--
    Tab is declared inside the template package and used by callers.
--%>
!{
    record Tab(String id, String label, Integer count, String href) {}
}

<nav class="tabs" aria-label="Section">
  @for(Tab t : tabs)
    !{boolean active = t.id().equals(activeId);}
    <a class="tabs__tab ${active ? "tabs__tab--active" : ""}" href="${t.href()}">
      <span>${t.label()}</span>
      @if(t.count() != null)
        <span class="tabs__count">${t.count()}</span>
      @endif
    </a>
  @endfor
</nav>
