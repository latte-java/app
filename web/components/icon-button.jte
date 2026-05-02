<%--
    Icon-only square button.
--%>
@param String icon
@param String ariaLabel
@param String variant = "default"
@param String type = "button"

!{String variantCls = variant.equals("danger")
    ? "text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40"
    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-slate-100";}

<button
    class="inline-flex items-center justify-center w-8 h-8 rounded-md border border-transparent transition-colors ${variantCls}"
    type="${type}" aria-label="${ariaLabel}">
  @template.components.icon(name = icon, size = 15)
</button>
