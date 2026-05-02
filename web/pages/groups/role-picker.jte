<%--
    Role select for member row.
--%>
@param String value

<label class="relative inline-flex">
  <select
      class="appearance-none pl-3 pr-7 py-1.5 text-sm font-medium text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md cursor-pointer">
    <option value="MAINTAINER" @if(value.equals("MAINTAINER"))selected @endif>Maintainer</option>
    <option value="PUBLISHER" @if(value.equals("PUBLISHER")) selected @endif>Publisher</option>
    <option value="VIEWER" @if(value.equals("VIEWER"))    selected @endif>Viewer</option>
  </select>
  <span class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none inline-flex">
        @template.components.icon(name = "chevron-down", size = 12)
    </span>
</label>
