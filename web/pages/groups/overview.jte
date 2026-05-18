@import org.lattejava.app.model.Group
@import org.lattejava.app.model.GroupState
@import org.lattejava.app.model.Member
@import org.lattejava.app.model.MembershipState
@param Group group
@param Member viewerMembership = null

<div class="grid grid-cols-1 lg:grid-cols-5 gap-4 items-start">
  <div class="lg:col-span-3">
    @if(viewerMembership != null && viewerMembership.state() == MembershipState.PENDING)
      @template.components.admonition(tone="attention", shadow=true, white=true, content=@`
        <div class="flex-1">
          <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">You've been invited</div>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">Accept to join this group.</p>
        </div>
        <form method="post" action="/app/groups/${group.name()}/members/${viewerMembership.userId().toString()}/accept" class="inline mr-2">
          <button type="submit" class="px-3 py-1.5 text-sm font-medium text-white bg-sky-600 rounded-md hover:bg-sky-700">
            Accept
          </button>
        </form>
        <form method="post" action="/app/groups/${group.name()}/members/${viewerMembership.userId().toString()}/decline" class="inline">
          <button type="submit" class="px-3 py-1.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-md hover:bg-slate-50 dark:bg-slate-900 dark:text-slate-300 dark:border-slate-700">
            Decline
          </button>
        </form>
      `)
    @endif

    @if(group.state() == GroupState.PENDING)
      @template.components.admonition(tone="warn", white=false, icon="clock",
      content=@`
        <div class="flex-1 text-sm font-semibold text-slate-900 dark:text-slate-100">Group verification is pending</div>
        @template.components.button(label="Continue", variant="primary", size="sm", href="/app/groups/" + group.name() + "/verify")
      `)
    @elseif(group.state() == GroupState.FAILED)
      @template.components.admonition(tone="danger", white=false,
      content=@`
        <div class="flex-1 text-sm font-semibold text-slate-900 dark:text-slate-100">You must verify this group to publish artifacts to it</div>
        @template.components.button(label="Verify", variant="primary", size="sm", href="/app/groups/" + group.name() + "/verify")
      `)
    @endif

    @template.components.card(header=@`
      @template.components.card-header(nameContent=@`Publish artifacts for @template.components.italic-name(text=group.name(), size = "text-sm")`)
    `,
    content=@`
      @template.components.code-block(command = "latte release")
    `)

    @template.components.card(
    header=@`
      @template.components.card-header(name="Top artifacts", content=@`<span class="ml-auto text-xs font-medium text-slate-500">last 30 days</span>`
    )`,
    content=@`
      @template.components.empty-state(title = "No artifacts published yet", body = "Once verified, run latte publish in your project.")
    `)
  </div>

  <div class="lg:col-span-2">
    @template.components.card(
    header=@`
      @template.components.card-header(name="ABOUT", separator=false)
    `,
    content=@`
      @template.components.field-row(label="Group ID", value=group.name(), monospace=true)
      @template.components.field-row(label="Visibility", value="Public")
      @template.components.field-row(label="Description", value=group.description())
    `)
  </div>
</div>
