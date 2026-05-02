<%--
    Field row — label/value pair stacked in a rail.
--%>
@param String label
@param String value
@param boolean monospace = false

<div class="flex items-baseline justify-between gap-3 py-1.5">
  <span class="text-xs font-medium text-slate-500 dark:text-slate-400">${label}</span>
  <span class="text-sm text-slate-900 dark:text-slate-100 text-right ${monospace ? "font-mono" : ""}">${value}</span>
</div>
