@import org.lattejava.app.model.view.MainView
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.Member
@import org.lattejava.app.model.Role
@import org.lattejava.app.error.Error
@import org.lattejava.app.error.Errors
@param MainView view
@param Group group
@param Member member
@param Role selectedRole
@param Errors errors = new Errors()

@template.layout.main(view=view, pageTitle="Change role", activeNav="groups", activeGroupId=group.name(),
content=@`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/">Groups</a>
    @template.components.icon(name="chevron-right", size=11)
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/${group.name()}/members/">${group.name()}</a>
    @template.components.icon(name="chevron-right", size=11)
    <span>Change role</span>
  </nav>
  @template.components.page-header(title="Change role", subtitle="Update the role for " + member.user().email() + " in this group.")
  @if(!errors.generalErrors.isEmpty())
    <div class="mb-4 p-4 border border-red-300 bg-red-50 dark:bg-red-950/40 rounded-lg text-sm text-red-700 dark:text-red-300">
      @for(Error err : errors.generalErrors)
        <p class="m-0">${err.message}</p>
      @endfor
    </div>
  @endif

  <form class="max-w-2xl" method="post" action="/app/groups/${group.name()}/members/${member.userId().toString()}/role">
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Role</h3>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      @template.components.radio-card(name="role", value="CONTRIBUTOR", icon="users", label=Role.CONTRIBUTOR.label(), description=Role.CONTRIBUTOR.description(), checked=selectedRole == Role.CONTRIBUTOR)
      @template.components.radio-card(name="role", value="OWNER", icon="shield", label=Role.OWNER.label(), description=Role.OWNER.description(), checked=selectedRole == Role.OWNER)
    </div>
    <div class="h-6"></div>
    <div class="flex gap-2 justify-end">
      @template.components.button(label="Cancel", variant="ghost", href="/app/groups/" + group.name() + "/members/")
      @template.components.button(label="Save role", variant="primary", icon="check", type="submit")
    </div>
  </form>
`)
