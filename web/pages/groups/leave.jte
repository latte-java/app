@import org.lattejava.app.model.view.GroupView
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.Member
@param GroupView groupView

!{Group group = groupView.group();}
!{Member viewerMembership = groupView.viewerMembership();}

@template.layout.main(view=groupView.mainView(), pageTitle="Leave " + group.name(), activeNav="groups", activeGroupId=group.name(),
content=@`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/">Groups</a>
    @template.components.icon(name="chevron-right", size=11)
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/${group.name()}/">${group.name()}</a>
    @template.components.icon(name="chevron-right", size=11)
    <span>Leave group</span>
  </nav>
  @template.components.page-header(title="Leave " + group.name(), subtitle="Confirm you want to leave this group.")

  <form class="max-w-2xl" method="post" action="/app/groups/${group.name()}/members/leave">
    <div class="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
      <p class="m-0 text-sm text-slate-700 dark:text-slate-200">
        Are you sure you want to leave <strong class="text-slate-900 dark:text-slate-100">${group.name()}</strong> as ${viewerMembership.role().label()}?
      </p>
      <p class="m-0 mt-2 text-xs text-slate-500 dark:text-slate-400">You will lose access immediately. You can be re-invited later.</p>
    </div>
    <div class="h-6"></div>
    <div class="flex gap-2 justify-end">
      @template.components.button(label="Cancel", variant="ghost", href="/app/groups/" + group.name() + "/settings")
      @template.components.button(label="Leave group", variant="danger", type="submit")
    </div>
  </form>
`)
