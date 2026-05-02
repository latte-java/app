<%--
    Button — primary, secondary, ghost, danger.
    Tailwind-only utility classes; no custom CSS.
--%>
@param String label
@param String variant = "primary"
@param String size = "md"
@param String icon = null
@param String href = null
@param String type = "button"
@param String cssClass = ""

!{String sizeCls = size.equals("sm") ? "px-3 py-1.5 text-xs"
                : size.equals("lg") ? "px-5 py-3 text-base"
                : "px-4 py-2 text-sm";}
!{String variantCls = variant.equals("primary")   ? "bg-sky-500 text-white border border-transparent hover:bg-sky-600 shadow-sm"
                   : variant.equals("secondary") ? "bg-transparent text-slate-900 border border-slate-300 hover:bg-slate-100 dark:text-slate-100 dark:border-slate-600 dark:hover:bg-slate-800"
                   : variant.equals("danger")    ? "bg-transparent text-red-600 border border-red-600 hover:bg-red-600 hover:text-white"
                   :                               "bg-transparent text-slate-600 border border-transparent hover:bg-sky-50 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-slate-100";}
!{int iconSize = size.equals("sm") ? 13 : size.equals("lg") ? 16 : 14;}
!{String classes = "inline-flex items-center gap-2 rounded-md font-medium whitespace-nowrap transition-colors disabled:opacity-50 disabled:cursor-not-allowed " + sizeCls + " " + variantCls + " " + cssClass;}

@if(href != null)
  <a class="${classes} no-underline" href="${href}">
    @if(icon != null)@template.components.icon(name = icon, size = iconSize)@endif
    <span>${label}</span>
  </a>
@else
  <button class="${classes}" type="${type}">
    @if(icon != null)@template.components.icon(name = icon, size = iconSize)@endif
    <span>${label}</span>
  </button>
@endif
