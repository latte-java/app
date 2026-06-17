@import org.lattejava.app.model.Group
@import org.lattejava.app.model.GroupState
@import org.lattejava.app.model.view.GroupView
@import org.lattejava.app.error.Errors
@param GroupView groupView
@param Errors errors = new Errors()

!{Group group = groupView.group();}
!{java.util.List<org.lattejava.app.error.Error> descErrors = errors.fieldErrors.getOrDefault("description", java.util.List.of());}
!{String descError = descErrors.isEmpty() ? null : descErrors.get(0).message();}

<div class="max-w-3xl grid gap-4">
  @if(groupView.canManage())
    @template.components.card(header=@`@template.components.card-header(name="General")`, content=@`
      <form method="post" action="/app/groups/${group.name()}/settings">
        @template.components.input(disabled=true, name="name", label="Name", value=group.name(), hint="Group IDs are permanent. To use a different ID, create a new group.")
        @template.components.textarea(name="description", label="Description", value=group.description() != null ? group.description() : "", hint="Shown publicly on artifact pages.", error=descError)
        <div class="flex justify-end mt-4">
          @template.components.button(label="Save", variant="primary", type="submit")
        </div>
      </form>
    `)

    @template.components.card(header=@`@template.components.card-header(name="Domain ownership")`, content=@`
      <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">
        Reverse-domain groups must own the matching domain.
      </p>
      !{var status = group.state() == GroupState.VERIFIED ? "Verified" : group.state() == GroupState.PENDING ? "Pending" : "Not verified";}
      !{var tone = group.state() == GroupState.VERIFIED ? "success" : "warn";}
      @template.components.admonition(content=@`${status}`, tone=tone, cssClass="mt-4")
      @if(group.state() != GroupState.VERIFIED)
        <div class="mt-3.5">
          @template.components.button(label="Start verification", variant="secondary", icon="shield", href="/groups/" + group.name() + "/verify")
        </div>
      @endif
    `)
  @endif

  @template.components.card(tone="danger", header=@`@template.components.card-header(tone="danger", name="Danger zone")`, content=@`
    <div class="mb-4">
      <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">
        Leaving removes your access to this group's private artifacts. You can be re-invited later.
      </p>
      @template.components.button(label="Leave group", variant="danger", icon="logout", href="/app/groups/" + group.name() + "/members/leave")
    </div>
    @if(groupView.canManage())
      <div class="mt-6 p-5 border border-red-300 dark:border-red-800 rounded-lg bg-red-50/30 dark:bg-red-950/20">
        <h3 class="m-0 mb-2 text-base font-bold text-red-700 dark:text-red-300">Delete group</h3>
        <p class="m-0 mb-4 text-sm text-slate-600 dark:text-slate-300">
          Deleting a group is permanent. This will remove the group, its members, and any pending verifications. You can only delete groups that have no published artifacts.
        </p>
        @template.components.button(label="Delete group", variant="danger", href="/app/groups/" + group.name() + "/delete")
      </div>
    @endif
  `)
</div>
