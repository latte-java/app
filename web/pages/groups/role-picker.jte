<%--
    Role select for member row.
--%>
@param String value
@param String name = "role"

<label class="relative inline-flex">
  <select name="${name}"
          class="appearance-none pl-3 pr-7 py-1.5 text-sm font-medium text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md cursor-pointer">
    <option value="OWNER" selected="${value.equals("OWNER")}">Owner</option>
    <option value="CONTRIBUTOR" selected="${value.equals("CONTRIBUTOR")}">Contributor</option>
  </select>
  <span class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none inline-flex">
    @template.components.icon(name = "chevron-down", size = 12)
  </span>
</label>
