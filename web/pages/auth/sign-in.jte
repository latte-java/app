@param String error = null

@template.layout.auth(pageTitle = "Sign in", content = @`
  <h1 class="m-0 mb-1 text-2xl font-bold text-slate-900 dark:text-slate-100 tracking-tight">Sign in</h1>
  <p class="m-0 mb-6 text-sm text-slate-600 dark:text-slate-300">Welcome back to the Latte repository.</p>
  <form method="post" action="/sign-in">
    @if(error != null)
      <div
          class="flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm font-medium mb-3.5">
        @template.components.icon(name = "alert", size = 14)
        ${error}
      </div>
    @endif
    <div class="mb-3.5">
      @template.components.input(name = "email", label = "Email", type = "email", placeholder = "you@company.com", autofocus = true, required = true)
    </div>
    <div class="mb-3.5">
      @template.components.input(name = "password", label = "Password", type = "password", required = true)
    </div>
    @template.components.button(label = "Sign in", variant = "primary", size = "lg", type = "submit", cssClass = "w-full justify-center mt-2.5")
  </form>
  <div
      class="flex items-center gap-3 my-5 text-[11px] font-medium uppercase tracking-widest text-slate-500 before:flex-1 before:h-px before:bg-slate-200 after:flex-1 after:h-px after:bg-slate-200 dark:before:bg-slate-700 dark:after:bg-slate-700">
    or
  </div>
  @template.components.button(label = "Continue with GitHub", variant = "secondary", size = "lg", icon = "github", cssClass = "w-full justify-center", href = "/sign-in/github")
  <p class="mt-4.5 text-center text-sm text-slate-500">No account? <a class="text-sky-600 hover:underline no-underline"
                                                                      href="/sign-up">Create one</a>.</p>
`)
