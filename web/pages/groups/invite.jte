@import org.lattejava.app.model.view.MainView
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.InviteRequest
@import org.lattejava.app.model.Role
@import org.lattejava.app.error.Error
@import org.lattejava.app.error.Errors
@import java.util.List
@param MainView view
@param Group group
@param InviteRequest inviteRequest
@param Errors errors = new Errors()

!{List<Error> emailErrors = errors.fieldErrors.getOrDefault("email", java.util.List.of());}
!{String emailError = emailErrors.isEmpty() ? null : emailErrors.get(0).message;}

@template.layout.main(view=view, pageTitle="Invite a member", activeNav="groups", activeGroupId=group.name(),
content=@`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/">Groups</a>
    @template.components.icon(name="chevron-right", size=11)
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/${group.name()}/members/">${group.name()}</a>
    @template.components.icon(name="chevron-right", size=11)
    <span>Invite</span>
  </nav>
  @template.components.page-header(title="Invite a member", subtitle="They'll get an email and stay invited until they accept.")
  @if(!errors.generalErrors.isEmpty())
    <div class="mb-4 p-4 border border-red-300 bg-red-50 dark:bg-red-950/40 rounded-lg text-sm text-red-700 dark:text-red-300">
      @for(Error err : errors.generalErrors)
        <p class="m-0">${err.message}</p>
      @endfor
    </div>
  @endif

  <form class="max-w-2xl" method="post" action="/app/groups/${group.name()}/members/invite">
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Email</h3>
    @template.components.input(name="email", value=inviteRequest.email(), type="email", placeholder="teammate@company.com", required=true, autofocus=true, error=emailError)
    <div class="h-6"></div>
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Role</h3>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      @template.components.radio-card(name="role", value="CONTRIBUTOR", icon="users", label=Role.CONTRIBUTOR.label(), description=Role.CONTRIBUTOR.description(), checked=inviteRequest.role() == Role.CONTRIBUTOR)
      @template.components.radio-card(name="role", value="OWNER", icon="shield", label=Role.OWNER.label(), description=Role.OWNER.description(), checked=inviteRequest.role() == Role.OWNER)
    </div>
    <div class="h-6"></div>
    <div class="flex gap-2 justify-end">
      @template.components.button(label="Cancel", variant="ghost", href="/app/groups/" + group.name() + "/members/")
      @template.components.button(label="Send invitation", variant="primary", icon="send", type="submit")
    </div>
  </form>
`)
