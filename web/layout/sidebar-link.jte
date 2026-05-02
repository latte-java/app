<%--
    Sidebar nav link.
--%>
@param String href
@param String icon
@param String label
@param boolean active = false
@param Integer badge = null

!{String activeCls = active
    ? "bg-sky-50 text-slate-900 font-semibold dark:bg-sky-950/40 dark:text-slate-100"
    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-slate-100";}

<a class="flex items-center gap-2.5 px-2.5 py-2 rounded-md text-sm font-medium no-underline transition-colors ${activeCls}"
   href="${href}">
    <span class="inline-flex ${active ? "text-sky-500" : ""}">
        @template.components.icon(name = icon, size = 16)
    </span>
  <span class="flex-1">${label}</span>
  @if(badge != null)
    <span
        class="text-[11px] font-medium text-slate-500 dark:text-slate-400 bg-slate-100 dark:bg-slate-800 px-1.5 py-0.5 rounded-full">${badge}</span>
  @endif
</a>
