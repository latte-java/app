@import org.lattejava.app.model.view.GroupView
@import org.lattejava.app.model.Group
@import org.lattejava.app.error.Error
@import org.lattejava.app.error.Errors
@param GroupView groupView
@param Errors errors = new Errors()

!{Group group = groupView.group();}

@template.layout.main(view=groupView.mainView(), pageTitle="Delete " + group.name(), activeNav="groups", activeGroupId=group.name(),
content=@`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/">Groups</a>
    @template.components.icon(name="chevron-right", size=11)
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/${group.name()}/">${group.name()}</a>
    @template.components.icon(name="chevron-right", size=11)
    <span>Delete group</span>
  </nav>
  @template.components.page-header(title="Delete " + group.name(), subtitle="Confirm you want to permanently delete this group.")
  @if(!errors.generalErrors.isEmpty())
    <div class="mb-4 p-4 border border-red-300 bg-red-50 dark:bg-red-950/40 rounded-lg text-sm text-red-700 dark:text-red-300">
      @for(Error err : errors.generalErrors)
        <p class="m-0">${err.message}</p>
      @endfor
    </div>
  @endif

  <form class="max-w-2xl" method="post" action="/app/groups/${group.name()}/delete">
    <div class="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
      <p class="m-0 text-sm text-slate-700 dark:text-slate-200">
        Are you sure you want to permanently delete <strong class="text-slate-900 dark:text-slate-100">${group.name()}</strong>?
      </p>
      <p class="m-0 mt-2 text-xs text-slate-500 dark:text-slate-400">This removes the group, its members, and any pending verifications. You can only delete groups that have no published artifacts.</p>
    </div>
    <div class="h-6"></div>
    <div class="flex gap-2 justify-end">
      @template.components.button(label="Cancel", variant="ghost", href="/app/groups/" + group.name() + "/settings")
      @template.components.button(label="Delete group", variant="danger", type="submit")
    </div>
  </form>
`)
