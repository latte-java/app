<%--
    Page header with optional eyebrow, italic title, subtitle, status row, actions.
--%>
@param String eyebrow = null
@param String title = null
@param String italicTitle = null
@param String subtitle = null
@param gg.jte.Content actions = null
@param gg.jte.Content statusRow = null

<header class="mb-8">
  @if(eyebrow != null)
    <div
        class="text-[11px] font-semibold uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-2.5">${eyebrow}</div>
  @endif
  <div class="flex flex-wrap items-start gap-6">
    <div class="flex-1 min-w-0">
      @if(italicTitle != null)
        <h1 class="m-0 text-4xl font-serif italic tracking-tight text-slate-900 dark:text-slate-100 leading-tight">${italicTitle}</h1>
      @else
        <h1 class="m-0 text-4xl font-extrabold tracking-tight text-slate-900 dark:text-slate-100 leading-tight">${title}</h1>
      @endif
      @if(subtitle != null)
        <p class="mt-2 text-base text-slate-600 dark:text-slate-300 max-w-2xl">${subtitle}</p>
      @endif
      @if(statusRow != null)
        <div class="mt-3.5 flex flex-wrap items-center gap-2.5">${statusRow}</div>
      @endif
    </div>
    @if(actions != null)
      <div class="flex gap-2 shrink-0">${actions}</div>
    @endif
  </div>
</header>
