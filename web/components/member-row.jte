<%--
    Member row.
--%>
@import org.lattejava.app.model.Member
@import org.lattejava.app.model.MembershipState
@import org.lattejava.app.model.Role
@import java.util.UUID
@param Member member
@param boolean canManage = true
@param String groupName = ""
@param UUID viewerUserId = null

!{boolean isPending = member.state() == MembershipState.PENDING;}
!{boolean isSelf = viewerUserId != null && member.userId().equals(viewerUserId);}

<div class="grid grid-cols-[36px_1.6fr_1fr_1fr_60px] items-center gap-3.5 px-5 py-3.5 border-t border-slate-200 dark:border-slate-700 first:border-t-0 ${isPending ? "bg-amber-50/30 dark:bg-amber-950/10" : ""}">
  @if(isPending)
    <div class="inline-flex items-center justify-center w-9 h-9 rounded-full bg-amber-100 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 shrink-0">
      @template.components.icon(name = "clock", size = 16)
    </div>
  @else
    @template.components.avatar(email=member.user().email(), size=32)
  @endif
  <div class="min-w-0">
    <div class="flex items-center gap-2">
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">${member.user().email()}</div>
      @if(isPending)
        @template.components.badge(label = "invited", tone = "warn")
      @endif
    </div>
    @if(!isPending)
      <div class="text-xs text-slate-500 dark:text-slate-400 truncate">${member.user().username()}</div>
    @endif
  </div>
  <div class="text-xs text-slate-500 dark:text-slate-400">
    @if(isPending)
      Awaiting acceptance
    @else
      <span>Active</span>
    @endif
  </div>
  @if(canManage && !isPending && !isSelf)
    <div class="flex items-center gap-2 text-sm">
      <span class="font-medium text-slate-700 dark:text-slate-200">${member.role().label()}</span>
      <a class="text-xs text-sky-600 dark:text-sky-400 hover:underline no-underline" href="/app/groups/${groupName}/members/${member.userId().toString()}/role">Change role</a>
    </div>
  @else
    <div class="text-sm font-medium text-slate-700 dark:text-slate-200">${member.role().label()}</div>
  @endif
  <div class="flex justify-end">
    @if(canManage && !isPending && !isSelf)
      <a class="text-sm text-red-600 dark:text-red-400 hover:underline no-underline" href="/app/groups/${groupName}/members/${member.userId().toString()}/remove">Remove</a>
    @endif
    @if(canManage && isPending && !isSelf)
      <form method="post" action="/app/groups/${groupName}/members/${member.userId().toString()}/remove" class="inline">
        <button type="submit" class="text-sm text-slate-500 dark:text-slate-400 hover:underline">Cancel invitation</button>
      </form>
    @endif
  </div>
</div>
