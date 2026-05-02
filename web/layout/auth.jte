<%--
    Auth layout — centered card on a plain page.
--%>
@param String pageTitle
@param gg.jte.Content content

<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>${pageTitle} · Latte repository</title>
  <link rel="icon" href="/static/logo-cup.svg" type="image/svg+xml"/>
  <link rel="preconnect" href="https://fonts.googleapis.com"/>
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
  <link rel="stylesheet"
        href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=Source+Serif+4:ital,wght@1,500;1,600&display=swap"/>
  <link rel="stylesheet" href="/static/css/app.css"/>
</head>
<body
    class="font-sans bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 antialiased min-h-screen flex items-center justify-center px-5 py-10">
<div class="w-full max-w-md">
  <a class="flex items-center gap-2.5 justify-center mb-7 no-underline" href="/">
    <img src="/static/logo-cup.svg" alt="" class="h-8"/>
    <span class="text-2xl font-extrabold tracking-tight text-slate-900 dark:text-slate-100">Latte</span>
  </a>
  <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl p-8 shadow-md">
    ${content}
  </div>
</div>
</body>
</html>
