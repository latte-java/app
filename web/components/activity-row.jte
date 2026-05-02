<%--
    Activity row.
--%>
@import org.lattejava.app.model.ActivityEntry
@param ActivityEntry entry
@param boolean showGroup = false

!{String toneCls = entry.tone().equals("cup")     ? "bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400"
               : entry.tone().equals("success") ? "bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400"
               :                                  "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300";}

<div class="flex gap-3 px-4 py-2.5 border-t border-slate-200 dark:border-slate-700 first:border-t-0">
  <div class="inline-flex items-center justify-center w-7 h-7 rounded-full shrink-0 ${toneCls}">
    @template.components.icon(name = entry.icon(), size = 13)
  </div>
  <div class="flex-1 min-w-0">
    <div class="text-sm text-slate-700 dark:text-slate-200">
      <span class="font-medium text-slate-900 dark:text-slate-100">${entry.actor()}</span>
      $unsafe{entry.text()}
      @if(showGroup && entry.group() != null)
        in @template.components.italic-name(text = entry.group(), size = "text-xs")
      @endif
    </div>
    <div class="mt-0.5 text-[11px] text-slate-500 dark:text-slate-400">${entry.when()}</div>
  </div>
</div>
