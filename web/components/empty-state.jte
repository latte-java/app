<%--
    Empty state.
--%>
@param String title
@param String body
@param String icon = "package"
@param gg.jte.Content action = null

<div class="px-6 py-12 text-center">
  <div class="inline-flex text-slate-400 dark:text-slate-500">
    @template.components.icon(name = icon, size = 32)
  </div>
  <h3 class="mt-3 mb-1.5 text-base font-bold text-slate-900 dark:text-slate-100">${title}</h3>
  <p class="mx-auto mb-4 max-w-md text-sm text-slate-600 dark:text-slate-300">${body}</p>
  @if(action != null)${action}@endif
</div>
