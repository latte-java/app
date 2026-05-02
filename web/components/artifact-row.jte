<%--
    Artifact row inside the artifacts table.
--%>
@import org.lattejava.app.model.Artifact
@import org.lattejava.app.util.Format
@param Artifact artifact

<div>
  <code class="block font-mono text-sm font-semibold text-slate-900 dark:text-slate-100">${artifact.name()}</code>
  <div class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">${artifact.groupName()}:${artifact.name()}</div>
</div>
<code class="font-mono text-sm text-sky-600 dark:text-sky-400">${artifact.latest()}</code>
<span class="text-sm text-slate-700 dark:text-slate-200">${artifact.license()}</span>
<span class="text-sm font-medium text-slate-900 dark:text-slate-100">${Format.commas(artifact.downloads30d())}</span>
<span class="text-sm text-slate-500 dark:text-slate-400">${artifact.lastPublished()}</span>
@template.components.icon-button(icon = "more-horizontal", ariaLabel = "Artifact options")
