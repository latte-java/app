@import org.lattejava.app.model.View
@import org.lattejava.app.model.Group
@import java.util.List
@param View view
@param List<Group> groups
@param String filter = ""

@template.layout.main(view = view, pageTitle = "Groups", activeNav = "groups", sidebarGroups = groups, content = @`
  @template.components.page-header(
  title = "Groups",
  subtitle = "Reverse-domain groups you publish to. Verified groups can be used by anyone you invite.",
  actions = @`
    @template.components.button(label = "New group", variant = "primary", icon = "plus", href = "/groups/new")
  `
  )
  <form class="flex gap-2 mb-4" method="get" action="/groups">
    <div class="flex-1">
      @template.components.input(name = "q", value = filter, placeholder = "Filter groups...")
    </div>
    @template.components.button(label = "Status", variant = "secondary", icon = "filter", type = "submit")
  </form>
  <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
    @for(Group g : groups)
      @template.components.group-list-row(group = g)
    @endfor
  </div>
`)
