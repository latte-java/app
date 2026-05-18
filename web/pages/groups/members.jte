@import org.lattejava.app.model.view.MainView
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.Member
@import org.lattejava.app.model.MembershipState
@import org.lattejava.app.model.Role
@import org.lattejava.app.error.Errors
@import java.util.List
@param MainView view
@param Group group
@param String activeTab = "members"
@param String inviteEmail = ""
@param String inviteRole = "CONTRIBUTOR"
@param Errors errors = new Errors()
@param List<Member> members = java.util.List.of()

!{java.util.List<org.lattejava.app.error.Error> emailErrors = errors.fieldErrors.getOrDefault("email", java.util.List.of());}
!{String emailError = emailErrors.isEmpty() ? null : emailErrors.get(0).message;}

<div class="flex items-center mb-3.5">
  <p class="m-0 text-sm text-slate-600 dark:text-slate-300">
    Access to
    @template.components.italic-name(text = group.name(), size = "text-sm").
  </p>
  <div class="flex-1"></div>
  @template.components.button(label = "Invite member", variant = "primary", icon = "plus", href = "?invite=1#invite")
</div>

@if(!errors.empty() || !inviteEmail.isEmpty())
  <form id="invite" method="post" action="/app/groups/${group.name()}/members"
        class="bg-white dark:bg-slate-900 border border-sky-300 dark:border-sky-800 rounded-lg shadow-sm p-5 mb-4">
    <div class="flex items-center gap-2.5 mb-3">
      <div
          class="inline-flex items-center justify-center w-8 h-8 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400">
        @template.components.icon(name = "users", size = 16)
      </div>
      <div class="flex-1">
        <h3 class="m-0 text-base font-bold text-slate-900 dark:text-slate-100">Invite a member</h3>
        <p class="m-0 mt-0.5 text-xs text-slate-500 dark:text-slate-400">They'll get an email and stay <em>invited</em>
          until they accept.</p>
      </div>
      <a class="inline-flex items-center justify-center w-8 h-8 rounded-md text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
         href="/app/groups/${group.name()}/members" aria-label="Cancel">
        @template.components.icon(name = "x", size = 15)
      </a>
    </div>
    <div class="grid grid-cols-[1fr_200px] gap-3">
      @template.components.input(name = "email", label = "Email", type = "email", value = inviteEmail, placeholder = "teammate@company.com", required = true, autofocus = true, error = emailError)
      <div>
        <label class="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5" for="f-role">Role</label>
        @template.pages.groups.role-picker(value = inviteRole, name = "role")
      </div>
    </div>
    <div class="flex gap-2 justify-end mt-4">
      @template.components.button(label = "Cancel", variant = "ghost", href = "/app/groups/" + group.name() + "/members")
      @template.components.button(label = "Send invitation", variant = "primary", icon = "send", type = "submit")
    </div>
  </form>
@endif

<div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
  @for(Member m : members)
    @template.components.member-row(member = m, canManage = true, groupName = group.name())
  @endfor
  @if(members.isEmpty())
    @template.components.empty-state(title = "No members yet", body = "Invite team members to collaborate on this group.")
  @endif
</div>
<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-2 gap-3 mt-6">
  @for(Role r : Role.values())
    <div class="p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg">
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">${r.label()}</div>
      <p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400 leading-relaxed">${r.description()}</p>
    </div>
  @endfor
</div>
