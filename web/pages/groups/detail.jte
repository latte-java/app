@import org.lattejava.app.model.View
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.VerificationStatus
@import java.util.List
@param View view
@param Group group
@param List<Group> sidebarGroups
@param String activeTab
@param gg.jte.Content tabContent

!{String base = "/groups/" + group.id();}

@template.layout.main(view = view, pageTitle = group.name(), activeNav = "groups", activeGroupId = group.id(), sidebarGroups = sidebarGroups, content = @`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/groups">Groups</a>
    @template.components.icon(name = "chevron-right", size = 11)
    @template.components.italic-name(text = group.name(), size = "text-sm")
  </nav>

  @template.components.page-header(
  italicTitle = group.name(),
  subtitle = group.description(),
  actions = @`
    @if(group.verification() == VerificationStatus.PENDING)
      @template.components.button(label = "Continue verification", variant = "secondary", icon = "shield", href = base + "/verify")
    @elseif(group.verification() == VerificationStatus.UNVERIFIED && !group.handleGroup())
      @template.components.button(label = "Verify domain", variant = "secondary", icon = "shield", href = base + "/verify")
    @endif
  `,
  statusRow = @`
    @if(group.verification() == VerificationStatus.VERIFIED)
      @template.components.badge(label = "verified · " + group.domain(), tone = "cup", dot = true)
    @elseif(group.verification() == VerificationStatus.PENDING)
      @template.components.badge(label = "verification pending · " + group.domain(), tone = "warn", dot = true)
    @elseif(group.handleGroup())
      @template.components.badge(label = "personal handle", tone = "neutral", dot = true)
    @else
      @template.components.badge(label = "unverified", tone = "neutral", dot = true)
    @endif
  `
  )

  <nav class="flex gap-1 border-b border-slate-200 dark:border-slate-700">
    <a class="inline-flex items-center gap-2 px-3.5 py-3 -mb-px border-b-2 text-sm no-underline transition-colors ${activeTab.equals("overview") ? "border-sky-500 text-slate-900 dark:text-slate-100 font-semibold" : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"}"
       href="${base}">Overview</a>
    <a class="inline-flex items-center gap-2 px-3.5 py-3 -mb-px border-b-2 text-sm no-underline transition-colors ${activeTab.equals("artifacts") ? "border-sky-500 text-slate-900 dark:text-slate-100 font-semibold" : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"}"
       href="${base}/artifacts">
      <span>Artifacts</span><span
          class="text-[11px] font-medium text-slate-500 bg-slate-100 dark:bg-slate-800 px-1.5 py-0.5 rounded">${group.artifactCount()}</span>
    </a>
    <a class="inline-flex items-center gap-2 px-3.5 py-3 -mb-px border-b-2 text-sm no-underline transition-colors ${activeTab.equals("members") ? "border-sky-500 text-slate-900 dark:text-slate-100 font-semibold" : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"}"
       href="${base}/members">
      <span>Members</span><span
          class="text-[11px] font-medium text-slate-500 bg-slate-100 dark:bg-slate-800 px-1.5 py-0.5 rounded">${group.memberCount()}</span>
    </a>
    <a class="inline-flex items-center gap-2 px-3.5 py-3 -mb-px border-b-2 text-sm no-underline transition-colors ${activeTab.equals("settings") ? "border-sky-500 text-slate-900 dark:text-slate-100 font-semibold" : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-100"}"
       href="${base}/settings">Settings</a>
  </nav>

  <div class="h-6"></div>
  ${tabContent}
`)
