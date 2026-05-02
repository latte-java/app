@import org.lattejava.app.model.View
@import org.lattejava.app.model.Group
@import java.util.List
@param View view
@param List<Group> sidebarGroups
@param String kind = "domain"
@param String name = ""
@param String error = null

@template.layout.main(view = view, pageTitle = "New group", activeNav = "groups", sidebarGroups = sidebarGroups, content = @`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/groups">Groups</a>
    @template.components.icon(name = "chevron-right", size = 11)
    <span>New group</span>
  </nav>
  @template.components.page-header(
  title = "Create a group",
  subtitle = "Groups namespace your artifacts. Reverse-domain groups must prove ownership of the matching domain."
  )
  <form class="max-w-2xl" method="post" action="/groups/new">
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Kind</h3>
    <label
        class="flex gap-3.5 p-4 border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-900 cursor-pointer mb-3 hover:border-sky-500 transition-colors">
      <input type="radio" name="kind" value="domain" class="mt-1 accent-sky-500"
             @if(kind.equals("domain"))checked @endif/>
      <div
          class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 shrink-0">
        @template.components.icon(name = "globe", size = 18)
      </div>
      <div>
        <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">Reverse-domain group</div>
        <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">For organizations or projects that own a domain.
          Requires DNS verification.</p>
      </div>
    </label>
    <label
        class="flex gap-3.5 p-4 border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-900 cursor-pointer mb-3 hover:border-sky-500 transition-colors">
      <input type="radio" name="kind" value="handle" class="mt-1 accent-sky-500"
             @if(kind.equals("handle"))checked @endif/>
      <div
          class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 shrink-0">
        @template.components.icon(name = "user", size = 18)
      </div>
      <div>
        <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">Personal handle group</div>
        <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">Tied to your account. No verification needed.</p>
      </div>
    </label>
    <div class="h-6"></div>
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Group ID</h3>
    @template.components.input(name = "name", value = name, placeholder = "io.nimbusworks", prefix = "latte://", monospace = true, hint = "Reverse-DNS format. Lowercase. Up to 4 labels.", error = error)
    <div class="h-6"></div>
    <div class="flex gap-2 justify-end">
      @template.components.button(label = "Cancel", variant = "ghost", href = "/groups")
      @template.components.button(label = "Create group", variant = "primary", type = "submit")
    </div>
  </form>
`)
