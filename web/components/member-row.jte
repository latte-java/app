<%--
    Member row. Shows role picker + remove for active members,
    "Invited" pill + cancel for outstanding invitations.
--%>
@import org.lattejava.app.model.Member
@param Member member
@param String groupId
@param boolean canManage = true

!{boolean isOwner = member.role().name().equals("OWNER");}
!{String displayName = member.name() != null ? member.name() : member.email();}

<form
    class="grid items-center gap-3.5 px-5 py-3.5 border-t border-slate-200 dark:border-slate-700 first:border-t-0 ${member.invited() ? "bg-amber-50/30 dark:bg-amber-950/10" : ""}"
    style="grid-template-columns: 36px 1.6fr 1fr 1fr 60px;">
  @if(member.invited())
    <div
        class="inline-flex items-center justify-center w-9 h-9 rounded-full bg-amber-100 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 shrink-0">
      @template.components.icon(name = "clock", size = 16)
    </div>
  @else
    @template.components.avatar(initials = member.avatar(), size = 32)
  @endif
  <div class="min-w-0">
    <div class="flex items-center gap-2">
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">${displayName}</div>
      @if(member.invited())
        @template.components.badge(label = "invited", tone = "warn")
      @endif
    </div>
    <div class="text-xs text-slate-500 dark:text-slate-400 truncate">${member.email()}</div>
  </div>
  <div class="text-xs text-slate-500 dark:text-slate-400">
    @if(member.invited())
      Awaiting acceptance
    @else
      Joined ${member.joined()}
    @endif
  </div>
  @if(canManage && !isOwner)
    @template.pages.groups.role-picker(value = member.role().name())
  @else
    <div class="text-sm font-medium text-slate-700 dark:text-slate-200">${member.role().label()}</div>
  @endif
  <div class="flex justify-end">
    @if(canManage && !isOwner)
      @template.components.icon-button(icon = "trash", ariaLabel = member.invited() ? "Cancel invitation" : "Remove", variant = "danger")
    @endif
  </div>
</form>
