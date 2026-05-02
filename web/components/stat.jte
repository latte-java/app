<%--
    Stat card.
--%>
@param String label
@param String value
@param String sub = null
@param boolean accent = false

<div class="p-5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg">
  <div class="text-[11px] font-semibold uppercase tracking-widest text-slate-500 dark:text-slate-400">${label}</div>
  <div
      class="mt-2.5 text-3xl font-bold tracking-tight ${accent ? "text-sky-500" : "text-slate-900 dark:text-slate-100"}">${value}</div>
  @if(sub != null)
    <div class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">${sub}</div>
  @endif
</div>
