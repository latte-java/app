@import org.lattejava.app.model.View
@import org.lattejava.app.model.Group
@import java.util.List
@param View view
@param String pageTitle
@param String activeNav = "dashboard"
@param String activeGroupId = null
@param List<Group> sidebarGroups
@param gg.jte.Content content

<!doctype html>
<html lang="en" class="">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>${pageTitle} · Latte repository</title>
  <link rel="icon" href="/static/images/logo-cup.svg" type="image/svg+xml"/>
  <link rel="preconnect" href="https://fonts.googleapis.com"/>
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
  <link rel="stylesheet"
        href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=Source+Serif+4:ital,wght@1,500;1,600&family=JetBrains+Mono:wght@400;500;600&display=swap"/>
  <link rel="stylesheet" href="/static/css/app.css"/>
</head>
<body class="font-sans bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 antialiased">
<div class="flex items-stretch min-h-screen">
  @template.layout.sidebar(view = view, groups = sidebarGroups, activeNav = activeNav, activeGroupId = activeGroupId)
  <main class="flex-1 min-w-0">
    <div class="max-w-6xl px-10 pt-9 pb-20 mx-auto">
      ${content}
    </div>
  </main>
</div>
<script src="/static/app.js"></script>
</body>
</html>
