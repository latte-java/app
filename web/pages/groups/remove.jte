@import org.lattejava.app.model.view.MainView
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.Member
@import org.lattejava.app.error.Error
@import org.lattejava.app.error.Errors
@param MainView view
@param Group group
@param Member member
@param Errors errors = new Errors()

@template.layout.main(view=view, pageTitle="Remove member", activeNav="groups", activeGroupId=group.name(),
content=@`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/">Groups</a>
    @template.components.icon(name="chevron-right", size=11)
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/${group.name()}/members/">${group.name()}</a>
    @template.components.icon(name="chevron-right", size=11)
    <span>Remove member</span>
  </nav>
  @template.components.page-header(title="Remove member", subtitle="Confirm you want to remove this member from " + group.name() + ".")
  @if(!errors.generalErrors.isEmpty())
    <div class="mb-4 p-4 border border-red-300 bg-red-50 dark:bg-red-950/40 rounded-lg text-sm text-red-700 dark:text-red-300">
      @for(Error err : errors.generalErrors)
        <p class="m-0">${err.message()}</p>
      @endfor
    </div>
  @endif

  <form class="max-w-2xl" method="post" action="/app/groups/${group.name()}/members/${member.userId().toString()}/remove">
    <div class="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
      <p class="m-0 text-sm text-slate-700 dark:text-slate-200">
        Are you sure you want to remove <strong class="text-slate-900 dark:text-slate-100">${member.user().email()}</strong> (${member.role().label()}) from <strong class="text-slate-900 dark:text-slate-100">${group.name()}</strong>?
      </p>
      <p class="m-0 mt-2 text-xs text-slate-500 dark:text-slate-400">They will lose access immediately. You can invite them back later.</p>
    </div>
    <div class="h-6"></div>
    <div class="flex gap-2 justify-end">
      @template.components.button(label="Cancel", variant="ghost", href="/app/groups/" + group.name() + "/members/")
      @template.components.button(label="Remove", variant="danger", type="submit")
    </div>
  </form>
`)
