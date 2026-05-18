@import org.lattejava.app.model.view.MainView
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.GroupState
@import org.lattejava.app.model.Member
@import org.lattejava.app.error.Errors
@import java.util.List
@param MainView view
@param Group group
@param String activeTab
@param String inviteEmail = ""
@param String inviteRole = "CONTRIBUTOR"
@param String status = ""
@param Errors errors = new Errors()
@param List<Member> members = java.util.List.of()
@param Member viewerMembership = null

!{String base = "/app/groups/" + group.name();}

@template.layout.main(view=view, pageTitle=group.name(), activeNav="groups", activeGroupId=group.name(), content=@`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/">Groups</a>
    @template.components.icon(name = "chevron-right", size = 11)
    @template.components.italic-name(text = group.name(), size = "text-sm")
  </nav>

  @if("oauth_failed".equals(status))
    @template.components.admonition(content=@`GitHub didn't authorize the connection. Try "Connect GitHub" again.`, tone="danger", cssClass="mt-4")
  @endif

  @template.components.page-header(italicTitle = group.name(), subtitle = group.description(), actions = @`
    @if(group.state() == GroupState.PENDING)
      @template.components.button(label = "Continue verification", variant = "secondary", icon = "shield", href = base + "/verify")
    @elseif(group.state() == GroupState.FAILED)
      @template.components.button(label = "Verify domain", variant = "secondary", icon = "shield", href = base + "/verify")
    @endif
  `,
  statusRow = @`
    @template.components.verified-mark(status = group.state())
  `
  )

  <nav class="flex gap-1 border-b border-slate-200 dark:border-slate-700">
    <a class="inline-flex items-center gap-2 px-3.5 py-3 -mb-px border-b-2 text-sm no-underline transition-colors ${activeTab.equals("overview") ? "border-sky-500 text-slate-900 dark:text-slate-100 font-semibold" : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"}" href="${base}/">
      Overview
    </a>
<%--    <a class="inline-flex items-center gap-2 px-3.5 py-3 -mb-px border-b-2 text-sm no-underline transition-colors ${activeTab.equals("artifacts") ? "border-sky-500 text-slate-900 dark:text-slate-100 font-semibold" : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"}" href="${base}/artifacts">--%>
<%--      Artifacts--%>
<%--    </a>--%>
    <a class="inline-flex items-center gap-2 px-3.5 py-3 -mb-px border-b-2 text-sm no-underline transition-colors ${activeTab.equals("members") ? "border-sky-500 text-slate-900 dark:text-slate-100 font-semibold" : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"}" href="${base}/members/">
      Members
    </a>
    <a class="inline-flex items-center gap-2 px-3.5 py-3 -mb-px border-b-2 text-sm no-underline transition-colors ${activeTab.equals("settings") ? "border-sky-500 text-slate-900 dark:text-slate-100 font-semibold" : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"}" href="${base}/settings">
      Settings
    </a>
  </nav>

  <div class="h-6"></div>
  @if(activeTab.equals("members"))
    @template.pages.groups.members(view = view, group = group, activeTab = activeTab, inviteEmail = inviteEmail, inviteRole = inviteRole, errors = errors, members = members)
  @elseif(activeTab.equals("settings"))
    @template.pages.groups.settings(group = group, errors = errors)
  @else
    @template.pages.groups.overview(group = group, viewerMembership = viewerMembership)
  @endif
`)
