@import org.lattejava.app.model.Group
@import org.lattejava.app.model.Member
@import org.lattejava.app.model.Role
@param Group group
@param boolean canManage = true
@param boolean inviteOpen = false
@param String inviteEmail = ""
@param String inviteRole = "PUBLISHER"
@param String inviteError = null

!{long activeCount = group.members().stream().filter(m -> !m.invited()).count();}
!{long invitedCount = group.members().size() - activeCount;}
!{String[] descriptions = {
    "Full control. Can transfer ownership and delete the group.",
    "Manage artifacts, members, and verification. Cannot delete.",
    "Publish new versions. Cannot manage members.",
    "Read-only access to private artifact metadata."
};}

<div class="flex items-center mb-3.5">
  <p class="m-0 text-sm text-slate-600 dark:text-slate-300">
    ${activeCount} ${activeCount == 1 ? "member" : "members"}@if(invitedCount > 0), ${invitedCount} invited@endif
    &middot; access to
    @template.components.italic-name(text = group.name(), size = "text-sm").
  </p>
  <div class="flex-1"></div>
  @if(canManage)
    @template.components.button(label = "Invite member", variant = "primary", icon = "plus", href = "?invite=1#invite")
  @endif
</div>

@if(inviteOpen && canManage)
  <form id="invite" method="post" action="/groups/${group.id()}/invite"
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
         href="/groups/${group.id()}/members" aria-label="Cancel">
        @template.components.icon(name = "x", size = 15)
      </a>
    </div>
    @if(inviteError != null)
      <div
          class="flex items-center gap-2.5 px-3.5 py-2.5 mb-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm font-medium">
        @template.components.icon(name = "alert", size = 14)
        ${inviteError}
      </div>
    @endif
    <div class="grid gap-3" style="grid-template-columns: 1fr 200px;">
      @template.components.input(name = "email", label = "Email", type = "email", value = inviteEmail, placeholder = "teammate@company.com", required = true, autofocus = true)
      <div>
        <label class="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5" for="f-role">Role</label>
        <div class="relative">
          <select id="f-role" name="role"
                  class="w-full appearance-none pl-3 pr-9 py-2.5 text-sm font-medium text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md cursor-pointer focus:border-sky-500 focus:ring-2 focus:ring-sky-100 outline-none">
            @for(Role r : Role.values())
              @if(r != Role.OWNER)
                <option value="${r.name()}" @if(r.name().equals(inviteRole))selected @endif>${r.label()}</option>
              @endif
            @endfor
          </select>
          <span class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none inline-flex">
                        @template.components.icon(name = "chevron-down", size = 12)
                    </span>
        </div>
      </div>
    </div>
    <div class="flex gap-2 justify-end mt-4">
      @template.components.button(label = "Cancel", variant = "ghost", href = "/groups/" + group.id() + "/members")
      @template.components.button(label = "Send invitation", variant = "primary", icon = "send", type = "submit")
    </div>
  </form>
@endif

<div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
  @for(Member m : group.members())
    @template.components.member-row(member = m, groupId = group.id(), canManage = canManage)
  @endfor
</div>
<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 mt-6">
  @for(int i = 0; i < Role.values().length; i++)
    !{Role r = Role.values()[i];}
    <div class="p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg">
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">${r.label()}</div>
      <p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400 leading-relaxed">${descriptions[i]}</p>
    </div>
  @endfor
</div>
