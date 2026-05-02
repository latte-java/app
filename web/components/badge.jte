<%--
    Pill badge with optional dot.
--%>
@param String label
@param String tone = "neutral"
@param boolean dot = false

!{String toneCls = tone.equals("cup")     ? "bg-sky-50 text-sky-700 dark:bg-sky-950/40 dark:text-sky-300"
               : tone.equals("success") ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300"
               : tone.equals("warn")    ? "bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300"
               : tone.equals("danger")  ? "bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300"
               :                          "bg-slate-100 text-slate-700 border border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700";}

<span class="inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-medium tracking-wide rounded-full ${toneCls}">
    @if(dot)<span class="w-1.5 h-1.5 rounded-full bg-current"></span>@endif
  ${label}
</span>
