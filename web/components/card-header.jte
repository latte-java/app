<%--
    Card header — title row that sits at the top of a card surface. Renders an h3 with the
    given name plus any additional content (typically a "See all" link or actions).
--%>
@param String name = null
@param gg.jte.Content nameContent = null
@param gg.jte.Content content = null
@param boolean separator = true
@param String tone = "default"
@param boolean small = false

!{var border = separator ? "border-b border-slate-200 dark:border-slate-700" : "";}
!{var fontColor = tone.equals("danger") ? "text-red-600"
             : tone.equals("warn") ? "text-amber-600"
             :                       "text-slate-900 dark:text-slate-100";}
!{var font = small ? "text-xs font-bold" : "text-sm font-bold";}

<header class="flex items-center gap-2.5 px-5 py-3.5 ${border}">
  <h3 class="m-0 ${font} ${fontColor}">
    @if(nameContent != null)
      ${nameContent}
    @else
      ${name}
    @endif
  </h3>
  @if(content != null)
    ${content}
  @endif
</header>
