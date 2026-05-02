@param String error = null

@template.layout.auth(pageTitle = "Create account", content = @`
  <h1 class="m-0 mb-1 text-2xl font-bold text-slate-900 dark:text-slate-100 tracking-tight">Create your account</h1>
  <p class="m-0 mb-6 text-sm text-slate-600 dark:text-slate-300">Publish Java artifacts to the Latte repository.</p>
  <form method="post" action="/sign-up">
    @if(error != null)
      <div
          class="flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm font-medium mb-3.5">
        @template.components.icon(name = "alert", size = 14)
        ${error}
      </div>
    @endif
    <div
        class="mb-3.5">@template.components.input(name = "email", label = "Email", type = "email", placeholder = "you@company.com", required = true)</div>
    <div
        class="mb-3.5">@template.components.input(name = "name", label = "Display name", placeholder = "Jamie Doe", hint = "Shown next to your activity in groups.", required = true)</div>
    <div
        class="mb-3.5">@template.components.input(name = "password", label = "Password", type = "password", hint = "12+ characters.", required = true)</div>
    @template.components.button(label = "Create account", variant = "primary", size = "lg", type = "submit", cssClass = "w-full justify-center mt-2.5")
  </form>
  <p class="mt-5 text-center text-sm text-slate-500">Already have an account? <a
        class="text-sky-600 hover:underline no-underline" href="/sign-in">Sign in</a>.</p>
`)
