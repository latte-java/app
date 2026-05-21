@import org.lattejava.app.model.view.MainView
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.GroupState
@import java.util.*

@param MainView view

!{List<Group> groups = view.groupsForSidebar();}
!{List<Group> top5 = groups.stream().limit(5).toList();}
!{long verified = groups.stream().filter(g -> g.state() == GroupState.VERIFIED).count();}
!{long pending  = groups.stream().filter(g -> g.state() == GroupState.PENDING).count();}
!{var title = view.viewer().username();}

@template.layout.main(view=view, pageTitle="Dashboard", activeNav="dashboard",
content=@`
  @template.components.page-header(eyebrow="Welcome back", title=title, subtitle="Publish and manage Java artifacts on the Latte repository.",
  actions=@`
    @template.components.button(label = "New group",  variant = "primary",   icon = "plus", href = "/app/groups/new")
  `)

  <div class="grid grid-cols-1 gap-3 mb-7">
    @template.components.stat(label = "Groups", value = String.valueOf(groups.size()), sub = verified + " verified" + (pending > 0 ? ", " + pending + " pending" : ""))
  </div>

  @template.components.card(
  header=@`
    @template.components.card-header(name="Your groups",
    content=@`
      <a class="ml-auto inline-flex items-center gap-1 text-sm font-medium text-slate-500 hover:text-sky-600 no-underline" href="/app/groups/">
        See all @template.components.icon(name = "arrow-right", size = 12)
      </a>
    `)
  `,
  content=@`
    @for(Group g : top5)
      <div class="flex items-center gap-2 px-5 py-3 border-t border-slate-200 dark:border-slate-700 first:border-t-0">
        @template.components.italic-name(text = g.name(), size = "text-sm")
        @template.components.verified-mark(status = g.state())
      </div>
    @endfor
  `)
`)
