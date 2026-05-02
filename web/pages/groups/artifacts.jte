@import org.lattejava.app.model.Group
@import org.lattejava.app.model.Artifact
@import org.lattejava.app.model.VerificationStatus
@param Group group

@if(group.artifacts().isEmpty())
  <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
    @template.components.empty-state(
    icon = "package",
    title = "No artifacts yet",
    body = group.verification() == VerificationStatus.VERIFIED
                ? "Run latte publish in your Java project to publish here."
                : "Verify the domain to enable publishing.",
    action = @`
      <div class="max-w-md mx-auto text-left">
        @template.components.code-block(command = "latte publish --group " + group.name())
      </div>
    `
    )
  </div>
@else
  <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
    <div
        class="grid items-center gap-3 px-5 py-3 border-b border-slate-200 dark:border-slate-700 text-[11px] font-semibold uppercase tracking-widest text-slate-500"
        style="grid-template-columns: 2.4fr 1fr 1fr 1.2fr 1.2fr 40px;">
      <div>Artifact</div>
      <div>Version</div>
      <div>License</div>
      <div>Downloads (30d)</div>
      <div>Last published</div>
      <div></div>
    </div>
    @for(Artifact a : group.artifacts())
      <div
          class="grid items-center gap-3 px-5 py-3.5 border-t border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800/50"
          style="grid-template-columns: 2.4fr 1fr 1fr 1.2fr 1.2fr 40px;">
        @template.components.artifact-row(artifact = a)
      </div>
    @endfor
  </div>
@endif
