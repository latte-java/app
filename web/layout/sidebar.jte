<%--
    Sidebar.
--%>
@import org.lattejava.app.model.View
@import org.lattejava.app.model.Group
@import java.util.List
@param View view
@param List<Group> groups
@param String activeNav = "dashboard"
@param String activeGroupId = null

<aside
    class="w-60 shrink-0 bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-700 flex flex-col sticky top-0 h-screen">
  <div class="flex items-center gap-2.5 px-4 pt-4 pb-3">
    <img src="/static/logo-cup.svg" alt="" class="h-7 block"/>
    <div class="flex flex-col leading-none">
      <span class="text-base font-extrabold text-slate-900 dark:text-slate-100 tracking-tight">Latte</span>
      <span class="mt-1 text-[11px] font-medium text-slate-500 dark:text-slate-400">repository</span>
    </div>
  </div>
  <nav class="flex-1 px-2.5 pb-3 overflow-y-auto flex flex-col">
    @template.layout.sidebar-link(href = "/",         icon = "home",     label = "Dashboard",   active = activeNav.equals("dashboard"))
    @template.layout.sidebar-link(href = "/groups",   icon = "users",    label = "Groups",      active = activeNav.equals("groups"), badge = groups.size())
    @template.layout.sidebar-link(href = "/activity", icon = "activity", label = "Activity",    active = activeNav.equals("activity"))
    <div
        class="px-2.5 pt-4 pb-1.5 text-[10.5px] font-semibold uppercase tracking-widest text-slate-500 dark:text-slate-400">
      Account
    </div>
    @template.layout.sidebar-link(href = "/account",        icon = "settings", label = "Settings",   active = activeNav.equals("settings"))
    <div
        class="px-2.5 pt-4 pb-1.5 text-[10.5px] font-semibold uppercase tracking-widest text-slate-500 dark:text-slate-400">
      Your groups
    </div>
    @for(Group g : groups)
      <a class="flex items-center gap-2 px-2.5 py-1.5 rounded-md no-underline transition-colors mt-px ${g.id().equals(activeGroupId) ? "bg-sky-50 text-slate-900 dark:bg-sky-950/40 dark:text-slate-100" : "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"}"
         href="/groups/${g.id()}">
        @template.components.italic-name(text = g.name(), size = "text-sm")
        @template.components.verified-mark(status = g.verification(), size = 12)
      </a>
    @endfor
    <a class="flex items-center gap-2 px-2.5 py-1.5 mt-1.5 rounded-md border border-dashed border-slate-300 dark:border-slate-600 text-sm font-medium text-slate-500 dark:text-slate-400 no-underline hover:text-slate-900 hover:border-sky-500 dark:hover:text-slate-100"
       href="/groups/new">
      @template.components.icon(name = "plus", size = 13)
      <span>New group</span>
    </a>
  </nav>
  <div class="border-t border-slate-200 dark:border-slate-700 p-3 flex items-center gap-2.5">
    @template.components.avatar(initials = view.viewer().avatar(), size = 30)
    <div class="flex-1 min-w-0">
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">${view.viewer().name()}</div>
      <div class="text-[11px] text-slate-500 dark:text-slate-400 truncate">${view.viewer().email()}</div>
    </div>
    <form method="post" action="/auth/sign-out" class="shrink-0">
      <button type="submit" aria-label="Sign out" title="Sign out"
              class="inline-flex items-center justify-center w-8 h-8 rounded-md text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100">
        @template.components.icon(name = "logout", size = 15)
      </button>
    </form>
  </div>
</aside>
