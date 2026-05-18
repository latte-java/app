@import org.lattejava.app.model.view.MainView
@import org.lattejava.app.model.Group
@import java.util.List
@param MainView view
@param List<Group> groups
@param String filter = ""

@template.layout.main(view=view, pageTitle="Groups", activeNav="groups",
content=@`
  @template.components.page-header(title="Groups", subtitle="Reverse-domain groups you publish to. Verified groups can be used by anyone you invite.",
  actions=@`
    @template.components.button(label = "New group", variant = "primary", icon = "plus", href = "/app/groups/new")
  `)
  <form class="flex gap-2 mb-4" method="get" action="/app/groups/">
    <div class="flex-1">
      @template.components.input(name = "q", value = filter, placeholder = "Filter groups...")
    </div>
    @template.components.button(label = "Filter", variant = "secondary", icon = "filter", type = "submit")
  </form>
  @template.components.card(paddedContent=false,
  content=@`
    @for(Group g : groups)
      @template.components.group-list-row(group=g)
    @endfor
  `)
`)
