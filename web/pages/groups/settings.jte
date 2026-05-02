@import org.lattejava.app.model.Group
@import org.lattejava.app.model.Member
@import org.lattejava.app.model.Role
@import org.lattejava.app.model.User
@import org.lattejava.app.model.VerificationStatus
@param Group group
@param User viewer

!{boolean viewerIsOwner = group.viewerRole() == Role.OWNER;}
!{long otherOwners = group.members().stream().filter(m -> !m.invited() && m.role() == Role.OWNER && !m.email().equals(viewer.email())).count();}
!{boolean blocked = viewerIsOwner && otherOwners == 0;}

<div class="max-w-3xl grid gap-4">
  <form class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm p-6"
        method="post" action="/groups/${group.id()}/settings">
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">General</h3>
    <div class="mb-4">
      <div class="text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">Group ID</div>
      <div
          class="flex items-center gap-2 px-3 py-2 rounded-md border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 font-mono text-[13.5px] text-slate-700 dark:text-slate-200">
        <span class="text-slate-400 dark:text-slate-500 select-none">latte://</span><span>${group.name()}</span>
      </div>
      <p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">Group IDs are permanent. To use a different ID,
        create a new group.</p>
    </div>
    @template.components.input(name = "description", label = "Description", value = group.description() != null ? group.description() : "", hint = "Shown publicly on artifact pages.")
    <div class="flex justify-end mt-4">
      @template.components.button(label = "Save", variant = "primary", type = "submit")
    </div>
  </form>
  <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm p-6">
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Domain ownership</h3>
    <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">Reverse-domain groups must own the matching domain.
      Re-verifying every 12 months is required.</p>
    @template.components.field-row(label = "Domain", value = group.domain() != null ? group.domain() : "—")
    @template.components.field-row(label = "Status", value = group.verification() == VerificationStatus.VERIFIED ? "Verified" : group.verification() == VerificationStatus.PENDING ? "Pending" : "Not verified")
    @if(group.verification() == VerificationStatus.VERIFIED)
      @template.components.field-row(label = "Last verified", value = group.verifiedOn() != null ? group.verifiedOn().toString() : "—")
    @endif
    @if(group.verification() != VerificationStatus.VERIFIED && !group.handleGroup())
      <div class="mt-3.5">
        @template.components.button(label = "Start verification", variant = "secondary", icon = "shield", href = "/groups/" + group.id() + "/verify")
      </div>
    @endif
  </div>
  <form class="bg-white dark:bg-slate-900 border border-red-300 dark:border-red-800 rounded-lg shadow-sm p-6"
        method="post" action="/groups/${group.id()}/leave">
    <h3 class="m-0 mb-3 text-base font-bold text-red-600">Danger zone</h3>
    @if(blocked)
      <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">You're the only <span
            class="font-medium text-slate-900 dark:text-slate-100">Owner</span> in this group. Promote another member to
        Owner before leaving.</p>
      <button type="button" disabled
              class="inline-flex items-center gap-2 px-3.5 py-2 rounded-md text-sm font-medium border border-slate-200 dark:border-slate-700 text-slate-400 dark:text-slate-500 bg-slate-50 dark:bg-slate-800/50 cursor-not-allowed">
        @template.components.icon(name = "logout", size = 14)
        <span>Leave group</span>
      </button>
    @else
      <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">Leaving removes your access to this group's private
        artifacts. You can be re-invited later.</p>
      @template.components.button(label = "Leave group", variant = "danger", icon = "logout", type = "submit")
    @endif
  </form>
</div>
