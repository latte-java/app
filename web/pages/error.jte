@param int status

<%--
    Standalone error page. No layout, no components. Render via AppExceptionHandler.writeBody.
--%>
<!doctype html>
<html lang="en" class="">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>${status} · Latte repository</title>
  <link rel="icon" href="/static/images/logo-cup.svg" type="image/svg+xml"/>
  <link rel="preconnect" href="https://fonts.googleapis.com"/>
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
  <link rel="stylesheet"
        href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=Source+Serif+4:ital,wght@1,500;1,600&family=JetBrains+Mono:wght@400;500;600&display=swap"/>
  <link rel="stylesheet" href="/static/css/app.css"/>
</head>
<body class="font-sans bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 antialiased">
<div class="min-h-screen flex flex-col">

  <header class="px-6 sm:px-10 pt-6">
    <a href="/" class="inline-flex items-center gap-2.5 no-underline">
      <img src="/static/images/logo.svg" alt="" class="h-7 block"/>
    </a>
  </header>

  <main class="flex-1 flex items-center justify-center px-6 sm:px-10 py-12">
    <div class="w-full max-w-2xl text-center">

      <div class="inline-block origin-[50%_70%] animate-wiggle-404 motion-reduce:animate-none will-change-transform">
        <div class="text-[9rem] sm:text-[11rem] leading-none font-extrabold tracking-tight text-slate-900 dark:text-slate-100 select-none">
          ${status / 100}<span class="text-sky-500">${(status / 10) % 10}</span>${status % 10}
        </div>
      </div>

      <p class="mt-2 font-serif italic text-2xl sm:text-3xl font-semibold text-sky-600 dark:text-sky-400">
        EspressoMachineUnpluggedException
      </p>

      <div class="mt-8 mx-auto max-w-xl text-left rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 shadow-sm overflow-hidden">
        <div class="flex items-center gap-1.5 px-4 py-2 border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/60">
          <span class="block w-2.5 h-2.5 rounded-full bg-red-400"></span>
          <span class="block w-2.5 h-2.5 rounded-full bg-amber-400"></span>
          <span class="block w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
          <span class="ml-2 text-[11px] font-medium text-slate-500 dark:text-slate-400 tracking-wide">stacktrace.log</span>
        </div>
        <pre class="font-mono text-[12.5px] leading-relaxed px-4 py-3 text-slate-700 dark:text-slate-300 overflow-x-auto"><span class="text-red-600 dark:text-red-400">org.lattejava.app.EspressoMachineUnpluggedException</span>: the beans never made it to the grinder
    at org.lattejava.app.Barista.pour(Barista.java:${status})
    at org.lattejava.app.Main.serve(Main.java:&hairsp;?&hairsp;)
    at java.lang.Thread.run(Thread.java:1583)
    ... 42 more &nbsp;☕</pre>
      </div>

      <p class="mt-6 text-sm text-slate-500 dark:text-slate-400">
        <span class="font-medium text-slate-600 dark:text-slate-300">Suggested fix:</span>
        refresh the page. If the espresso machine is still cold, file a bug.
      </p>

      <div class="mt-8 flex items-center justify-center gap-3 flex-wrap">
        <a href="/app/" class="inline-flex items-center gap-2 rounded-md font-medium whitespace-nowrap transition-colors px-4 py-2 text-sm bg-sky-500 text-white border border-transparent hover:bg-sky-600 shadow-sm no-underline">
          <span>Back to dashboard</span>
        </a>
        <a href="/app/groups/" class="inline-flex items-center gap-2 rounded-md font-medium whitespace-nowrap transition-colors px-4 py-2 text-sm bg-transparent text-slate-900 border border-slate-300 hover:bg-slate-100 dark:text-slate-100 dark:border-slate-600 dark:hover:bg-slate-800 no-underline">
          <span>Browse groups</span>
        </a>
      </div>

    </div>
  </main>

  <footer class="px-6 sm:px-10 pb-6 text-center text-[11px] text-slate-400 dark:text-slate-500">
    Server-side hiccup. The logs know more than this page does.
  </footer>

</div>
</body>
</html>
