@import org.lattejava.app.model.Group
@import org.lattejava.app.model.GroupState
@param Group group

<div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
  @template.components.empty-state(
  icon = "package",
  title = "No artifacts yet",
  body = group.state() == GroupState.VERIFIED
              ? "Run latte publish in your Java project to publish here."
              : "Verify the domain to enable publishing.",
  action = @`
    <div class="max-w-md mx-auto text-left">
      @template.components.code-block(command = "latte publish --group " + group.name())
    </div>
  `
  )
</div>
