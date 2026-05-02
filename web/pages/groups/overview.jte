@import org.lattejava.app.model.Group
@import org.lattejava.app.model.Artifact
@import org.lattejava.app.model.ActivityEntry
@import org.lattejava.app.model.VerificationStatus
@import org.lattejava.app.util.Format
@param Group group

<div class="grid grid-cols-1 lg:grid-cols-5 gap-4 items-start">
  <div class="lg:col-span-3">
    @if(group.verification() == VerificationStatus.PENDING)
      <div
          class="flex items-start gap-3.5 p-4 mb-4 bg-white dark:bg-slate-900 border border-amber-300 dark:border-amber-800 rounded-lg">
        <div
            class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 shrink-0">
          @template.components.icon(name = "clock", size = 17)
        </div>
        <div class="flex-1">
          <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">DNS check is waiting on your record
          </div>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">Add the TXT record below
            to @template.components.italic-name(text = group.domain(), size = "text-sm"). Latte rechecks every 10
            minutes.</p>
        </div>
        @template.components.button(label = "View record", variant = "primary", size = "sm", href = "/groups/" + group.id() + "/verify")
      </div>
    @elseif(group.verification() == VerificationStatus.UNVERIFIED && !group.handleGroup())
      <div
          class="flex items-start gap-3.5 p-4 mb-4 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-600 rounded-lg">
        <div
            class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 shrink-0">
          @template.components.icon(name = "shield", size = 17)
        </div>
        <div class="flex-1">
          <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">Verify ${group.domain()} to publish
          </div>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">Reverse-domain groups must prove ownership of the
            matching domain before publishing.</p>
        </div>
        @template.components.button(label = "Verify", variant = "primary", size = "sm", href = "/groups/" + group.id() + "/verify")
      </div>
    @endif

    <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm mb-4">
      <header class="px-5 py-3.5 border-b border-slate-200 dark:border-slate-700">
        <h3 class="m-0 text-sm font-bold text-slate-900 dark:text-slate-100">Publish
          to @template.components.italic-name(text = group.name(), size = "text-sm")</h3>
      </header>
      <div class="p-5">
        @template.components.code-block(command = "latte publish --group " + group.name())
        <p class="mt-3 text-xs text-slate-500 dark:text-slate-400">Or declare the group in <code>latte.toml</code> and
          run <code>latte publish</code>.</p>
      </div>
    </div>

    <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
      <header class="flex items-center px-5 py-3.5 border-b border-slate-200 dark:border-slate-700">
        <h3 class="m-0 text-sm font-bold text-slate-900 dark:text-slate-100">Top artifacts</h3>
        <span class="ml-auto text-xs font-medium text-slate-500">last 30 days</span>
      </header>
      @if(group.artifacts().isEmpty())
        @template.components.empty-state(title = "No artifacts published yet", body = "Once verified, run latte publish in your project.")
      @else
        @for(int i = 0; i < Math.min(5, group.artifacts().size()); i++)
          !{Artifact a = group.artifacts().get(i);}
          <div
              class="flex items-center gap-3 px-4 py-3 border-t border-slate-200 dark:border-slate-700 first:border-t-0">
            <div
                class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400 shrink-0">
              @template.components.icon(name = "package", size = 16)
            </div>
            <div class="flex-1 min-w-0">
              <div class="font-mono text-sm font-semibold text-slate-900 dark:text-slate-100">${a.name()}</div>
              <p class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">v${a.latest()} · ${a.lastPublished()}
                · ${a.license()}</p>
            </div>
            <div class="text-right">
              <div
                  class="text-sm font-medium text-slate-700 dark:text-slate-200">${Format.shortNumber(a.downloads30d())}</div>
              <div class="mt-0.5 text-[11px] text-slate-500 dark:text-slate-400">downloads</div>
            </div>
          </div>
        @endfor
      @endif
    </div>
  </div>

  <div class="lg:col-span-2 flex flex-col gap-4">
    <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm p-5">
      <h3 class="m-0 mb-3 text-xs font-bold uppercase tracking-widest text-slate-900 dark:text-slate-100">About</h3>
      @template.components.field-row(label = "Group ID", value = group.name(), monospace = true)
      @template.components.field-row(label = "Domain", value = group.domain() != null ? group.domain() : "—")
      @template.components.field-row(label = "Created", value = group.createdOn() != null ? group.createdOn().toString() : "—")
      @template.components.field-row(label = "Visibility", value = "Public")
    </div>
    <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
      <header class="px-5 py-3.5 border-b border-slate-200 dark:border-slate-700">
        <h3 class="m-0 text-xs font-bold uppercase tracking-widest text-slate-900 dark:text-slate-100">Activity</h3>
      </header>
      @for(int i = 0; i < Math.min(5, group.activity().size()); i++)
        !{ActivityEntry a = group.activity().get(i);}
        @template.components.activity-row(entry = a)
      @endfor
    </div>
  </div>
</div>
