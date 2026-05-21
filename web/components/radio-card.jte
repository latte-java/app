<%--
    Generic single-choice card backed by a radio input. Knows nothing about any
    specific domain — the consumer supplies the value, text, icon, and checked state.
    The whole card is clickable because the visible content is inside the <label>.
--%>
@param String name
@param String value
@param String label
@param String description = ""
@param String icon = ""
@param boolean checked = false

<label class="relative block cursor-pointer">
  <input type="radio" name="${name}" value="${value}" checked="${checked}" class="peer sr-only">
  <span aria-hidden="true"
        class="absolute top-4 right-4 w-4 h-4 rounded-full border-2 border-slate-300 dark:border-slate-600 transition-colors peer-checked:border-sky-500 peer-checked:bg-sky-500"></span>
  <div class="flex flex-col gap-2 h-full p-4 pr-10 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg transition-colors peer-checked:border-sky-500 peer-checked:ring-2 peer-checked:ring-sky-500/30 peer-focus-visible:ring-2 peer-focus-visible:ring-sky-500/40">
    <div class="flex items-center gap-2.5">
      @if(!icon.isEmpty())
        <div class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 shrink-0">
          @template.components.icon(name=icon, size=18)
        </div>
      @endif
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">${label}</div>
    </div>
    @if(!description.isEmpty())
      <p class="m-0 text-sm text-slate-600 dark:text-slate-300">${description}</p>
    @endif
  </div>
</label>
