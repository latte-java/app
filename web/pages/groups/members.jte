@import org.lattejava.app.model.Group
@import org.lattejava.app.model.Member
@import org.lattejava.app.model.Role
@import org.lattejava.app.model.view.GroupView
@import java.util.List
@param GroupView groupView
@param List<Member> members = java.util.List.of()

!{Group group = groupView.group();}

<div class="flex items-center mb-3.5">
  <p class="m-0 text-sm text-slate-600 dark:text-slate-300">
    Access to @template.components.italic-name(text=group.name(), size="text-sm")
  </p>
  <div class="flex-1"></div>
  @template.components.button(label="Invite member", variant="primary", icon="plus", href="/app/groups/" + group.name() + "/members/invite")
</div>

<div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
  @for(Member m : members)
    @template.components.member-row(member=m, canManage=true, groupName=group.name(), viewerUserId=groupView.viewer().userId())
  @endfor
  @if(members.isEmpty())
    @template.components.empty-state(title="No members yet", body="Invite team members to collaborate on this group.")
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
