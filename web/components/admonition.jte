<%--
    Admonition — a colored, rounded banner with a leading icon and a short
    message. Tone drives the color set and the default icon; pass `icon` to
    override, and `cssClass` for per-site spacing (e.g. "mt-4").
--%>
@param gg.jte.Content content
@param String tone = "neutral"
@param String icon = null
@param boolean white = false
@param String cssClass = ""
@param boolean shadow = false

!{String background;
  String fontColor;
  String resolvedIcon;
  String iconBackground;
  if (white) {
    background = "bg-white dark:bg-slate-900";
    fontColor = "";
    iconBackground = switch(tone) {
      case "attention" -> "bg-sky-50 dark:bg-sky-950/40";
      case "success" -> "bg-emerald-50 dark:bg-emerald-950/40";
      case "warn" -> "bg-amber-50 dark:bg-amber-950/40";
      case "danger" -> "bg-red-50 dark:bg-red-950/40";
      default -> "bg-slate-100 dark:bg-slate-800";
    };
  } else {
    background = switch(tone) {
      case "attention" -> "bg-sky-200 dark:bg-sky-950/40";
      case "success" -> "bg-emerald-200 dark:bg-emerald-950/40";
      case "warn" -> "bg-amber-200 dark:bg-amber-950/40";
      case "danger" -> "bg-red-200 dark:bg-red-950/40";
      default -> "bg-slate-200 dark:bg-slate-800";
    };
    iconBackground = switch(tone) {
      case "attention" -> "bg-sky-50 dark:bg-sky-950/40";
      case "success" -> "bg-emerald-50 dark:bg-emerald-950/40";
      case "warn" -> "bg-amber-50 dark:bg-amber-950/40";
      case "danger" -> "bg-red-50 dark:bg-red-950/40";
      default -> "bg-slate-100 dark:bg-slate-800";
    };
    fontColor = switch(tone) {
      case "attention" -> "text-sky-700 dark:text-sky-300";
      case "success" -> "text-emerald-700 dark:text-emerald-300";
      case "warn" -> "text-amber-700 dark:text-amber-300";
      case "danger" -> "text-red-700 dark:text-red-300";
      default -> "text-slate-600 dark:text-slate-300";
    };
  }
  if (icon == null) {
    resolvedIcon = switch(tone) {
      case "attention" -> "users";
      case "success" -> "check";
      case "warn" -> "alert";
      case "danger" -> "alert";
      default -> "check";
    };
  } else {
    resolvedIcon = icon;
  }
  String border = switch(tone) {
    case "attention" -> "border-sky-300 dark:border-sky-800";
    case "success" -> "border-emerald-300 dark:border-emerald-800";
    case "warn" -> "border-amber-300 dark:border-amber-800";
    case "danger" -> "border-red-300 dark:border-red-800";
    default -> "border-slate-300 dark:border-slate-800";
  };
  String iconFont = switch(tone) {
    case "attention" -> "text-sky-600";
    case "success" -> "text-emerald-600";
    case "warn" -> "text-amber-600";
    case "danger" -> "text-red-600";
    default -> "text-slate-600";
  };
  String shadowCss = shadow ? "shadow-sm" : "";
}

<div class="flex items-center gap-2.5 px-3.5 py-3 rounded-lg text-sm font-medium border mb-4 ${background} ${fontColor} ${border} ${cssClass} ${shadowCss}">
  <div class="inline-flex items-center justify-center w-9 h-9 rounded-lg shrink-0 mr-2 ${iconBackground} ${iconFont}">
    @template.components.icon(name=resolvedIcon, size=17)
  </div>
  ${content}
</div>
