<%--
    Code block. Always dark surface so it reads as code regardless of page theme.
--%>
@param String command = null
@param String filename = null
@param java.util.List<String> lines = null

!{String copyText = command != null ? command : (lines != null ? String.join("\n", lines) : "");}

<div class="relative">
  @if(filename != null)
    <div
        class="font-mono text-xs text-slate-400 bg-slate-950 px-4 py-2.5 rounded-t-lg border-b border-slate-800">${filename}</div>
  @endif
  <pre
      class="m-0 bg-slate-950 border border-slate-800 ${filename != null ? "rounded-b-lg border-t-0" : "rounded-lg"} px-4 py-3 text-slate-100 overflow-x-auto"><code
        class="font-mono text-[13.5px] leading-relaxed">@if(command != null)@for(String line : command.split("\n"))
      <div class="whitespace-pre"><span
            class="text-sky-400 select-none">$ </span>${line}</div>@endfor@elseif(lines != null)@for(String line : lines)
      <div class="whitespace-pre">${line}</div>@endfor@endif</code></pre>
  <button
      class="absolute ${filename != null ? "top-10" : "top-2"} right-2 inline-flex items-center gap-1 text-[11px] font-medium text-slate-400 bg-slate-800 border border-slate-700 rounded px-2 py-1 hover:text-sky-400"
      data-copy="${copyText}">
    @template.components.icon(name = "copy", size = 11)
    <span>Copy</span>
  </button>
</div>
