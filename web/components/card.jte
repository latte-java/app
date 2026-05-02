<%--
    Card surface.
--%>
@param gg.jte.Content content
@param String tone = "default"
@param boolean padded = false

!{String border = tone.equals("danger") ? "border-red-300 dark:border-red-800"
               : tone.equals("warn")   ? "border-amber-300 dark:border-amber-800"
               :                         "border-slate-200 dark:border-slate-700";}
!{String pad = padded ? "p-6" : "";}

<div class="bg-white dark:bg-slate-900 border rounded-lg shadow-sm ${border} ${pad}">
  ${content}
</div>
