<%--
    Card surface.
--%>
@param gg.jte.Content header = null
@param gg.jte.Content content
@param String tone = "default"
@param boolean padded = false
@param boolean paddedContent = true

!{
String border = switch(tone) {
    case "attention" -> "border-sky-300 dark:border-sky-800";
    case "danger" -> "border-red-300 dark:border-red-800";
    case "warn" -> "border-amber-300 dark:border-amber-800";
    default -> "border-slate-200 dark:border-slate-700";
  };
String pad = padded ? "p-6" : "";
String padContent = paddedContent ? "px-5 py-3" : "";
}

<div class="bg-white dark:bg-slate-900 border rounded-lg shadow-sm mb-4 ${border} ${pad}">
  @if(header != null)
    ${header}
  @endif
  <div class="${padContent}">
    ${content}
  </div>
</div>
