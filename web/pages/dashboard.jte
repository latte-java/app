@import org.lattejava.app.model.View
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.ActivityEntry
@import org.lattejava.app.model.VerificationStatus
@import org.lattejava.app.util.Format
@import java.util.*

@param View view
@param List<Group> groups
@param List<ActivityEntry> recentActivity

!{int totalArtifacts = groups.stream().mapToInt(Group::artifactCount).sum();}
!{long totalDownloads = groups.stream().mapToLong(Group::monthlyDownloads).sum();}
!{long verified = groups.stream().filter(g -> g.verification() == VerificationStatus.VERIFIED).count();}
!{long pending  = groups.stream().filter(g -> g.verification() == VerificationStatus.PENDING).count();}

@template.layout.main(view = view, pageTitle = "Dashboard", activeNav = "dashboard", sidebarGroups = groups, content = @`
  @template.components.page-header(
  eyebrow = "Welcome back",
  title = view.viewer().name().split(" ")[0],
  subtitle = "Publish and manage Java artifacts on the Latte repository.",
  actions = @`
    @template.components.button(label = "New group",  variant = "primary",   icon = "plus", href = "/groups/new")
  `
  )

  <div class="grid grid-cols-4 gap-3 mb-7">
    @template.components.stat(label = "Groups", value = String.valueOf(groups.size()), sub = verified + " verified" + (pending > 0 ? ", " + pending + " pending" : ""))
    @template.components.stat(label = "Artifacts", value = String.valueOf(totalArtifacts), sub = "across all groups")
    @template.components.stat(label = "Downloads (30d)", value = Format.shortNumber(totalDownloads), sub = "aggregated", accent = true)
  </div>

  <div class="grid grid-cols-1 lg:grid-cols-5 gap-4 items-start">
    <div
        class="lg:col-span-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
      <header class="flex items-center gap-2.5 px-5 py-3.5 border-b border-slate-200 dark:border-slate-700">
        <h3 class="m-0 text-sm font-bold text-slate-900 dark:text-slate-100">Your groups</h3>
        <a class="ml-auto inline-flex items-center gap-1 text-sm font-medium text-slate-500 hover:text-sky-600 no-underline"
           href="/groups">See all @template.components.icon(name = "arrow-right", size = 12)</a>
      </header>
      @for(Group g : groups)
        @template.components.group-list-row(group = g, compact = true)
      @endfor
    </div>
    <div
        class="lg:col-span-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
      <header class="px-5 py-3.5 border-b border-slate-200 dark:border-slate-700">
        <h3 class="m-0 text-sm font-bold text-slate-900 dark:text-slate-100">Recent activity</h3>
      </header>
      @for(ActivityEntry a : recentActivity)
        @template.components.activity-row(entry = a, showGroup = true)
      @endfor
    </div>
  </div>
`)
