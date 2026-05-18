<%--
    Form input with optional label, prefix/suffix addons, hint, error.
    Pure Tailwind.
--%>
@param String name
@param String label = null
@param String value = ""
@param String placeholder = ""
@param String type = "text"
@param String hint = null
@param String error = null
@param boolean monospace = false
@param boolean autofocus = false
@param boolean required = false

!{String inputFont = monospace ? "font-mono text-[13.5px]" : "text-sm";}
!{String shellBorder = error != null
    ? "border-red-600 focus-within:border-red-600 focus-within:ring-2 focus-within:ring-red-100 dark:focus-within:ring-red-900/40"
    : "border-slate-200 focus-within:border-sky-500 focus-within:ring-2 focus-within:ring-sky-100 dark:border-slate-700 dark:focus-within:ring-sky-900/40";}

<div class="block">
  @if(label != null)
    <label class="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5" for="f-${name}">${label}</label>
  @endif
  <div class="flex items-stretch bg-white dark:bg-slate-900 border rounded-md overflow-hidden transition-colors ${shellBorder}">
    <textarea id="f-${name}" name="${name}" rows="4" placeholder="${placeholder}"
              autofocus="${autofocus}" required="${required}"
              class="flex-1 min-w-0 border-0 outline-none bg-transparent px-3.5 py-2.5 text-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 resize-y">${value}</textarea>
  </div>
  @if(error != null)
    <p class="mt-1.5 text-xs text-red-600">${error}</p>
  @elseif(hint != null)
    <p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">${hint}</p>
  @endif
</div>
