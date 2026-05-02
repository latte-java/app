<%--
    Group list row.
--%>
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.VerificationStatus
@param Group group
@param boolean compact = false

!{String pad = compact ? "px-4 py-3 gap-3" : "px-5 py-4 gap-3.5";}
!{int iconSize = compact ? 17 : 19;}

<a class="flex items-center border-t border-slate-200 dark:border-slate-700 first:border-t-0 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors no-underline ${pad}"
   href="/groups/${group.id()}">
  <div
      class="inline-flex items-center justify-center rounded-lg shrink-0 bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 ${compact ? "w-9 h-9" : "w-10 h-10"}">
    @template.components.icon(name = group.handleGroup() ? "user" : "globe", size = iconSize)
  </div>
  <div class="flex-1 min-w-0">
    <div class="flex flex-wrap items-center gap-2.5">
      @template.components.italic-name(text = group.name(), size = compact ? "text-lg" : "text-xl")
      @template.components.verified-mark(status = group.verification(), size = 14)
      @if(group.verification() == VerificationStatus.VERIFIED)
        @template.components.badge(label = "verified", tone = "cup")
      @elseif(group.verification() == VerificationStatus.PENDING)
        @template.components.badge(label = "verification pending", tone = "warn", dot = true)
      @elseif(!group.handleGroup())
        @template.components.badge(label = "unverified", tone = "neutral")
      @endif
      @if(group.handleGroup())
        @template.components.badge(label = "handle", tone = "neutral")
      @endif
    </div>
    @if(!compact && group.description() != null)
      <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">${group.description()}</p>
    @endif
    <p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">
      ${group.artifactCount()} ${group.artifactCount() == 1 ? "artifact" : "artifacts"}
      · ${group.memberCount()} ${group.memberCount() == 1 ? "member" : "members"}
    </p>
  </div>
  @template.components.badge(label = group.viewerRole().label().toLowerCase(), tone = group.viewerRole().name().equals("OWNER") ? "cup" : "neutral")
  <span class="text-slate-400 inline-flex">@template.components.icon(name = "chevron-right", size = 16)</span>
</a>
