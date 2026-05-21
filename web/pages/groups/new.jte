@import org.lattejava.app.model.view.MainView
@import org.lattejava.app.model.Group
@import org.lattejava.app.error.Error
@import org.lattejava.app.error.Errors
@import java.util.List
@param MainView view
@param String name = ""
@param String description = ""
@param Errors errors = new Errors()

@template.layout.main(view=view, pageTitle="New group", activeNav="groups",
content=@`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/">Groups</a>
    @template.components.icon(name="chevron-right", size=11)
    <span>New group</span>
  </nav>
  @template.components.page-header(title="Create a group", subtitle="Groups namespace your artifacts. Pick a group ID that matches how you'll prove ownership.")
  @if(!errors.generalErrors.isEmpty())
    <div class="mb-4 p-4 border border-red-300 bg-red-50 dark:bg-red-950/40 rounded-lg text-sm text-red-700 dark:text-red-300">
      @for(Error err : errors.generalErrors)
        <p class="m-0">${err.message}</p>
      @endfor
    </div>
  @endif

  <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Group kinds</h3>
  <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">Latte uses reverse-DNS group names or simple names. The type is inferred from the group name itself. Below are some examples.</p>
  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 mb-10">
    @template.components.card(padded=true,
    content=@`
      <div class="flex flex-col gap-2 h-full">
        <div class="flex items-center gap-2.5">
          <div class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 shrink-0">
            @template.components.icon(name = "globe", size = 18)
          </div>
          <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">Reverse-DNS</div>
        </div>
        <p class="m-0 text-sm text-slate-600 dark:text-slate-300">For organizations or projects that own a domain. Verified by adding a TXT record to your DNS.</p>
        <div class="flex flex-col gap-1 mt-auto">
          <code class="font-mono text-xs text-slate-700 dark:text-slate-300">io.nimbusworks</code>
          <code class="font-mono text-xs text-slate-700 dark:text-slate-300">com.example.tools</code>
        </div>
      </div>
    `)

    @template.components.card(padded=true,
    content=@`
      <div class="flex flex-col gap-2 h-full">
        <div class="flex items-center gap-2.5">
          <div class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 shrink-0">
            @template.components.icon(name = "github", size = 18)
          </div>
          <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">GitHub</div>
        </div>
        <p class="m-0 text-sm text-slate-600 dark:text-slate-300">For GitHub users and organizations. Verified by linking your GitHub account.</p>
        <div class="flex flex-col gap-1 mt-auto">
          <code class="font-mono text-xs text-slate-700 dark:text-slate-300">io.github.voidmain</code>
          <code class="font-mono text-xs text-slate-700 dark:text-slate-300">io.github.latte-java</code>
        </div>
      </div>
    `)

    @template.components.card(padded=true,
    content=@`
      <div class="flex flex-col gap-2 h-full">
        <div class="flex items-center gap-2.5">
          <div class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 shrink-0">
            @template.components.icon(name = "package", size = 18)
          </div>
          <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">Short name</div>
        </div>
        <p class="m-0 text-sm text-slate-600 dark:text-slate-300">A single segment with no dots. No domain or GitHub account required — verified on creation.</p>
        <div class="flex flex-col gap-1 mt-auto">
          <code class="font-mono text-xs text-slate-700 dark:text-slate-300">my-project</code>
          <code class="font-mono text-xs text-slate-700 dark:text-slate-300">acme-tools</code>
        </div>
      </div>
    `)
  </div>

  <form class="max-w-2xl" method="post" action="/app/groups/new">
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Group ID</h3>
    !{List<Error> nameErrors = errors.fieldErrors.getOrDefault("name", java.util.List.of());}
    !{String nameError = nameErrors.isEmpty() ? null : nameErrors.get(0).message;}
    @template.components.input(name="name", value=name, placeholder="io.nimbusworks", monospace=true, hint="Reverse-DNS like com.acme or a short name like acme-tools. Lowercase letters, digits, and hyphens.", error=nameError)
    <div class="h-6"></div>
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Description</h3>
    @template.components.textarea(name="description", error=nameError, placeholder="What does this group publish?", hint="Optional. Shown on the group's page.")
    <div class="h-6"></div>
    <div class="flex gap-2 justify-end">
      @template.components.button(label = "Cancel", variant = "ghost", href = "/app/groups/")
      @template.components.button(label = "Create group", variant = "primary", type = "submit")
    </div>
  </form>
`)
